package com.example.insurancesystem.security.wechat;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.URI;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 仅从微信官方 HTTPS 端点验证授权身份，昵称拉取失败允许注册降级；不提取头像。
 * 与流程服务协作，微信令牌仅为方法局部变量，不进入日志、数据库、缓存或前端响应。
 */
@Component
@Slf4j
public class WechatClient {
    private final WechatProperties config;
    private final RestTemplate http;
    private final ObjectMapper json;

    /** 注入应用 JSON 配置并使用有限 HTTP 超时；响应解析策略仅用于微信客户端。 */
    public WechatClient(WechatProperties config, RestTemplateBuilder builder, ObjectMapper json) {
        this.config = config;
        this.http = builder.setConnectTimeout(Duration.ofSeconds(4)).setReadTimeout(Duration.ofSeconds(6)).build();
        this.json = json;
    }

    /**
     * 验证微信临时 code 并取得可信 openid；业务错误即使 HTTP 200 也拒绝登录。
     * @param code 来自微信回调的临时票据，流程服务已经校验原浏览器事务
     * @return 身份与可空昵称；不会包含 access_token、refresh_token 或头像
     */
    public Identity exchange(String code) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        long started = System.nanoTime();
        ResponseMetadata response = new ResponseMetadata();
        JsonNode token;
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com/sns/oauth2/access_token")
                    .queryParam("appid", "{app}").queryParam("secret", "{secret}")
                    .queryParam("code", "{code}").queryParam("grant_type", "authorization_code")
                    .encode().buildAndExpand(Map.of("app", config.getAppId(), "secret", config.getAppSecret(), "code", code)).toUri();
            token = requestJson(uri, response);
        } catch (Exception exception) {
            logFailure(traceId, "access_token", started, response, exception);
            throw failure(502, "微信授权服务暂不可用，请重试", traceId);
        }
        if (token == null || token.path("errcode").asInt(0) != 0
                || token.path("openid").asText("").isBlank() || token.path("access_token").asText("").isBlank()) {
            /* HTTP 成功不代表授权成功，只记录数值错误码，不记录包含凭据的原始响应。 */
            log.warn("WECHAT_AUTH_FAILURE traceId={} stage=access_token category=WECHAT_RESPONSE wechatErrcode={} httpStatus={} contentType={} elapsedMs={}",
                    traceId, token == null ? null : token.path("errcode").asInt(0), response.status, response.contentType, elapsedMillis(started));
            throw failure(400, "微信授权失败或已过期，请重新扫码", traceId);
        }
        String openId = token.path("openid").asText();
        String unionId = token.path("unionid").asText("");
        String nickname = "";
        /*
         * 昵称不是身份校验依据，拉取失败不阻止有效身份登录。明确忽略 headimgurl，
         * 注册服务会为无昵称用户生成姓名；用户编辑过的姓名不会因再次登录被覆盖。
         */
        started = System.nanoTime();
        response = new ResponseMetadata();
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com/sns/userinfo")
                    .queryParam("access_token", "{token}").queryParam("openid", "{openid}").queryParam("lang", "zh_CN")
                    .encode().buildAndExpand(Map.of("token", token.path("access_token").asText(), "openid", openId)).toUri();
            JsonNode info = requestJson(uri, response);
            if (info != null && info.path("errcode").asInt(0) == 0 && openId.equals(info.path("openid").asText())) {
                nickname = info.path("nickname").asText("").trim();
                if (unionId.isBlank()) unionId = info.path("unionid").asText("");
            } else {
                log.warn("WECHAT_AUTH_FAILURE traceId={} stage=userinfo category=WECHAT_RESPONSE wechatErrcode={} httpStatus={} contentType={} elapsedMs={} fallback=generated_name",
                        traceId, info == null ? null : info.path("errcode").asInt(0), response.status, response.contentType, elapsedMillis(started));
            }
        } catch (Exception exception) {
            logFailure(traceId, "userinfo", started, response, exception);
        }
        if (nickname.length() > 100) nickname = nickname.substring(0, 100);
        return new Identity(config.getAppId(), openId, unionId, nickname);
    }

    /**
     * 从固定微信端点读取原始响应后显式解析 JSON，不依赖对方是否声明 application/json。
     * 使用字节读取保留 UTF-8 中文昵称，避免 text/plain 缺省字符集导致乱码；不修改全局转换器。
     * @param uri 由本客户端构造的官方 HTTPS 地址，可能含凭据，禁止记录完整地址
     * @param metadata 当前调用独享的响应摘要，在解析前记录状态和媒体类型供失败诊断
     * @return 单个 JSON 对象，后续仍须校验微信错误码及身份字段
     * @throws IOException JSON 不合法、含多余内容或读取失败；空响应及非对象响应同样拒绝
     */
    private JsonNode requestJson(URI uri, ResponseMetadata metadata) throws IOException {
        ResponseEntity<byte[]> response = http.getForEntity(uri, byte[].class);
        metadata.status = response.getStatusCodeValue();
        metadata.contentType = contentType(response.getHeaders());
        byte[] body = response.getBody();
        /* 媒体类型仅作诊断；响应体必须是完整 JSON 对象，不能把 HTML 或部分 JSON 当作身份。 */
        if (body == null || body.length == 0) throw new HttpMessageConversionException("微信响应为空");
        JsonNode result = json.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(body);
        if (result == null || !result.isObject()) throw new HttpMessageConversionException("微信响应不是 JSON 对象");
        return result;
    }

    /**
     * 仅提取响应媒体类型用于诊断，不记录任意头部内容或参数；缺失及格式错误用固定标记。
     * 该步骤不影响 JSON 解析，避免非标准 Content-Type 本身阻止有效身份交换。
     */
    private String contentType(HttpHeaders headers) {
        if (headers == null) return "unknown";
        try {
            MediaType type = headers.getContentType();
            return type == null ? "missing" : type.getType() + "/" + type.getSubtype();
        } catch (IllegalArgumentException exception) {
            return "invalid";
        }
    }

    /**
     * 为身份交换失败附加本次请求的排查编号；两端既有错误页面会显示 msg，
     * data 同时提供结构化编号。保持原有业务码，不向客户端泄露服务器异常或凭据。
     */
    private BusinessException failure(int code, String message, String traceId) {
        return new BusinessException(code, message + "（排查编号：" + traceId + "）", Map.of("traceId", traceId));
    }

    /** 将单阶段单调时钟耗时换算成毫秒，仅用于诊断，不参与授权有效期判断。 */
    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    /**
     * 记录微信调用失败的原因分类、HTTP 状态及完整异常链的代码位置。
     * RestTemplate 异常文本可能包含 AppSecret、code、token 和响应体，因此禁止直接
     * 输出 Throwable/message；仅白名单输出异常类型和堆栈位置。昵称失败只告警并继续登录。
     * 异常链最多遍历十二层，避免异常对象循环引用导致日志处理阻塞认证线程。
     */
    private void logFailure(String traceId, String stage, long started, ResponseMetadata response, Exception exception) {
        String category = "UNKNOWN";
        Integer status = response.status;
        String mediaType = response.contentType;
        StringBuilder causes = new StringBuilder();
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause()) {
            if (cause instanceof RestClientResponseException) {
                status = ((RestClientResponseException) cause).getRawStatusCode();
                mediaType = contentType(((RestClientResponseException) cause).getResponseHeaders());
                category = "HTTP";
            } else if (cause instanceof UnknownContentTypeException) {
                status = ((UnknownContentTypeException) cause).getRawStatusCode();
                mediaType = contentType(((UnknownContentTypeException) cause).getResponseHeaders());
                category = "RESPONSE_CONTENT_TYPE";
            } else if (cause instanceof UnknownHostException) category = "DNS";
            else if (cause instanceof SocketTimeoutException) category = "TIMEOUT";
            else if (cause instanceof ConnectException) category = "CONNECT";
            else if (cause instanceof SSLException) category = "TLS";
            else if (cause instanceof HttpMessageConversionException || cause instanceof JsonProcessingException) category = "RESPONSE_PARSE";
            else if (cause instanceof IllegalArgumentException && "UNKNOWN".equals(category)) category = "CONFIG_OR_REQUEST";
            causes.append("\ncausedBy=").append(cause.getClass().getName());
            for (StackTraceElement frame : cause.getStackTrace()) {
                causes.append("\n  at ").append(frame);
            }
        }
        String details = "WECHAT_AUTH_FAILURE traceId=" + traceId + " stage=" + stage
                + " endpoint=api.weixin.qq.com/sns/" + ("access_token".equals(stage) ? "oauth2/access_token" : "userinfo")
                + " category=" + category + " httpStatus=" + status + " contentType=" + mediaType + " elapsedMs=" + elapsedMillis(started) + causes;
        if ("userinfo".equals(stage)) log.warn("{} fallback=generated_name", details);
        else log.error("{}", details);
    }

    /**
     * 单次 HTTP 调用的无凭据诊断摘要，身份交换与昵称查询各自独立，不跨请求或线程共享。
     * 只保存状态和媒体类型；不保存响应体、微信身份或令牌，也不写入持久化存储。
     */
    private static class ResponseMetadata {
        private Integer status;
        private String contentType = "unknown";
    }

    /** 微信服务端确认的最小身份数据，不接受浏览器直接构造的身份作为认证依据。 */
    public record Identity(String appId, String openId, String unionId, String nickname) { }
}
