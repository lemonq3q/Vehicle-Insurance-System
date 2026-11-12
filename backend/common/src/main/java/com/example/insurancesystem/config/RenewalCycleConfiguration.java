package com.example.insurancesystem.config;

import com.example.insurancesystem.utils.RenewalCalendar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 将服务配置的续保自然月周期交给共享日历规则。车险工作台和企业门户各自启动时读取
 * 同一个环境变量，保证 SQL 查询、跟进写入和门户统计采用一致的测试周期。
 * 周期仅在服务启动时设置；运行中切换需要重启相关服务。
 */
@Component
public class RenewalCycleConfiguration {
    /**
     * 校验并应用续保周期。默认保持每 12 个月一轮；测试环境可设为 1，
     * 生产业务数据不应在测试周期下写入跟进结果。
     * @param months 配置文件解析后的自然月数。
     */
    public RenewalCycleConfiguration(@Value("${renewal.cycle-months:12}") int months) {
        RenewalCalendar.configureCycleMonths(months);
    }
}
