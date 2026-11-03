package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.Insurance;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.Merchant;
import com.example.insurancesystem.domain.merchant.importbatch.BatchImportResult;
import com.example.insurancesystem.domain.workorder.WorkorderDTO;
import com.example.insurancesystem.mapper.InsuranceMapper;
import com.example.insurancesystem.mapper.MerchantMapper;
import com.example.insurancesystem.mapper.UserMapper;
import com.example.insurancesystem.security.EnterpriseContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证历史工单导入的真实模板契约、身份匹配、投保推导和失败隔离，所有写入均为模拟，不连接数据库。
 */
class WorkorderBatchImportServiceTest {
    /** 为导入建立真实线程级认证上下文，不依赖额外静态Mock依赖。 */
    @org.junit.jupiter.api.BeforeEach
    void authenticate() {
        var login = new com.example.insurancesystem.domain.authenticate.LoginUser();
        login.setEnterpriseId(9L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(login, null, List.of()));
    }

    /** 清除线程认证，防止影响同一测试进程中的其他企业测试。 */
    @org.junit.jupiter.api.AfterEach
    void clearAuthentication() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    /** 商业保费仅启用企业默认项，默认不投保的附加险不启用，交强与车船税独立判断。 */
    @Test
    void importsWithAndMatchedHandlerAndIndependentInsuranceChoices() throws Exception {
        ObjectMapper json = new ObjectMapper();
        WorkorderImportSchema schema = new WorkorderImportSchema(json);
        MerchantMapper merchants = mock(MerchantMapper.class);
        InsuranceMapper insurances = mock(InsuranceMapper.class);
        UserMapper users = mock(UserMapper.class);
        WorkorderImportRowWriter writer = mock(WorkorderImportRowWriter.class);
        Merchant merchant = new Merchant(); merchant.setId(17L);
        when(merchants.selectList(any())).thenReturn(List.of(merchant));
        when(users.selectImportHandler(9L, "赵六", "handler@example.com", "ABC123")).thenReturn(8L);
        Insurance commercial = product(1L, "座位险", 1, "不投保");
        commercial.setOptionsJson("[{\"label\":\"不投保\",\"value\":\"不投保\"},{\"label\":\"1万\",\"value\":\"1万\"}]");
        when(insurances.selectList(any())).thenReturn(List.of(product(4L, "车辆损失险", 1, "投保"),
                product(5L, "第三者责任险", 1, "100万"), commercial,
                product(2L, "交强险", 3, "需要"), product(3L, "车船税", 3, "需要")));
        WorkorderBatchImportService service = new WorkorderBatchImportService(schema, json, merchants, users, insurances, writer);
        Map<String, String> values = Map.ofEntries(
                Map.entry("createMerchantName", "甲店"), Map.entry("insuranceMerchantName", "保险公司"),
                Map.entry("handlerName", "赵六"), Map.entry("handlerEmail", "handler@example.com"), Map.entry("handlerIdNum", "ABC123"),
                Map.entry("type", "旧车"), Map.entry("ownerType", "个人"), Map.entry("ownerName", "张三"),
                Map.entry("commercialInsuranceStartTime", "2026-10-29"), Map.entry("compulsoryInsuranceStartTime", "2026-11-29"),
                Map.entry("commercialAmount", "1000.00"), Map.entry("compulsoryAmount", "0"), Map.entry("vehicleAndTaxAmount", "100.00"));
        {
            ResponseResult response = service.importFile(workbook(schema, values, false));
            assertEquals(200, response.getCode());
            assertEquals(1, ((BatchImportResult) response.getData()).getSuccessRows());
            ArgumentCaptor<WorkorderDTO> saved = ArgumentCaptor.forClass(WorkorderDTO.class);
            verify(writer).write(saved.capture());
            verify(users).selectImportHandler(9L, "赵六", "handler@example.com", "ABC123");
            assertEquals(8L, saved.getValue().getHandleBy());
            assertEquals(List.of("投保", "100万", "不投保", "不需要", "需要"), saved.getValue().getWorkorderInsuranceList().stream().map(item -> item.getOptionJson()).toList());
            assertTrue(saved.getValue().getWorkorderFileList().isEmpty());
        }
    }

    /** 修改固定表头应整体拒绝，不调用任何工单保存。 */
    @Test
    void rejectsAlteredTemplateBeforeWritingAnyRows() throws Exception {
        ObjectMapper json = new ObjectMapper();
        WorkorderImportSchema schema = new WorkorderImportSchema(json);
        WorkorderImportRowWriter writer = mock(WorkorderImportRowWriter.class);
        InsuranceMapper insurance = mock(InsuranceMapper.class);
        WorkorderBatchImportService service = new WorkorderBatchImportService(schema, json, mock(MerchantMapper.class), mock(UserMapper.class), insurance, writer);
        {
            assertEquals(400, service.importFile(workbook(schema, Map.of(), true)).getCode());
            verifyNoInteractions(writer);
        }
    }

    /** 处理人匹配成功后，单行事务保存失败仍继续后续行。 */
    @Test
    void continuesAfterFailedRowWithMatchedHandler() throws Exception {
        ObjectMapper json = new ObjectMapper();
        WorkorderImportSchema schema = new WorkorderImportSchema(json);
        MerchantMapper merchants = mock(MerchantMapper.class);
        Merchant merchant = new Merchant(); merchant.setId(17L);
        when(merchants.selectList(any())).thenReturn(List.of(merchant));
        UserMapper users = mock(UserMapper.class);
        when(users.selectImportHandler(9L, "赵六", null, null)).thenReturn(8L);
        InsuranceMapper insurance = mock(InsuranceMapper.class);
        when(insurance.selectList(any())).thenReturn(List.of(product(2L, "交强险", 3, "需要")));
        WorkorderImportRowWriter writer = mock(WorkorderImportRowWriter.class);
        doThrow(new IllegalArgumentException("本行校验失败")).doNothing().when(writer).write(any());
        WorkorderBatchImportService service = new WorkorderBatchImportService(schema, json, merchants, users, insurance, writer);
        Map<String, String> values = Map.of("createMerchantName", "甲店", "insuranceMerchantName", "保险公司", "handlerName", "赵六",
                "type", "旧车", "ownerType", "个人", "ownerName", "张三", "compulsoryInsuranceStartTime", "2026-10-29");
        var original = workbook(schema, values, false);
        try (XSSFWorkbook file = new XSSFWorkbook(original.getInputStream()); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = file.getSheetAt(0);
            Row copy = sheet.createRow(5);
            for (var cell : sheet.getRow(4)) copy.createCell(cell.getColumnIndex()).setCellValue(cell.getStringCellValue());
            file.write(output);
            var result = (BatchImportResult) service.importFile(new MockMultipartFile("file", "workorder.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray())).getData();
            assertEquals(2, result.getTotalRows());
            assertEquals(1, result.getSuccessRows());
            assertEquals(1, result.getFailures().size());
            verify(users, times(2)).selectImportHandler(9L, "赵六", null, null);
            ArgumentCaptor<WorkorderDTO> saved = ArgumentCaptor.forClass(WorkorderDTO.class);
            verify(writer, times(2)).write(saved.capture());
            assertEquals(8L, saved.getAllValues().get(1).getHandleBy());
        }
    }

    /** 超过1万条时在解析、匹配和保存任何工单之前整文件拒绝，避免出现只保存前半部分。 */
    @Test
    void rejects10001RowsBeforeMatchingOrWriting() throws Exception {
        ObjectMapper json = new ObjectMapper();
        WorkorderImportSchema schema = new WorkorderImportSchema(json);
        WorkorderImportRowWriter writer = mock(WorkorderImportRowWriter.class);
        MerchantMapper merchants = mock(MerchantMapper.class);
        WorkorderBatchImportService service = new WorkorderBatchImportService(schema, json, merchants,
                mock(UserMapper.class), mock(InsuranceMapper.class), writer);
        var source = workbook(schema, Map.of("createMerchantName", "超限测试"), false);
        try (XSSFWorkbook file = new XSSFWorkbook(source.getInputStream()); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 5; index < 10005; index++) file.getSheetAt(0).createRow(index).createCell(0).setCellValue("超限测试");
            file.write(output);
            var response = service.importFile(new MockMultipartFile("file", "over-limit.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()));
            assertEquals(400, response.getCode());
            assertTrue(response.getMsg().contains("10000"));
            verifyNoInteractions(writer, merchants);
        }
    }

    /** 根据正式契约生成最小上传工作簿，避免测试维护另一套列顺序。 */
    private MockMultipartFile workbook(WorkorderImportSchema schema, Map<String, String> values, boolean invalid) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("工单");
            Row header = sheet.createRow(3); Row row = sheet.createRow(4);
            for (int i = 0; i < schema.getColumns().size(); i++) {
                var column = schema.getColumns().get(i);
                header.createCell(i).setCellValue(invalid && i == 0 ? "错误表头" : column.label());
                if (values.containsKey(column.path())) row.createCell(i).setCellValue(values.get(column.path()));
            }
            workbook.write(output);
            return new MockMultipartFile("file", "workorder.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    /** 构造企业已有险种配置，用于检查保额值和默认投保选择。 */
    private Insurance product(Long id, String name, int type, String defaultValue) {
        Insurance product = new Insurance(); product.setId(id); product.setName(name); product.setType(type); product.setDefaultOptionJson(defaultValue);
        product.setOptionsJson("[{\"value\":\"不需要\"},{\"value\":\"需要\"}]");
        return product;
    }

    /**
     * 合并的交强险车船税产品在任一保费为正时采用原默认“投保”，全为0时关闭。
     * 同时检查无商业保费时车损与三者不被启用，空值和0均不推断为有保费。
     */
    @Test
    void combinedCompulsoryProductUsesEitherPremiumAndOwnOptions() throws Exception {
        var json = new ObjectMapper();
        var schema = new WorkorderImportSchema(json);
        var merchants = mock(MerchantMapper.class);
        var merchant = new Merchant(); merchant.setId(17L);
        when(merchants.selectList(any())).thenReturn(List.of(merchant));
        var users = mock(UserMapper.class);
        when(users.selectImportHandler(9L, null, "handler@example.com", null)).thenReturn(8L);
        var insurance = mock(InsuranceMapper.class);
        var combined = product(3L, "交强险、车船税", 3, "投保");
        combined.setOptionsJson("[{\"value\":\"不投保\"},{\"value\":\"投保\"}]");
        when(insurance.selectList(any())).thenReturn(List.of(product(1L, "车辆损失险", 1, "投保"),
                product(2L, "第三者责任险", 1, "100万"), combined));
        var writer = mock(WorkorderImportRowWriter.class);
        var service = new WorkorderBatchImportService(schema, json, merchants, users, insurance, writer);
        /* 仅税费、仅交强、均为0、全部未填，四种组合均需独立推导。 */
        for (var amounts : List.of(List.of("0", "360"), List.of("950", "0"), List.of("0", "0"), List.of("", ""))) {
            var values = new java.util.HashMap<>(Map.of("createMerchantName", "甲店", "insuranceMerchantName", "保险公司",
                    "handlerEmail", "handler@example.com", "type", "旧车", "ownerType", "个人", "ownerName", "张三",
                    "compulsoryInsuranceStartTime", "2026-10-29"));
            values.put("compulsoryAmount", amounts.get(0));
            values.put("vehicleAndTaxAmount", amounts.get(1));
            values.put("commercialAmount", "0");
            var response = service.importFile(workbook(schema, values, false));
            assertEquals(1, ((BatchImportResult) response.getData()).getSuccessRows());
        }
        var saved = ArgumentCaptor.forClass(WorkorderDTO.class);
        verify(writer, times(4)).write(saved.capture());
        assertEquals(List.of("投保", "投保", "不投保", "不投保"), saved.getAllValues().stream()
                .map(dto -> dto.getWorkorderInsuranceList().get(2).getOptionJson()).toList());
        assertTrue(saved.getAllValues().stream().allMatch(dto -> dto.getWorkorderInsuranceList().subList(0, 2)
                .stream().allMatch(item -> "不投保".equals(item.getOptionJson()))));
    }

    /** 姓名、邮箱、证件号全部未填时拒绝该行，不查询用户或写入工单。 */
    @Test
    void rejectsMissingHandlerIdentityBeforeWriting() throws Exception {
        var json = new ObjectMapper();
        var schema = new WorkorderImportSchema(json);
        var merchants = mock(MerchantMapper.class);
        var merchant = new Merchant(); merchant.setId(17L);
        when(merchants.selectList(any())).thenReturn(List.of(merchant));
        var users = mock(UserMapper.class);
        var writer = mock(WorkorderImportRowWriter.class);
        var service = new WorkorderBatchImportService(schema, json, merchants, users, mock(InsuranceMapper.class), writer);
        var response = service.importFile(workbook(schema, Map.of("createMerchantName", "甲店",
                "insuranceMerchantName", "保险公司", "type", "旧车", "ownerType", "个人"), false));
        var result = (BatchImportResult) response.getData();
        assertEquals(1, result.getFailureRows());
        assertTrue(result.getFailures().get(0).getReason().contains("至少填写一项"));
        verifyNoInteractions(users, writer);
    }
}
