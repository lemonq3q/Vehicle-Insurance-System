package com.example.insurancesystem.statistics;

import java.math.BigDecimal;

/**
 * 承载企业在一个统计周期内已结算工单的合并费用和盈利结果。金额始终使用十进制定点数，
 * 同时保留上下游费用便于日统计对账和后续纠错。
 */
public class ProfitSummary {
    private final long processedWorkorderCount;
    private final BigDecimal upstreamIncome;
    private final BigDecimal downstreamCost;

    /**
     * 创建不可变盈利汇总；调用方负责保证金额非空，盈利由上游费用减下游费用统一推导。
     */
    public ProfitSummary(long processedWorkorderCount, BigDecimal upstreamIncome, BigDecimal downstreamCost) {
        this.processedWorkorderCount = processedWorkorderCount;
        this.upstreamIncome = upstreamIncome;
        this.downstreamCost = downstreamCost;
    }

    public long getProcessedWorkorderCount() {
        return processedWorkorderCount;
    }

    public BigDecimal getUpstreamIncome() {
        return upstreamIncome;
    }

    public BigDecimal getDownstreamCost() {
        return downstreamCost;
    }

    public BigDecimal getProfitAmount() {
        return upstreamIncome.subtract(downstreamCost);
    }
}
