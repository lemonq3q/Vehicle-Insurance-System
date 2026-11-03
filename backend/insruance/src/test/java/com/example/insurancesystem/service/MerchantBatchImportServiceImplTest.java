package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.importbatch.BatchTemplateValidationResult;
import com.example.insurancesystem.mapper.MerchantAreaMapper;
import com.example.insurancesystem.mapper.MerchantMapper;
import com.example.insurancesystem.service.impl.MerchantBatchImportServiceImpl;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 验证批量导入的模板预检边界：正确结构应通过，表头被用户修改时应拒绝，数据内容不参与此阶段判断。
 */
class MerchantBatchImportServiceImplTest {

    private final MerchantBatchImportService service = new MerchantBatchImportServiceImpl(
            mock(MerchantMapper.class), mock(MerchantAreaMapper.class));

    /** 官方上游 Sheet 与表头保持完整时通过结构预检，即使没有任何数据行。 */
    @Test
    void shouldAcceptOfficialUpstreamStructureWithoutDataValidation() throws Exception {
        MockMultipartFile file = workbook("上游机构",
                List.of("* 机构名称", "* 所在地区", "机构地址", "* 联系人", "* 联系电话", "* 业务区域", "备注"), false);
        ResponseResult response = service.validateUpstreamTemplate(file);
        assertTrue(((BatchTemplateValidationResult) response.getData()).isValid());
    }

    /** 数据 Sheet 表头被重命名时拒绝继续，防止列错位后把值写入错误数据库字段。 */
    @Test
    void shouldRejectChangedHeader() throws Exception {
        MockMultipartFile file = workbook("上游机构",
                List.of("机构", "* 所在地区", "机构地址", "* 联系人", "* 联系电话", "* 业务区域", "备注"), false);
        ResponseResult response = service.validateUpstreamTemplate(file);
        assertFalse(((BatchTemplateValidationResult) response.getData()).isValid());
    }

    /** 直接校验随服务发布的模板文件，确保新下游同一行包含联系人、手机号而无需人员表。 */
    @Test
    void shouldAcceptPublishedDownstreamTemplate() throws Exception {
        try (var input = getClass().getResourceAsStream("/import/downstream.xlsx")) {
            org.junit.jupiter.api.Assertions.assertNotNull(input);
            var file = new MockMultipartFile("file", "downstream.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", input.readAllBytes());
            assertTrue(((BatchTemplateValidationResult) service.validateDownstreamTemplate(file).getData()).isValid());
        }
    }

    /** 1万条空必填资料应进入逐行校验而非超限；第10001条则整体拒绝且不调用写入。 */
    @Test
    void enforces10000NonemptyRowsBeforeAnyDatabaseWrite() throws Exception {
        var user = new com.example.insurancesystem.domain.user.User(); user.setId(7L); user.setEnterpriseId(9L);
        var login = new com.example.insurancesystem.domain.authenticate.LoginUser(user, List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(login, null, List.of()));
        MerchantMapper merchants = mock(MerchantMapper.class);
        var importer = new MerchantBatchImportServiceImpl(merchants, mock(MerchantAreaMapper.class));
        var source = workbook("下游机构", List.of("* 下游名称", "* 下游类型", "* 所在地区", "详细地址", "* 开户银行", "* 银行卡号", "* 联系人", "* 联系电话", "备注"), false);
        try (XSSFWorkbook file = new XSSFWorkbook(source.getInputStream())) {
            for (int index = 4; index < 10004; index++) file.getSheetAt(1).createRow(index).createCell(0).setCellValue("容量测试");
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                file.write(bytes);
                var accepted = importer.importDownstream(new MockMultipartFile("file", "capacity.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray()));
                org.junit.jupiter.api.Assertions.assertEquals(200, accepted.getCode());
                org.junit.jupiter.api.Assertions.assertEquals(10000,
                        ((com.example.insurancesystem.domain.merchant.importbatch.BatchImportResult) accepted.getData()).getTotalRows());
            }
            file.getSheetAt(1).createRow(10004).createCell(0).setCellValue("第10001条");
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                file.write(bytes);
                var rejected = importer.importDownstream(new MockMultipartFile("file", "over-limit.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray()));
                org.junit.jupiter.api.Assertions.assertEquals(400, rejected.getCode());
                assertTrue(rejected.getMsg().contains("10000"));
            }
            org.mockito.Mockito.verify(merchants, org.mockito.Mockito.never()).insertImportBatch(org.mockito.ArgumentMatchers.anyList());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    /** 创建只包含说明、数据和字段说明 Sheet 的最小上游工作簿。 */
    private MockMultipartFile workbook(String dataSheet, List<String> headers, boolean addData) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("使用说明");
            Sheet sheet = workbook.createSheet(dataSheet);
            Row header = sheet.createRow(3);
            for (int index = 0; index < headers.size(); index++) header.createCell(index).setCellValue(headers.get(index));
            if (addData) sheet.createRow(4).createCell(0).setCellValue("任意数据");
            workbook.createSheet("字段说明");
            workbook.write(output);
            return new MockMultipartFile("file", "template.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
