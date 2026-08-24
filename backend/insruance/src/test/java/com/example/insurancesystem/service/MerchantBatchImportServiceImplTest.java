package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.importbatch.BatchTemplateValidationResult;
import com.example.insurancesystem.mapper.MerchantAreaMapper;
import com.example.insurancesystem.mapper.MerchantMapper;
import com.example.insurancesystem.mapper.MerchantStaffMapper;
import com.example.insurancesystem.mapper.MerchantStaffRoleMapper;
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
            mock(MerchantMapper.class), mock(MerchantAreaMapper.class),
            mock(MerchantStaffMapper.class), mock(MerchantStaffRoleMapper.class));

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
