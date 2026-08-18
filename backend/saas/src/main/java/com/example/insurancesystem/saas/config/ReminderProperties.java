package com.example.insurancesystem.saas.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 近期提醒业务阈值。到期类固定使用 7 天和 1 天两阶段；工单接近额度使用 80% 和 90%；
 * 欠费停服预警按暂停阈值绝对风险的 80% 计算，所有值均允许按部署环境调整而无需修改业务代码。
 */
@Component
@ConfigurationProperties(prefix = "saas.reminder")
public class ReminderProperties {
    private int firstExpiryWarningDays = 7;
    private int urgentExpiryWarningDays = 1;
    private int quotaFirstPercent = 80;
    private int quotaUrgentPercent = 90;
    private BigDecimal nearSuspensionRatio = new BigDecimal("0.80");
    private int[] dataDeletionWarningDays = new int[]{30, 15, 3};

    public int getFirstExpiryWarningDays() { return firstExpiryWarningDays; }
    public void setFirstExpiryWarningDays(int value) { this.firstExpiryWarningDays = value; }
    public int getUrgentExpiryWarningDays() { return urgentExpiryWarningDays; }
    public void setUrgentExpiryWarningDays(int value) { this.urgentExpiryWarningDays = value; }
    public int getQuotaFirstPercent() { return quotaFirstPercent; }
    public void setQuotaFirstPercent(int value) { this.quotaFirstPercent = value; }
    public int getQuotaUrgentPercent() { return quotaUrgentPercent; }
    public void setQuotaUrgentPercent(int value) { this.quotaUrgentPercent = value; }
    public BigDecimal getNearSuspensionRatio() { return nearSuspensionRatio; }
    public void setNearSuspensionRatio(BigDecimal value) { this.nearSuspensionRatio = value; }
    public int[] getDataDeletionWarningDays() { return dataDeletionWarningDays; }
    public void setDataDeletionWarningDays(int[] value) { this.dataDeletionWarningDays = value; }
}
