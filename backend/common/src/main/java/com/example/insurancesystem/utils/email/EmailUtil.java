package com.example.insurancesystem.utils.email;

/**
 * 统一车险系统的纯文本邮件发送能力，供验证码等业务调用。
 * 实际发信渠道由配置选择，调用方无需了解 QQ SMTP 或 Gmail API 的认证细节。
 * 发送失败返回 false，业务层据此决定是否保存验证码或向用户报告失败。
 */
public interface EmailUtil {

    /**
     * 向指定邮箱同步发送纯文本邮件。
     *
     * @param to 收件邮箱，由业务层提供
     * @param subject 邮件主题，由业务层提供
     * @param content 邮件正文，由业务层提供
     * @return 邮件服务确认接受请求时返回 true，参数、认证或网络失败时返回 false
     */
    boolean sendEmail(String to, String subject, String content);
}
