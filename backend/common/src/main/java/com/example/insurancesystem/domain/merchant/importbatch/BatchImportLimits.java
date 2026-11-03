package com.example.insurancesystem.domain.merchant.importbatch;

/**
 * 上下游机构与历史工单导入共用的业务容量约束，供上传校验、行数检查和批量写入使用。
 * 行数只统计非空业务行，含未删除的模板示例；单次请求与整文件事务边界不由这些常量改变。
 * 文件限制与前端保持20MiB，批次大小用于控制数据库参数和单条SQL体积，不代表分批提交。
 */
public final class BatchImportLimits {
    public static final int MAX_DATA_ROWS = 10000;
    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    public static final int WRITE_BATCH_SIZE = 500;

    private BatchImportLimits() { }
}
