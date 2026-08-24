package com.example.insurancesystem.domain.merchant.importbatch;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 汇总一次正式导入的处理结果。
 * 成功数代表已经提交到数据库的业务行，失败明细只包含经逐行校验后被筛除的数据。
 */
@Data
public class BatchImportResult {
    private int totalRows;
    private int successRows;
    private int failureRows;
    private List<BatchImportFailure> failures = new ArrayList<>();

    /**
     * 添加失败行并同步维护总数，避免调用方分别修改多个统计字段造成前后端展示不一致。
     */
    public void addFailure(BatchImportFailure failure) {
        failures.add(failure);
        failureRows = failures.size();
    }
}
