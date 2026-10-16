package com.example.insurancesystem.utils.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * QQ 邮箱的 SMTP 发信实现，继续服务于原有验证码场景。
 * 由邮件渠道配置决定是否注册为 Spring 组件；每次调用独立创建邮件会话，不维护跨请求状态。
 * 发件账号和 SMTP 授权码从运行环境注入，避免业务代码保存凭据。
 */
@Component
@ConditionalOnProperty(name = "insurance.email.provider", havingValue = "qq", matchIfMissing = true)
public class QqEmailUtil implements EmailUtil {

    private static final Logger log = LoggerFactory.getLogger(QqEmailUtil.class);
    private final String user;
    private final String password;

    /**
     * 接收部署环境中的 QQ 发件账号和 SMTP 授权码。
     * 缺少配置时仍允许应用启动，但发送请求会明确失败，不会尝试匿名发信。
     *
     * @param user QQ 邮箱地址
     * @param password QQ 邮箱 SMTP 授权码，不是登录密码
     */
    public QqEmailUtil(@Value("${insurance.email.qq.user:}") String user,
                       @Value("${insurance.email.qq.password:}") String password) {
        this.user = user;
        this.password = password;
    }

    /**
     * 通过 QQ SMTP 的 STARTTLS 通道提交纯文本邮件。
     * 认证、地址和网络异常只向调用方返回失败，不记录授权码或邮件正文。
     *
     * @param to 目标邮箱地址
     * @param subject 邮件主题
     * @param content 邮件正文
     * @return SMTP 提交成功返回 true，否则返回 false
     */
    @Override
    public boolean sendEmail(String to, String subject, String content) {
        if (user.isBlank() || password.isBlank() || to == null || subject == null || content == null) {
            log.warn("QQ 邮件发送失败：配置或邮件参数不完整");
            return false;
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.qq.com");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            /**
             * 在 SMTP 认证握手时提供当前 QQ 发件账号及其授权码。
             * 凭据仅在当前邮件会话内使用，不写入邮件内容。
             *
             * @return SMTP 认证信息
             */
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(user));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, true));
            message.setSubject(subject, "UTF-8");
            message.setText(content, "UTF-8");
            Transport.send(message);
            return true;
        } catch (MessagingException e) {
            log.warn("QQ 邮件发送失败：{}", e.getClass().getSimpleName());
            return false;
        }
    }
}
