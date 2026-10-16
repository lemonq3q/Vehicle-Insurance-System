package com.example.insurancesystem.utils.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;

/**
 * Gmail API 的 OAuth 2.0 发信实现，用于以已授权的 Gmail 账号发送验证码邮件。
 * 仅在选择 Gmail 渠道时注册；每次发信先用离线刷新令牌获取短期访问令牌，
 * 再将 UTF-8 邮件提交给 Gmail messages.send，不在组件中保存可变授权状态。
 * OAuth 客户端凭据、刷新令牌和发件地址均由部署环境提供。
 */
@Component
@ConditionalOnProperty(name = "insurance.email.provider", havingValue = "gmail")
public class GmailEmailUtil implements EmailUtil {

    private static final Logger log = LoggerFactory.getLogger(GmailEmailUtil.class);
    private static final URI TOKEN_URI = URI.create("https://oauth2.googleapis.com/token");
    private static final URI SEND_URI = URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send");
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String from;

    /**
     * 载入 Gmail OAuth 客户端与已完成用户授权后取得的刷新令牌。
     * JSON 客户端文件本身不含刷新令牌；缺少任一配置时发送操作会失败。
     *
     * @param objectMapper Spring 提供的 JSON 解析器
     * @param clientId OAuth JSON 中的 client_id
     * @param clientSecret OAuth JSON 中的 client_secret
     * @param refreshToken 使用 gmail.send 权限和离线访问取得的刷新令牌
     * @param from 完成授权的 Gmail 发件地址
     */
    public GmailEmailUtil(ObjectMapper objectMapper,
                          @Value("${insurance.email.gmail.client-id:}") String clientId,
                          @Value("${insurance.email.gmail.client-secret:}") String clientSecret,
                          @Value("${insurance.email.gmail.refresh-token:}") String refreshToken,
                          @Value("${insurance.email.gmail.from:}") String from) {
        this.objectMapper = objectMapper;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.from = from;
    }

    /**
     * 使用发件账号的刷新令牌换取访问令牌，随后通过 Gmail API 发送 MIME 邮件。
     * 仅在两个 Google 接口均确认成功后返回 true，避免业务层误保存未发出的验证码。
     * 凭据、令牌与邮件正文不会写入日志；令牌失效时需要重新完成用户授权。
     *
     * @param to 目标邮箱地址
     * @param subject 邮件主题
     * @param content 纯文本邮件正文
     * @return Gmail 接受邮件时返回 true，其余情况返回 false
     */
    @Override
    public boolean sendEmail(String to, String subject, String content) {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank() || from.isBlank()
                || to == null || subject == null || content == null) {
            log.warn("Gmail 邮件发送失败：OAuth 配置或邮件参数不完整");
            return false;
        }

        try {
            String accessToken = refreshAccessToken();
            if (accessToken == null) {
                return false;
            }

            String raw = encodeMessage(to, subject, content);
            String body = objectMapper.createObjectNode().put("raw", raw).toString();
            HttpRequest request = HttpRequest.newBuilder(SEND_URI)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Gmail 邮件发送失败：Gmail API 返回 HTTP {}", response.statusCode());
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Gmail 邮件发送被中断");
            return false;
        } catch (IOException | MessagingException | IllegalArgumentException e) {
            log.warn("Gmail 邮件发送失败：{}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 使用服务端保存的离线刷新令牌申请短期访问令牌。
     * 每次发信重新获取令牌，避免并发请求共享过期状态；Google 拒绝授权时返回 null。
     *
     * @return 可用于 Gmail API 的访问令牌，失败时返回 null
     * @throws IOException 网络或响应解析失败
     * @throws InterruptedException 当前请求被中断
     */
    private String refreshAccessToken() throws IOException, InterruptedException {
        String form = "client_id=" + encodeForm(clientId)
                + "&client_secret=" + encodeForm(clientSecret)
                + "&refresh_token=" + encodeForm(refreshToken)
                + "&grant_type=refresh_token";
        HttpRequest request = HttpRequest.newBuilder(TOKEN_URI)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Gmail 访问令牌获取失败：Google OAuth 返回 HTTP {}", response.statusCode());
            return null;
        }
        JsonNode token = objectMapper.readTree(response.body()).path("access_token");
        if (!token.isTextual() || token.asText().isBlank()) {
            log.warn("Gmail 访问令牌获取失败：响应中没有 access_token");
            return null;
        }
        return token.asText();
    }

    /**
     * 将验证码等纯文本内容组装为 Gmail API 所需的 Base64URL MIME 消息。
     * JavaMail 负责处理地址、主题和正文编码，防止非 ASCII 内容出现乱码。
     *
     * @param to 收件邮箱
     * @param subject 邮件主题
     * @param content 邮件正文
     * @return 可放入 messages.send 的 raw 字段的编码结果
     * @throws MessagingException 地址或邮件头无效
     * @throws IOException 序列化邮件失败
     */
    private String encodeMessage(String to, String subject, String content) throws MessagingException, IOException {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress(from, true));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, true));
        message.setSubject(subject, "UTF-8");
        message.setText(content, "UTF-8");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    }

    /**
     * 对 OAuth 表单字段进行 URL 编码，确保密钥中的特殊字符不会改变参数边界。
     *
     * @param value 原始配置值
     * @return UTF-8 表单编码值
     */
    private String encodeForm(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
