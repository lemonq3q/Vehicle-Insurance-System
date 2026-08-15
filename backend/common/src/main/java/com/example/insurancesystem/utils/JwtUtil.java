package com.example.insurancesystem.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * 负责生成和解析车险端 JWT。subject 保存用户 ID，jti 保存 Redis 单登录会话 ID，
 * 过滤器同时校验签名、有效期和 jti，因此仅持有未过期令牌不足以绕过新登录会话。
 */
public class JwtUtil {

    public static final Long JWT_TTL = 60 * 60 * 1000L;

    public static final Long LOGIN_JWT_TTL = 7 * 24 * 60 * 60 * 1000L;

    // 阈值比token逻辑过期时间长一些，避免token逻辑上没过期但是实际jwt已经过期的情况
    public static final Long JWT_REFRESH_THRESHOLD = 25 * 60 * 60 * 1000L;

    public static final String JWT_KEY = "lmy";

    /**
     * 生成不带连字符的随机 jti，用作 JWT 与 Redis 登录会话之间的唯一关联标识。
     */
    public static String getUUID(){
        String token = UUID.randomUUID().toString().replace("-", "");
        return token;
    }

    /**
     * 使用默认一小时有效期和新 jti 为指定用户主体创建令牌。
     */
    public static String createJWT(String subject){
        JwtBuilder builder = getJwtBuilder(subject, null, getUUID());
        return builder.compact();
    }

    /**
     * 使用默认有效期和调用方提供的 jti 创建令牌，适用于临期续签时保持同一 Redis 会话。
     */
    public static String createJWT(String subject, String uuid){
        JwtBuilder builder = getJwtBuilder(subject, null, uuid);
        return builder.compact();
    }

    /**
     * 使用指定有效期和自动生成的 jti 创建令牌。
     */
    public static String createJWT(String subject, Long ttlMillis){
        JwtBuilder builder = getJwtBuilder(subject, ttlMillis, getUUID());
        return builder.compact();
    }

    /**
     * 使用指定有效期与 jti 创建令牌，是登录签发和会话续签共用的完整入口。
     */
    public static String createJWT(String subject, Long ttlMillis, String uuid){
        JwtBuilder builder = getJwtBuilder(subject, ttlMillis, uuid);
        return builder.compact();
    }


    /**
     * 构造尚未压缩的 JWT：设置 jti、用户 subject、签发方、签发时间、HS256 签名和到期时间；
     * 调用方未提供有效期时回退到默认一小时。
     */
    private  static JwtBuilder getJwtBuilder(String subject, Long ttlMillis, String uuid){
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
        SecretKey secretKey = generalKey();
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        if (ttlMillis == null){
            ttlMillis = JwtUtil.JWT_TTL;
        }
        long expMillis = nowMillis + ttlMillis;
        Date expDate = new Date(expMillis);
        return Jwts.builder()
                .setId(uuid)
                .setSubject(subject)
                .setIssuer("lemon")
                .setIssuedAt(now)
                .signWith(signatureAlgorithm, secretKey)
                .setExpiration(expDate);
    }

    /**
     * 兼容显式 id 参数的历史签名方法，语义等同于用该 id 作为 jti 创建令牌。
     */
    public static String createJWT(String id, String subject, Long ttlMillis){
        JwtBuilder builder = getJwtBuilder(subject, ttlMillis, id);
        return builder.compact();
    }

    /**
     * 将 Base64 配置密钥恢复为 JJWT 签名所需 SecretKey，签发和验签必须使用同一结果。
     */
    public static SecretKey generalKey(){
        byte[] encodeKey = Base64.getDecoder().decode(JwtUtil.JWT_KEY);
        SecretKey key = new SecretKeySpec(encodeKey, 0, encodeKey.length, "AES");
        return key;
    }

    /**
     * 验证 JWT 签名和有效期并返回声明；非法、篡改或过期令牌由 JJWT 抛出异常交给认证过滤器处理。
     */
    public static Claims parseJWT(String jwt) throws Exception{
        SecretKey secretKey = generalKey();
        return Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(jwt)
                .getBody();
    }

    /**
     * 本地开发时用于快速验证签发和解析的独立调试入口，不参与应用运行链路。
     */
    public static void main(String[] args) throws Exception {
        String jwt = createJWT("1234");
        System.out.println(jwt);
        Claims claims = parseJWT("eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiI0ZTA3NDY4MzE5NTY0YzQyYWZhMWIwZDEzYTcxYzY5MyIsInN1YiI6IjEyMzQiLCJpc3MiOiJsZW1vbiIsImlhdCI6MTc2NzUxMzM0MSwiZXhwIjoxNzY3NTE2OTQxfQ.-LId8y_2MiDGAp6P6yONSiejsdc811e_IU4VL5A8YZ0");
        String subject = claims.getSubject();
        System.out.println(subject);

    }
}
