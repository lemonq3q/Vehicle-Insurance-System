package com.example.insurancesystem.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.example.insurancesystem.statistics.UsageMetricRecorder;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 覆盖 OpenAI SDK 与服务运行时 Jackson 的实际链接，防止依赖树回退导致识别请求抛出 NoSuchMethodError。
 * 测试故意不配置密钥；SDK 构造请求后应到达本地配置校验，不发起网络请求。
 */
class PolicyOcrModelClientTest {
    /**
     * 图片识别与文本整理都须先成功初始化 SDK 的 JSON 模块。
     * 两条路径最终应因缺少测试密钥而抛出配置异常，而非 Jackson 链接错误。
     */
    @Test
    void sdkRequestBuildersLoadWithApplicationJackson() {
        PolicyOcrModelClient client = new PolicyOcrModelClient("qwen", "", "", "qwen3.8-flash", "qwen3.8-flash");
        assertThrows(IllegalStateException.class, () -> client.recognize("https://example.test/policy.png", false));
        assertThrows(IllegalStateException.class, () -> client.recognize("https://example.test/policy.pdf", true));
        assertThrows(IllegalStateException.class, () -> client.organize("{}", List.of("缺少保单字段")));
    }

    /**
     * 让 SDK 向本机假服务发送一次识别请求，覆盖实际 OkHttp 请求体构造与发送路径。
     * 假服务固定返回可重试的 500，不访问百炼；图片、PDF 和整理请求都必须各到达一次，
     * 同时验证 SDK 隐式重试已关闭，避免仅在某一路径上隐藏依赖错误或产生额外模型调用。
     */
    @Test
    void sdkCanSendRequestWithPackagedOkHttp() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> pdfRequest = new AtomicReference<>();
        server.createContext("/compatible-mode/v1/responses", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"error\":{\"message\":\"test rejection\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/compatible-mode/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            pdfRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"error\":{\"message\":\"test rejection\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            PolicyOcrModelClient client = new PolicyOcrModelClient("qwen", "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode/v1",
                "qwen3.8-flash", "qwen3.8-flash");
            RuntimeException error = assertThrows(RuntimeException.class,
                () -> client.recognize("https://example.test/policy.png", false));
            assertEquals(1, calls.get(), error.toString());
            RuntimeException pdfError = assertThrows(RuntimeException.class,
                () -> client.recognize("https://example.test/policy.pdf", true));
            assertEquals(2, calls.get(), pdfError.toString());
            JsonNode pdfBody = new ObjectMapper().readTree(pdfRequest.get());
            assertEquals("qwen3.8-flash", pdfBody.path("model").asText());
            assertEquals("file", pdfBody.path("messages").get(0).path("content").get(0).path("type").asText());
            assertEquals("https://example.test/policy.pdf",
                pdfBody.path("messages").get(0).path("content").get(0).path("file").path("file_url").asText());
            assertEquals(false, pdfBody.has("ocr_options"));
            assertEquals("json_object", pdfBody.path("response_format").path("type").asText());
            RuntimeException organizerError = assertThrows(RuntimeException.class,
                () -> client.organize("{}", List.of("缺少保单字段")));
            assertEquals(3, calls.get(), organizerError.toString());

            /*
             * 真正的 500 故障由业务调用器重试两次，三次网络尝试都进入企业 OCR 用量。
             * 整理模型不走此调用器，仍由独立工作流限制次数。
             */
            UsageMetricRecorder recorder = mock(UsageMetricRecorder.class);
            PolicyOcrCaller caller = new PolicyOcrCaller(client, recorder, 2);
            assertThrows(RuntimeException.class,
                () -> caller.recognize("https://example.test/policy.png", false, 9L));
            assertEquals(6, calls.get());
            verify(recorder, times(3)).recordOcrInvocation(9L);

            PolicyOcrModelClient invalidRequest = mock(PolicyOcrModelClient.class);
            when(invalidRequest.recognize("invalid", false)).thenThrow(new IllegalArgumentException("bad request"));
            PolicyOcrCaller noRetry = new PolicyOcrCaller(invalidRequest, recorder, 2);
            assertThrows(IllegalArgumentException.class, () -> noRetry.recognize("invalid", false, 10L));
            verify(recorder).recordOcrInvocation(10L);
        } finally {
            server.stop(0);
        }
    }

    /**
     * 模拟百炼 PDF 理解的成功响应，确认业务流程取得的是直接提取文本而非旧版版面结果。
     * 本地服务只测试 SDK 请求与响应契约，不替代真实保单的线上识别效果验证。
     */
    @Test
    void pdfRecognitionReadsChatCompletionContent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/compatible-mode/v1/chat/completions", exchange -> {
            byte[] body = ("{\"id\":\"test-pdf\",\"object\":\"chat.completion\",\"created\":1,"
                + "\"model\":\"qwen3.8-flash\",\"choices\":[{\"index\":0,\"finish_reason\":\"stop\","
                + "\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"commercial\\\":{}}\"}}]}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            PolicyOcrModelClient client = new PolicyOcrModelClient("qwen", "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode/v1",
                "qwen3.8-flash", "qwen3.8-flash");
            assertEquals("{\"commercial\":{}}", client.recognize("https://example.test/policy.pdf", true));
        } finally {
            server.stop(0);
        }
    }

    /**
     * 豆包使用同一提示词和整理链路，但 PDF、图片均走方舟 Responses 文件输入。
     * 验证模型、独立密钥、文件类型及关闭深度思考参数，避免切换供应商后仍向百炼发送文件。
     */
    @Test
    void doubaoUsesArkResponsesForPdfImageAndOrganizer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<JsonNode> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/api/v3/responses", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requests.add(new ObjectMapper().readTree(exchange.getRequestBody()));
            byte[] body = "{\"error\":{\"message\":\"test rejection\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            PolicyOcrModelClient client = new PolicyOcrModelClient("doubao", "ark-test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v3",
                "doubao-seed-2-1-lite-260915", "doubao-seed-2-1-lite-260915");
            assertThrows(RuntimeException.class, () -> client.recognize("https://example.test/policy.pdf", true));
            assertThrows(RuntimeException.class, () -> client.recognize("https://example.test/policy.png", false));
            assertThrows(RuntimeException.class, () -> client.organize("{}", List.of("缺少保单字段")));
            assertEquals(3, requests.size());
            assertEquals("Bearer ark-test-key", authorization.get());
            for (JsonNode request : requests) {
                assertEquals("doubao-seed-2-1-lite-260915", request.path("model").asText());
                assertEquals("disabled", request.path("thinking").path("type").asText());
            }
            assertEquals("input_file", requests.get(0).path("input").get(0).path("content").get(0).path("type").asText());
            assertEquals("https://example.test/policy.pdf",
                requests.get(0).path("input").get(0).path("content").get(0).path("file_url").asText());
            assertEquals("input_image", requests.get(1).path("input").get(0).path("content").get(0).path("type").asText());
            assertEquals("json_schema", requests.get(2).path("text").path("format").path("type").asText());
            assertEquals("vehicle_policy_ocr", requests.get(2).path("text").path("format").path("name").asText());
            assertEquals("object", requests.get(2).path("text").path("format").path("schema").path("type").asText());
            assertEquals(false, requests.get(2).path("text").path("format").has("json_schema"));
        } finally {
            server.stop(0);
        }
    }

    /**
     * 供应商选择只读取当前供应商的密钥和地址；未选中的配置为空不阻碍切换。
     * 非法供应商值在应用启动时拒绝，避免后台任务落到意外账号。
     */
    @Test
    void configurationSelectsOnlyRequestedProvider() {
        PolicyOcrModelConfiguration configuration = new PolicyOcrModelConfiguration();
        ObjectMapper mapper = new ObjectMapper();
        PolicyOcrModel qwen = configuration.policyOcrModelClient("qwen", "", "", "qwen3.8-flash",
            "qwen3.8-flash", "ark-key", "https://ark.example.test/api/v3", "doubao-lite", "doubao-lite",
            "gemini-key", "https://gemini.example.test/v1beta", "gemini-3.8-flash", "gemini-3.8-flash", false, mapper);
        assertEquals("请配置 DASHSCOPE_API_KEY 和 DASHSCOPE_BASE_URL",
            assertThrows(IllegalStateException.class, () -> qwen.recognize("https://example.test/a.png", false)).getMessage());
        PolicyOcrModel doubao = configuration.policyOcrModelClient("doubao", "qwen-key",
            "https://qwen.example.test/v1", "qwen3.8-flash", "qwen3.8-flash", "", "", "doubao-lite", "doubao-lite",
            "gemini-key", "https://gemini.example.test/v1beta", "gemini-3.8-flash", "gemini-3.8-flash", false, mapper);
        assertEquals("请配置 ARK_API_KEY 和 ARK_BASE_URL",
            assertThrows(IllegalStateException.class, () -> doubao.recognize("https://example.test/a.png", false)).getMessage());
        PolicyOcrModel gemini = configuration.policyOcrModelClient("gemini", "qwen-key", "", "qwen3.8-flash",
            "qwen3.8-flash", "ark-key", "", "doubao-lite", "doubao-lite", "",
            "https://gemini.example.test/v1beta", "gemini-3.8-flash", "gemini-3.8-flash", false, mapper);
        assertEquals("请配置 GEMINI_API_KEY 和 GEMINI_BASE_URL",
            assertThrows(IllegalStateException.class, () -> gemini.recognize("https://example.test/a.png", false)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> configuration.policyOcrModelClient("unknown", "", "", "",
            "", "", "", "", "", "", "", "", "", false, mapper));
    }
}
