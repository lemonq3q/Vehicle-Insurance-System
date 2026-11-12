package com.example.insurancesystem.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 从原官方模板延展当前导入契约，按前端业务流程重排101列并保留原视觉样式。
 * 模板、列说明和解析共用同一 JSON 契约，避免合表后仍下载不能填写新资料的旧模板。
 * 每次下载建立独立工作簿并关闭资源，不共享可变 Excel 对象，不写数据库或服务器文件。
 */
@Service
public class WorkorderImportTemplateService {
    private final WorkorderImportSchema schema;
    /**
     * 为源模板仍留空的字段提供可读示例；键与导入列契约的字段路径一致。
     * 新增字段必须在此明确给出示例，避免按类型生成无业务含义的占位值。
     */
    private static final Map<String, String> MISSING_EXAMPLES = Map.ofEntries(
            Map.entry("organizationName", "示例科技有限公司"),
            Map.entry("socialCreditCode", "91310110MA1G123456"),
            Map.entry("vehicleCertificate.brandModel", "示例牌ABC123"),
            Map.entry("vehicleCertificate.vehicleType", "小型轿车"),
            Map.entry("vehicleCertificate.vehicleCode", "LDEMO123456789012"),
            Map.entry("vehicleCertificate.engineCode", "DEMO123456"),
            Map.entry("vehicleCertificate.seats", "5"),
            Map.entry("vehicleCertificate.curbWeight", "1500"),
            Map.entry("vehicleCertificate.displacement", "1598"),
            Map.entry("vehicleCertificate.approvedLoadCapacity", "0"),
            Map.entry("vehicleInvoice.invoiceAmount", "120000"),
            Map.entry("vehicleInvoice.buyerName", "张三"),
            Map.entry("vehicleInvoice.buyerIdNum", "110101199002020000"),
            Map.entry("vehicleInvoice.vehicleType", "小型轿车"),
            Map.entry("vehicleInvoice.brandModel", "示例牌ABC123"),
            Map.entry("vehicleInvoice.vehicleCode", "LDEMO123456789012"),
            Map.entry("vehicleInvoice.engineCode", "DEMO123456"),
            Map.entry("vehicleInvoice.seats", "5"),
            Map.entry("vehicleInvoice.approvedLoadCapacity", "0"),
            Map.entry("vehicleLicense.transferDate", "2025-10-29"),
            Map.entry("quotationFailedRemark", "示例：报价失败时填写原因"),
            Map.entry("payFailedRemark", "示例：支付失败时填写原因"),
            Map.entry("underwritingFailedRemark", "示例：核保失败时填写原因"),
            Map.entry("acceptInsuranceFailedRemark", "示例：承保失败时填写原因"),
            Map.entry("commercialInsurerName", "示例保险公司"),
            Map.entry("commercialInsuranceEndTime", "2027-10-29"),
            Map.entry("compulsoryInsurerName", "示例保险公司"),
            Map.entry("compulsoryInsuranceEndTime", "2027-10-29"),
            Map.entry("nonMotorInsurerName", "示例保险公司"),
            Map.entry("nonMotorInsuranceEndTime", "2027-10-29"),
            Map.entry("nonMotorPolicyNo", "DEMO-NONMOTOR-001"),
            Map.entry("nonMotorInsuranceStartTime", "2026-10-29"));

    /**

     * 注入当前列契约，模板下载与导入校验使用同一字段顺序和长度定义。

     */
    public WorkorderImportTemplateService(WorkorderImportSchema schema) { this.schema = schema; }

    /**
     * 将按前端流程排列的101列模板写入响应流；旧模板只由导入器兼容读取。
     * @param output 调用方持有的 HTTP 输出流，本方法不关闭该流。
     * @throws IOException 原模板损坏或响应写入失败，不返回半截伪造文件。
     */
    public void write(OutputStream output) throws IOException {
        try (InputStream input = new ClassPathResource("import/workorder.xlsx").getInputStream();
             Workbook workbook = new XSSFWorkbook(input)) {
            List<WorkorderImportSchema.Column> columns = schema.getColumns();
            Sheet sheet = workbook.getSheet("工单");
            Row header = sheet.getRow(3);
            int oldSize = header.getLastCellNum();
            CellStyle headerStyle = header.getCell(oldSize - 1).getCellStyle();
            CellStyle inputStyle = workbook.createCellStyle();
            inputStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            /* 源工作簿仍是旧列序，先按旧契约保存示例，再按新列序重写，避免值跟着列号错位。 */
            DataFormatter formatter = new DataFormatter();
            Map<String, String> sourceExamples = new HashMap<>();
            Row sample = sheet.getRow(4) == null ? sheet.createRow(4) : sheet.getRow(4);
            for (int index = 0; index < oldSize; index++) {
                String value = formatter.formatCellValue(sample.getCell(index));
                if (!value.isBlank()) sourceExamples.put(schema.getLegacyColumns().get(index).path(), value);
            }

            /*
             * 原模板第二行重复写了“基础信息”，第三行又由字段契约生成同名分组。
             * 第二行改为填写提示，保留第四行表头和第五行数据的固定位置，
             * 使新版下载文件与现有批量导入解析及旧模板继续兼容。
             */
            Row guidance = sheet.getRow(1) == null ? sheet.createRow(1) : sheet.getRow(1);
            for (Cell cell : guidance) cell.setBlank();

            /*
             * 新列使用原表头风格，号码统一文本格式，避免 Excel 把长保单号转换成科学计数。
             * 原说明横向合并范围扩展到新列；数据区不填虚构 OCR 或保险公司资料。
             */
            for (int index = 0; index < columns.size(); index++) {
                Cell cell = header.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                cell.setCellValue(columns.get(index).label());
                if (index >= oldSize) {
                    cell.setCellStyle(headerStyle);
                    sheet.setColumnWidth(index, 24 * 256);
                    sheet.setDefaultColumnStyle(index, inputStyle);
                }
            }
            /*
             * 第五行是可删除的示例数据；源模板已有的示例按字段路径迁移，新增及历史空列补齐。
             * 路径缺少示例时立即失败，防止后续加列却再次交付带空白字段的模板。
             */
            for (int index = 0; index < columns.size(); index++) {
                Cell cell = sample.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String example = sourceExamples.getOrDefault(columns.get(index).path(), MISSING_EXAMPLES.get(columns.get(index).path()));
                if (example == null) throw new IllegalStateException("导入模板缺少示例：" + columns.get(index).path());
                cell.setCellValue(example);
                cell.setCellStyle(inputStyle);
            }
            for (int index = sheet.getNumMergedRegions() - 1; index >= 0; index--) {
                CellRangeAddress range = sheet.getMergedRegion(index);
                if (range.getLastColumn() == oldSize - 1 && range.getLastRow() < 2) {
                    sheet.removeMergedRegion(index);
                    range.setLastColumn(columns.size() - 1);
                    sheet.addMergedRegion(range);
                }
            }
            for (int index = sheet.getNumMergedRegions() - 1; index >= 0; index--) {
                int firstRow = sheet.getMergedRegion(index).getFirstRow();
                if (firstRow == 1 || firstRow == 2) sheet.removeMergedRegion(index);
            }
            guidance.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    .setCellValue("填写说明：第4行为固定表头，第5行为完整示例；正式导入前请删除或替换示例行");
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, columns.size() - 1));
            Row groups = sheet.getRow(2) == null ? sheet.createRow(2) : sheet.getRow(2);
            for (Cell cell : groups) cell.setBlank();
            int first = 0;
            for (int index = 0; index <= columns.size(); index++) {
                if (index == columns.size() || !columns.get(index).group().equals(columns.get(first).group())) {
                    Cell cell = groups.getCell(first, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    cell.setCellValue(columns.get(first).group()); cell.setCellStyle(headerStyle);
                    if (index - first > 1) sheet.addMergedRegion(new CellRangeAddress(2, 2, first, index - 1));
                    first = index;
                }
            }
            Sheet description = workbook.getSheet("字段说明");
            if (description == null) description = workbook.createSheet("字段说明");
            /* 字段说明全部按契约重建，保持旧列含义并准确列出新公司、非车险起期与保单终期。 */
            for (int index = description.getLastRowNum(); index >= 0; index--) {
                Row row = description.getRow(index);
                if (row != null) description.removeRow(row);
            }
            while (description.getNumMergedRegions() > 0) description.removeMergedRegion(0);
            String[] headings = {"列序号", "分组", "列名", "字段路径", "类型", "必填", "约束"};
            Row descriptionHeader = description.createRow(0);
            for (int index = 0; index < headings.length; index++) {
                Cell cell = descriptionHeader.createCell(index); cell.setCellValue(headings[index]); cell.setCellStyle(headerStyle);
            }
            for (int index = 0; index < columns.size(); index++) {
                var column = columns.get(index);
                Row row = description.createRow(index + 1);
                String constraint = column.maxLength() != null ? "最多" + column.maxLength() + "字"
                    : column.values() != null ? String.join(" / ", column.values().keySet())
                    : "date".equals(column.type()) ? "yyyy-MM-dd；终期晚于对应起期" : "非负数值";
                String[] values = {String.valueOf(index + 1), column.group(), column.label(), column.path(),
                        column.type(), column.required() ? "是" : "否", constraint};
                for (int position = 0; position < values.length; position++) row.createCell(position).setCellValue(values[position]);
            }
            for (int index = 0; index < headings.length; index++) description.setColumnWidth(index, (index == 3 ? 42 : 24) * 256);
            Sheet instructions = workbook.getSheet("使用说明");
            if (instructions != null) {
                Row note = instructions.createRow(instructions.getLastRowNum() + 2);
                note.createCell(0).setCellValue("本版101列按业务页面顺序排列；导入仍兼容上一版101列及原93列。非车险不参与自动续保提醒。");
            }
            workbook.write(output);
        }
    }
}

