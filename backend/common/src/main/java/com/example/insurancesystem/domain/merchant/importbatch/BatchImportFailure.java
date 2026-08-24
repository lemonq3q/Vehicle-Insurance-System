package com.example.insurancesystem.domain.merchant.importbatch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 描述批量导入中未进入数据库的一行数据。
 * 前端依靠 Sheet、Excel 行号、原始字段和值及原因帮助用户在原文件中准确定位并修正问题。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportFailure {
    private String sheetName;
    private Integer rowNumber;
    private Map<String, String> rowData;
    private String reason;
}
