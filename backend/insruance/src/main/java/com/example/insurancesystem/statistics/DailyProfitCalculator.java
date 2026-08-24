package com.example.insurancesystem.statistics;

import java.time.LocalDate;
import java.util.Map;

/**
 * 定义已结算工单的每日盈利计算接口。日终统计只依赖该契约，未来调整税费、退款或其他成本口径时，
 * 可以替换实现而无需改动 Redis 归档和统计表写入流程。
 */
public interface DailyProfitCalculator {

    /**
     * 汇总指定北京时间自然日内结算完毕的全部企业工单。
     *
     * @param date 以 finish_time 归属的自然日
     * @return 企业 ID 到完成工单数、合并上下游费用及盈利的映射
     */
    Map<Long, ProfitSummary> calculate(LocalDate date);
}
