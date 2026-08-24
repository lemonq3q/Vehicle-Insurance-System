package com.example.insurancesystem.domain.merchant.importbatch;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 返回 Excel 模板结构校验结果。
 * 此阶段只确认用户确实沿用了官方模板，不评价任何业务数据是否可入库。
 */
@Data
@AllArgsConstructor
public class BatchTemplateValidationResult {
    private boolean valid;
    private String fileName;
    private String message;
}
