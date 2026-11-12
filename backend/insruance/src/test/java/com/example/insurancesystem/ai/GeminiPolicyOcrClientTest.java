package com.example.insurancesystem.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用本地 HTTP 服务核对 Gemini 原生协议的文件输入、整理输入和错误状态。
 * 不依赖真实密钥或外部服务；测试重点是现有保单流程交给 Gemini 的请求契约。
 */
class GeminiPolicyOcrClientTest {
    /**
     * 图片、PDF 和整理应分别发送正确的文件类型、模型与共用 Schema。
     * 整理阶段只包含文本，避免再次发送签名文件地址。
     */
    @Test
    void sendsPdfImageAndTextRequests() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> requests = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/interactions", exchange -> {
            assertEquals("test-key", exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            requests.add(mapper.readTree(exchange.getRequestBody()));
            byte[] response = "{\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"{}\"}]}]}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            GeminiPolicyOcrClient client = new GeminiPolicyOcrClient("test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta",
                "gemini-3.8-flash", "gemini-3.8-flash", mapper);
            assertEquals("{}", client.recognize("https://oss.example.test/policy.pdf?signature=abc", true));
            assertEquals("{}", client.recognize("https://oss.example.test/policy.png?signature=abc", false));
            assertEquals("{}", client.organize("{}", List.of("缺少保单号")));
            assertEquals(3, requests.size());
            JsonNode pdf = requests.get(0);
            assertEquals("document", pdf.path("input").get(0).path("type").asText());
            assertEquals("application/pdf", pdf.path("input").get(0).path("mime_type").asText());
            assertEquals("https://oss.example.test/policy.pdf?signature=abc",
                pdf.path("input").get(0).path("uri").asText());
            assertEquals("gemini-3.8-flash", pdf.path("model").asText());
            assertEquals("application/json", pdf.path("response_format").path("mime_type").asText());
            assertEquals("object", pdf.path("response_format").path("schema").path("type").asText());
            JsonNode image = requests.get(1);
            assertEquals("image", image.path("input").get(0).path("type").asText());
            assertEquals("image/png", image.path("input").get(0).path("mime_type").asText());
            JsonNode organizer = requests.get(2);
            assertEquals(1, organizer.path("input").size());
            assertEquals("text", organizer.path("input").get(0).path("type").asText());
            assertTrue(organizer.path("input").get(0).path("text").asText().contains("缺少保单号"));
            assertFalse(organizer.toString().contains("oss.example.test"));
        } finally {
            server.stop(0);
        }
    }

    /**
     * 将 Gemini 的 HTTP 失败状态原样送给现有重试分类，确保参数错误不会误判成临时故障。
     */
    @Test
    void preservesHttpFailureStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/interactions", exchange -> {
            byte[] response = "{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            GeminiPolicyOcrClient client = new GeminiPolicyOcrClient("test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta",
                "gemini-3.8-flash", "gemini-3.8-flash", new ObjectMapper());
            PolicyOcrModelException error = assertThrows(PolicyOcrModelException.class,
                () -> client.recognize("https://oss.example.test/policy.pdf", true));
            assertEquals(400, error.statusCode());
        } finally {
            server.stop(0);
        }
    }
}
