package com.example.insurancesystem.security.wechat;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/**
 * 验证微信安全模式消息签名并解密 XML/JSON；不接收未经签名的用户撤回事件。
 * 采用微信 AES-CBC、32 字节 PKCS#7 及尾部 AppID 校验；XML 禁用外部实体与 DTD。
 * 所有异常转换为不包含正文、签名密钥或用户资料的业务错误。
 */
@Component
public class WechatMessageCodec {
    private final WechatProperties config;
    private final ObjectMapper json;

    /** 注入后台签名配置及项目 JSON 编解码器，不使用用户 access_token。 */
    public WechatMessageCodec(WechatProperties config, ObjectMapper json) { this.config = config; this.json = json; }

    /**
     * 验证微信接收地址；兼容首次校验的普通 echostr 和安全模式的加密 echostr。
     * @param query 官方 GET 参数；不接受任意回调地址或应用标识
     * @return 微信要求原样返回的校验文本
     */
    public String verifyUrl(Map<String, String> query) {
        ready(); validateTime(query);
        String echo = query.getOrDefault("echostr", "");
        if (echo.isEmpty() || echo.length() > 8192) fail();
        if (query.containsKey("msg_signature")) {
            verify(query.get("msg_signature"), query.get("timestamp"), query.get("nonce"), echo);
            return decrypt(echo);
        }
        verify(query.get("signature"), query.get("timestamp"), query.get("nonce"));
        return echo;
    }

    /**
     * 仅解码消息安全模式，明文事件不被视为可信撤回操作。
     * @param body 外层 XML 或 JSON 加密消息，最大 128 KiB
     * @param query timestamp、nonce、msg_signature
     * @return 验签并核对 AppID 后的业务事件字段
     */
    public Map<String, String> decode(String body, Map<String, String> query) {
        ready(); validateTime(query);
        Map<String, String> envelope = fields(body);
        String encrypted = envelope.getOrDefault("Encrypt", envelope.getOrDefault("encrypt", ""));
        if (encrypted.isEmpty()) fail();
        verify(query.get("msg_signature"), query.get("timestamp"), query.get("nonce"), encrypted);
        Map<String, String> event = fields(decrypt(encrypted));
        if (!config.getAppId().equals(event.get("AppID"))) fail();
        return event;
    }

    /** 验证后台已显式启用消息服务，避免空共享参数使撤回入口被伪造。 */
    private void ready() {
        if (!config.isEnabled() || config.getAppId().isBlank() || config.getMessageToken().isBlank() || config.getEncodingAesKey().length() != 43)
            throw new BusinessException(503, "微信消息推送尚未配置");
    }

    /** 消息传输时间限十分钟并限制 nonce 长度，持久重试使用本地待办而不是依赖无限重放。 */
    private void validateTime(Map<String, String> query) {
        try {
            long timestamp = Long.parseLong(query.getOrDefault("timestamp", ""));
            if (Math.abs(Instant.now().getEpochSecond() - timestamp) > 600) fail();
            String nonce = query.getOrDefault("nonce", "");
            if (nonce.isEmpty() || nonce.length() > 256) fail();
        } catch (Exception exception) { fail(); }
    }

    /** 官方字典序 SHA-1 签名，按安全模式加入 Encrypt；常量时间比较避免直接信任请求来源。 */
    private void verify(String supplied, String... values) {
        try {
            List<String> parts = new ArrayList<>(); parts.add(config.getMessageToken());
            parts.addAll(Arrays.asList(values));
            if (supplied == null || !supplied.matches("[0-9a-fA-F]{40}") || parts.contains(null)) fail();
            Collections.sort(parts);
            byte[] expected = MessageDigest.getInstance("SHA-1").digest(String.join("", parts).getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(supplied))) fail();
        } catch (Exception exception) { fail(); }
    }

    /** 解密并严格校验填充、消息长度和 AppID 尾部，不把解密异常正文传播至日志。 */
    private String decrypt(String encrypted) {
        try {
            byte[] key = Base64.getDecoder().decode(config.getEncodingAesKey() + "=");
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(Arrays.copyOf(key, 16)));
            byte[] raw = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            int padding = raw[raw.length - 1] & 255;
            if (padding < 1 || padding > 32 || raw.length < padding + 20) fail();
            for (int i = raw.length - padding; i < raw.length; i++) if ((raw[i] & 255) != padding) fail();
            int length = ByteBuffer.wrap(raw, 16, 4).getInt();
            int end = raw.length - padding;
            if (length < 0 || length > end - 20) fail();
            String app = new String(raw, 20 + length, end - 20 - length, StandardCharsets.UTF_8);
            if (!config.getAppId().equals(app)) fail();
            return new String(raw, 20, length, StandardCharsets.UTF_8);
        } catch (Exception exception) { fail(); return ""; }
    }

    /** 提取有限长度、无外部实体的直接字段；只读取事件协议，不执行任何附件或嵌入内容。 */
    private Map<String, String> fields(String value) {
        try {
            if (value == null || value.length() > 131072) fail();
            Map<String, String> result = new LinkedHashMap<>();
            if (value.stripLeading().startsWith("{")) {
                json.readTree(value).fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
            } else {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
                NodeList nodes = factory.newDocumentBuilder().parse(new InputSource(new StringReader(value))).getDocumentElement().getChildNodes();
                for (int i = 0; i < nodes.getLength(); i++) {
                    Node node = nodes.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) result.put(node.getNodeName(), node.getTextContent());
                }
            }
            return result;
        } catch (Exception exception) { fail(); return Map.of(); }
    }

    /** 所有协议校验错误统一拒绝，不产生会话或用户资料副作用。 */
    private void fail() { throw new BusinessException(400, "微信消息签名或内容不正确"); }
}
