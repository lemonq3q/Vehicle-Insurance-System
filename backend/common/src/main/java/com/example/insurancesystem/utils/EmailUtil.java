package com.example.insurancesystem.utils;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * 使用 QQ SMTP 发送纯文本通知邮件的基础工具，当前通过 STARTTLS 在 587 端口完成认证和传输。
 */
public class EmailUtil {

    private static final String HOST = "smtp.qq.com";
    private static final String PORT = "587";
    private static final String USER = "3041811612@qq.com";
    private static final String PASSWORD = "qbtwlkbrbxtfdfjd";


    /**
     * 创建带 SMTP 认证的邮件会话，组装发件人、收件人、主题和纯文本正文后同步发送；
     * 认证、地址或网络失败返回 false，调用方可据此提示或重试，成功返回 true。
     */
    public static boolean sendEmail(String to, String subject, String content) {
        Properties props = new Properties();
        props.put("mail.smtp.host", HOST);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", PORT);
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            @Override
            /**
             * 向 JavaMail 会话提供 SMTP 账号与授权凭证。
             */
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(USER, PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(USER));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(content);
            Transport.send(message);
        } catch (MessagingException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * 本地验证 SMTP 配置的调试入口，不参与 Web 应用调用链。
     */
    public static void main(String[] args) {
        sendEmail("1526863902@qq.com", "你好", "我是逆叠");
    }
}
