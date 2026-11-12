package com.example.insurancesystem.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通过 Gemini 原生 Interactions API 读取保单 PDF、图片并整理纯文本结果。
 * Google 的签名 URL 文档输入及 JSON Schema 格式与两家 OpenAI 兼容接口不同，
 * 因此仅在模型传输层独立适配；共用保单提示词、Schema、任务重试和本地字段校验。
 * 客户端在应用启动时创建，请求本身不保存会话状态，同一实例可供后台任务并发使用。
 */
public class GeminiPolicyOcrClient implements PolicyOcrModel {
    private static final Logger log = LoggerFactory.getLogger(GeminiPolicyOcrClient.class);
    private static final int MAX_LOG_OUTPUT_CHARS = 3000;
    private final String apiKey;
    private final String baseUrl;
    private final String ocrModel;
    private final String organizerModel;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    /**
     * 固定 Gemini 密钥、地址和两阶段模型，避免异步任务中途切换供应商。
     * 未配置密钥时允许应用启动，实际识别请求会返回明确配置错误。
     *
     * @param apiKey Gemini Developer API Key
     * @param baseUrl Interactions API 的 v1beta 基础地址
     * @param ocrModel PDF 与图片提取模型
     * @param organizerModel 文本整理模型
     * @param mapper 应用已有 JSON 处理器
     */
    public GeminiPolicyOcrClient(String apiKey, String baseUrl, String ocrModel,
                                 String organizerModel, ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.ocrModel = ocrModel;
        this.organizerModel = organizerModel;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    /**
     * 直接让 Gemini 从 OSS 签名 URL 读取附件，按现有三险提示词生成结构化结果。
     * PDF 用 document，图片按扩展名选择 MIME；不额外下载文件或生成第二份上传副本。
     *
     * @param url 后端生成的临时 OSS 下载地址
     * @param pdf 文件是否为 PDF
     * @return Gemini 返回的 JSON 文本，后续交给共用校验器
     */
    @Override
    public String recognize(String url, boolean pdf) {
        Map<String, String> file = Map.of("type", pdf ? "document" : "image", "uri", url,
            "mime_type", pdf ? "application/pdf" : imageMimeType(url));
        return invoke("保单OCR", ocrModel, List.of(file, Map.of("type", "text", "text", PolicyOcrPrompt.EXTRACTION)));
    }

    /**
     * 只使用首次识别原文及本地校验问题进行 Gemini 文本整理，避免再次读取私有文件。
     * 与文件识别共用三险 Schema，整理后仍由既有业务校验器复核事实和格式。
     *
     * @param raw 首次识别返回的原文
     * @param errors 本轮完整校验问题
     * @return 整理后的 JSON 文本
     */
    @Override
    public String organize(String raw, List<String> errors) {
        String prompt = PolicyOcrPrompt.ORGANIZER_SYSTEM + "\n" + PolicyOcrPrompt.organize(raw, errors);
        return invoke("结构整理", organizerModel, List.of(Map.of("type", "text", "text", prompt)));
    }

    /**
     * 构造 Gemini 的 JSON Schema 请求并提取最后一段模型输出；每次只发送一个独立请求。
     * 临时 HTTP 或网络错误保留状态供现有文件识别调用器重试，整理次数仍由工作流控制。
     * 日志只包含模型、耗时和限长输出摘要，不记录密钥及 OSS 签名地址。
     *
     * @param stage 文件识别或结构整理阶段
     * @param model 本阶段使用的 Gemini 模型 ID
     * @param input 文件与提示词，或纯文本整理提示词
     * @return 模型输出文本；无文本时抛出错误
     */
    private String invoke(String stage, String model, List<Map<String, String>> input) {
        if (apiKey == null || apiKey.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("请配置 GEMINI_API_KEY 和 GEMINI_BASE_URL");
        }
        long started = System.nanoTime();
        /* 暂停大模型调用及校验日志，保留原语句便于恢复。 */
        // log.info("大模型开始调用 stage={}, provider=gemini, model={}", stage, model);
        try {
            Map<String, Object> body = Map.of("model", model, "input", input,
                "response_format", Map.of("type", "text", "mime_type", "application/json",
                    "schema", PolicyOcrSchema.SCHEMA.get("schema")));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/interactions"))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(8))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String status = "";
                try {
                    status = mapper.readTree(response.body()).path("error").path("status").asText("");
                } catch (IOException ignored) {
                    /*
                     * 网关可能返回非 JSON 错误页。保留 HTTP 状态供上层判断是否重试，
                     * 不让错误页的解析失败覆盖真正的供应商响应状态。
                     */
                }
                throw new PolicyOcrModelException(response.statusCode(),
                    "Gemini 接口返回 HTTP " + response.statusCode() + (status.isBlank() ? "" : "，" + status), null);
            }
            JsonNode document = mapper.readTree(response.body());
            String output = outputText(document);
            if (output.isBlank()) throw new IllegalStateException("Gemini 模型未返回识别内容");
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            String preview = output.length() > MAX_LOG_OUTPUT_CHARS ? output.substring(0, MAX_LOG_OUTPUT_CHARS) : output;
            /* 暂停大模型调用及校验日志，保留原语句便于恢复。 */
            // log.info("大模型调用完成 stage={}, provider=gemini, model={}, elapsedMs={}, outputChars={}, truncated={}, outputPreview={}",
            //     stage, model, elapsedMs, output.length(), output.length() > MAX_LOG_OUTPUT_CHARS,
            //     preview.replace("\r", "\\r").replace("\n", "\\n"));
            return output;
        } catch (IOException error) {
            throw new PolicyOcrModelException(0, "Gemini 接口连接失败", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini 识别请求已中断", error);
        } catch (RuntimeException error) {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            /* 暂停大模型调用及校验日志，保留原语句便于恢复。 */
            // log.warn("大模型调用失败 stage={}, provider=gemini, model={}, elapsedMs={}, errorType={}, message={}",
            //     stage, model, elapsedMs, error.getClass().getSimpleName(), error.getMessage());
            throw error;
        }
    }

    /**
     * 从 Interactions 新版响应的 model_output 步骤收集文本，忽略思考和工具步骤。
     * 多个文本片段按顺序合并；没有 model_output 时由调用方按空响应处理。
     *
     * @param response Gemini 返回的 JSON 对象
     * @return 可交给字段校验器的完整模型文本
     */
    private String outputText(JsonNode response) {
        List<String> parts = new ArrayList<>();
        for (JsonNode step : response.path("steps")) {
            if (!"model_output".equals(step.path("type").asText())) continue;
            for (JsonNode content : step.path("content")) {
                if ("text".equals(content.path("type").asText())) parts.add(content.path("text").asText(""));
            }
        }
        return String.join("", parts);
    }

    /**
     * 从 OSS 签名 URL 的对象路径推断图片 MIME，签名查询参数不参与扩展名判断。
     * 不支持的后缀直接报错，避免把格式错误的图片交给供应商而产生无效调用。
     *
     * @param url 服务端签发的图片访问地址
     * @return Gemini 文件输入要求的 MIME 类型
     */
    private String imageMimeType(String url) {
        String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".bmp")) return "image/bmp";
        if (path.endsWith(".tif") || path.endsWith(".tiff")) return "image/tiff";
        throw new IllegalArgumentException("Gemini 不支持该图片格式");
    }
}
