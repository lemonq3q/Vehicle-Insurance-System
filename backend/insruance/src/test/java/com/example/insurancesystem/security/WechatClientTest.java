package com.example.insurancesystem.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.security.wechat.WechatClient;
import com.example.insurancesystem.security.wechat.WechatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.UnknownContentTypeException;
import org.springframework.http.HttpHeaders;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * 以真实 RestTemplate 和模拟响应验证微信诊断日志、错误契约与昵称降级。
 * 不连接微信、数据库或 Redis；捕获当前客户端日志，确保异常中的凭据不会进入诊断记录。
 */
class WechatClientTest {
    private WechatClient client;
    private MockRestServiceServer server;
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;
    private static final String SENSITIVE = "fake-secret-code-access-token";

    /** 每个用例独立构造 HTTP 模拟服务和日志捕获器，避免请求及事件相互污染。 */
    @BeforeEach
    void setup() {
        WechatProperties props = new WechatProperties();
        props.setAppId("wx_test"); props.setAppSecret(SENSITIVE);
        client = new WechatClient(props, new RestTemplateBuilder(), new ObjectMapper());
        server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(client, "http")).build();
        logger = (Logger) LoggerFactory.getLogger(WechatClient.class);
        logs = new ListAppender<>(); logs.start(); logger.addAppender(logs);
    }

    /** 释放附加日志监听器，并校验所有计划的请求已完成。 */
    @AfterEach
    void cleanup() {
        logger.detachAppender(logs); logs.stop(); server.verify();
    }

    /** 网络根因必须可区分，同时不输出带凭据的异常文本或请求 URL。 */
    @Test
    void recordsDnsTimeoutAndTlsWithoutSecrets() {
        assertNetworkFailure(new UnknownHostException(SENSITIVE), "DNS");
        assertNetworkFailure(new SocketTimeoutException(SENSITIVE), "TIMEOUT");
        assertNetworkFailure(new SSLHandshakeException(SENSITIVE), "TLS");
    }

    /** HTTP 错误仅记录状态码，错误响应体与 URL 里的密钥不进入日志。 */
    @Test
    void recordsHttpStatusWithoutResponseBody() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body(SENSITIVE));
        BusinessException failure = assertThrows(BusinessException.class, () -> client.exchange(SENSITIVE));
        assertDiagnostic(failure, "HTTP");
        assertTrue(logText().contains("httpStatus=502"));
        assertTrue(logText().contains("contentType=text/html"));
    }

    /** JSON、无字符集文本、HTML 类型及缺失类型头的 JSON 均按响应内容解析，中文昵称不能乱码。 */
    @Test
    void acceptsJsonWithDifferentMediaTypesAndPreservesChinese() {
        for (MediaType type : new MediaType[] { MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN,
                MediaType.parseMediaType("text/plain;charset=UTF-8"), MediaType.TEXT_HTML, null }) {
            server.reset(); logs.list.clear();
            server.expect(anything()).andRespond(withSuccess(identityBody().getBytes(StandardCharsets.UTF_8), type));
            server.expect(anything()).andRespond(withSuccess("{\"openid\":\"openid_test\",\"nickname\":\"微信昵称小李\"}".getBytes(StandardCharsets.UTF_8), type));
            WechatClient.Identity identity = client.exchange(SENSITIVE);
            assertEquals("openid_test", identity.openId());
            assertEquals("微信昵称小李", identity.nickname());
            assertEquals("", logText());
            server.verify();
        }
    }

    /** 空白、HTML、截断 JSON、非对象及尾随内容不得变成登录身份，日志须保留实际响应状态和类型。 */
    @Test
    void rejectsInvalidResponsesWithoutLeakingBody() {
        for (String body : new String[] { "", "  ", "<html>" + SENSITIVE + "</html>", "{\"access_token\":\"" + SENSITIVE,
                "null", "[]", "42", identityBody() + " {}" }) {
            server.reset(); logs.list.clear();
            server.expect(anything()).andRespond(withSuccess(body, MediaType.TEXT_PLAIN));
            assertDiagnostic(assertThrows(BusinessException.class, () -> client.exchange(SENSITIVE)), "RESPONSE_PARSE");
            assertTrue(logText().contains("httpStatus=200"));
            assertTrue(logText().contains("contentType=text/plain"));
            server.verify();
        }
    }

    /** 微信业务错误即使标记为纯文本也须显示授权失败，不误报网络不可用。 */
    @Test
    void textResponseRetainsWechatBusinessRejection() {
        server.expect(anything()).andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"" + SENSITIVE + "\"}", MediaType.TEXT_PLAIN));
        assertEquals(400, assertThrows(BusinessException.class, () -> client.exchange(SENSITIVE)).getCode());
        assertTrue(logText().contains("wechatErrcode=40029"));
        assertTrue(logText().contains("httpStatus=200 contentType=text/plain"));
        assertFalse(logText().contains(SENSITIVE));
    }

    /** 对象缺少可信身份字段时维持拒绝登录，不因解析兼容而接受空身份。 */
    @Test
    void rejectsMissingIdentityFields() {
        server.expect(anything()).andRespond(withSuccess("{}", MediaType.TEXT_PLAIN));
        assertEquals(400, assertThrows(BusinessException.class, () -> client.exchange(SENSITIVE)).getCode());
        assertTrue(logText().contains("category=WECHAT_RESPONSE"));
    }

    /** 无法解析昵称响应或返回其他身份时，只降级姓名，不能替换已验证的微信身份。 */
    @Test
    void invalidUserInfoPreservesTrustedIdentity() {
        for (String body : new String[] { "<html>" + SENSITIVE + "</html>", "{\"openid\":\"other\",\"nickname\":\"他人\"}",
                "{\"errcode\":40001,\"errmsg\":\"" + SENSITIVE + "\"}" }) {
            server.reset(); logs.list.clear();
            server.expect(anything()).andRespond(withSuccess(identityBody(), MediaType.TEXT_PLAIN));
            server.expect(anything()).andRespond(withSuccess(body, MediaType.TEXT_PLAIN));
            WechatClient.Identity identity = client.exchange(SENSITIVE);
            assertEquals("openid_test", identity.openId());
            assertEquals("", identity.nickname());
            assertTrue(logText().contains("stage=userinfo"));
            assertTrue(logText().contains("fallback=generated_name"));
            assertFalse(logText().contains(SENSITIVE));
            server.verify();
        }
    }

    /** 预留对定制 HTTP 转换器故障的诊断，不能再把已收到响应的类型错误记录为 UNKNOWN。 */
    @Test
    void recordsUnknownContentTypeMetadataWithoutBody() {
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.TEXT_HTML);
        server.expect(anything()).andRespond(request -> {
            throw new UnknownContentTypeException(Object.class, MediaType.TEXT_HTML, 200, "OK", headers, SENSITIVE.getBytes(StandardCharsets.UTF_8));
        });
        assertDiagnostic(assertThrows(BusinessException.class, () -> client.exchange(SENSITIVE)), "RESPONSE_CONTENT_TYPE");
        assertTrue(logText().contains("httpStatus=200 contentType=text/html"));
    }

    /** 构造不包含真实令牌的有效身份响应，供各媒体类型及昵称降级场景复用。 */
    private String identityBody() {
        return "{\"openid\":\"openid_test\",\"access_token\":\"" + SENSITIVE + "\"}";
    }

    /** 非 JSON 响应明确归类为响应解析故障，保持登录失败且不泄露原文。 */
    @Test
    void recordsResponseParseFailure() {
        server.expect(anything()).andRespond(withSuccess(SENSITIVE, MediaType.APPLICATION_JSON));
        assertDiagnostic(assertThrows(BusinessException.class, () -> client.exchange(SENSITIVE)), "RESPONSE_PARSE");
    }

    /** 微信 HTTP 200 业务拒绝保留原有 400，并记录可查阅官方含义的错误码。 */
    @Test
    void recordsWechatErrorCode() {
        server.expect(anything()).andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"" + SENSITIVE + "\"}", MediaType.APPLICATION_JSON));
        BusinessException failure = assertThrows(BusinessException.class, () -> client.exchange(SENSITIVE));
        assertEquals(400, failure.getCode());
        assertTrue(logText().contains("wechatErrcode=40029"));
        assertFalse(logText().contains(SENSITIVE));
    }

    /** 昵称查询失败只产生可追踪告警，有效微信身份仍交给既有注册或登录逻辑。 */
    @Test
    void nicknameFailureDoesNotRejectIdentity() {
        server.expect(anything()).andRespond(withSuccess("{\"openid\":\"openid_test\",\"access_token\":\"" + SENSITIVE + "\"}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withServerError().body(SENSITIVE));
        assertEquals("", client.exchange(SENSITIVE).nickname());
        assertTrue(logText().contains("stage=userinfo"));
        assertTrue(logText().contains("fallback=generated_name"));
        assertEquals("WARN", logs.list.get(0).getLevel().toString());
        assertFalse(logText().contains(SENSITIVE));
    }

    /** 为网络根因注入包含假敏感文本的异常，验证只保留异常类型和代码位置。 */
    private void assertNetworkFailure(java.io.IOException cause, String category) {
        server.reset(); logs.list.clear();
        server.expect(anything()).andRespond(request -> { throw new ResourceAccessException(SENSITIVE, cause); });
        assertDiagnostic(assertThrows(BusinessException.class, () -> client.exchange(SENSITIVE)), category);
        assertTrue(logText().contains(cause.getClass().getName()));
        server.verify();
    }

    /** 同一个排查编号必须贯穿前端提示、结构化响应与服务器日志。 */
    private void assertDiagnostic(BusinessException failure, String category) {
        assertEquals(502, failure.getCode());
        String traceId = (String) ((Map<?, ?>) failure.getData()).get("traceId");
        assertTrue(traceId.matches("[a-f0-9]{32}"));
        assertTrue(failure.getMsg().contains(traceId));
        assertTrue(logText().contains("traceId=" + traceId));
        assertTrue(logText().contains("category=" + category));
        assertFalse(logText().contains(SENSITIVE));
        assertFalse(logText().contains("secret="));
    }

    /** 仅收集业务客户端格式化后的日志，不依赖 HTTP 库自身的调试输出。 */
    private String logText() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b);
    }
}
