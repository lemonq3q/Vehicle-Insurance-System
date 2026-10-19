package com.example.insurancesystem.security;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.security.wechat.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证安全模式事件加密、签名和应用身份绑定，不启动外部服务。
 * 覆盖合法撤回、伪造签名、错误应用、明文及 XXE 输入，防止外部请求强制退出用户。
 */
class WechatMessageCodecTest {
    private final ObjectMapper json = new ObjectMapper();
    private final byte[] key = new byte[32];

    /** 有效加密通知可解析原始业务字段。 */
    @Test
    void decryptsSignedEvent() throws Exception {
        String encrypted = encrypt("wx_test");
        assertEquals("openid", codec().decode(json.writeValueAsString(Map.of("Encrypt", encrypted)), query(encrypted)).get("OpenID"));
    }

    /** 未签名明文、其他应用的密文、篡改签名和外部实体均不进入撤回业务。 */
    @Test
    void rejectsForgedOrWrongApplicationMessages() throws Exception {
        String encrypted = encrypt("wx_other");
        assertThrows(BusinessException.class, () -> codec().decode(json.writeValueAsString(Map.of("Encrypt", encrypted)), query(encrypted)));
        String valid = encrypt("wx_test");
        Map<String, String> forged = new HashMap<>(query(valid)); forged.put("msg_signature", "0".repeat(40));
        assertThrows(BusinessException.class, () -> codec().decode(json.writeValueAsString(Map.of("Encrypt", valid)), forged));
        assertThrows(BusinessException.class, () -> codec().decode("{\"Event\":\"user_authorization_revoke\"}", query(valid)));
        assertThrows(BusinessException.class, () -> codec().decode("<!DOCTYPE xml [<!ENTITY x SYSTEM 'file:///test'>]><xml>&x;</xml>", query(valid)));
    }

    /** 使用测试消息密钥构造独立协议校验器。 */
    private WechatMessageCodec codec() {
        WechatProperties props = new WechatProperties(); props.setEnabled(true); props.setAppId("wx_test");
        props.setMessageToken("message-token"); props.setEncodingAesKey(Base64.getEncoder().withoutPadding().encodeToString(key));
        return new WechatMessageCodec(props, json);
    }

    /** 按微信二进制信封及 32 字节填充生成测试密文，独立于生产解码实现。 */
    private String encrypt(String appId) throws Exception {
        byte[] message = json.writeValueAsBytes(Map.of("Event", "user_authorization_revoke", "AppID", appId, "OpenID", "openid"));
        byte[] app = appId.getBytes(StandardCharsets.UTF_8);
        int size = 20 + message.length + app.length, padding = 32 - size % 32;
        ByteBuffer bytes = ByteBuffer.allocate(size + padding); bytes.put(new byte[16]).putInt(message.length).put(message).put(app);
        for (int i = 0; i < padding; i++) bytes.put((byte) padding);
        Cipher aes = Cipher.getInstance("AES/CBC/NoPadding"); aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(Arrays.copyOf(key, 16)));
        return Base64.getEncoder().encodeToString(aes.doFinal(bytes.array()));
    }

    /** 生成官方字典序签名和当前传输时间，业务事件时间不作为传输签名的替代。 */
    private Map<String, String> query(String encrypted) throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond()), nonce = "test-nonce";
        List<String> parts = new ArrayList<>(List.of("message-token", timestamp, nonce, encrypted)); Collections.sort(parts);
        String signature = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(String.join("", parts).getBytes(StandardCharsets.UTF_8)));
        return Map.of("timestamp", timestamp, "nonce", nonce, "msg_signature", signature);
    }
}
