package com.example.insurancesystem.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputFile;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 封装阿里百炼与火山方舟的 OpenAI 兼容调用，供同一保单识别流程按配置选择供应商。
 * 两家共用提示词、字段校验和整理规则；仅 PDF 文件输入及供应商参数存在差异。
 * 客户端在应用生命周期内只连接一个供应商，SDK 禁用隐式重试，由业务层控制调用次数。
 */
public class PolicyOcrModelClient implements PolicyOcrModel {
    private static final Logger log = LoggerFactory.getLogger(PolicyOcrModelClient.class);
    private static final int MAX_LOG_OUTPUT_CHARS = 3000;
    private final String provider;
    private final String apiKey;
    private final String baseUrl;
    private final String ocrModel;
    private final String organizerModel;
    private volatile OpenAIClient client;

    /**
     * 固定本次应用使用的供应商和调用参数，避免异步任务运行中混用不同账号或模型。
     * 密钥仅保存在服务端内存，构造阶段校验供应商名称，实际请求阶段再检查密钥与地址。
     *
     * @param provider qwen 或 doubao，由后端配置选择
     * @param apiKey 所选供应商的 API Key
     * @param baseUrl 所选供应商的 OpenAI 兼容 API 地址
     * @param ocrModel 文件识别模型 ID
     * @param organizerModel 纯文本整理模型 ID
     */
    public PolicyOcrModelClient(String provider, String apiKey, String baseUrl,
                                String ocrModel, String organizerModel) {
        if (!"qwen".equals(provider) && !"doubao".equals(provider)) {
            throw new IllegalArgumentException("POLICY_OCR_PROVIDER 只支持 qwen 或 doubao");
        }
        this.provider = provider;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.ocrModel = ocrModel;
        this.organizerModel = organizerModel;
    }

    /**
     * 根据供应商的 PDF 接口约束和文件类型直接提取三险业务字段。
     * 百炼 PDF 使用 Chat Completions，火山 PDF 和两家图片使用 Responses API；
     * 两种路径都只接收服务端生成的私有 OSS 短时签名地址。
     *
     * @param url 服务端生成的短时 OSS GET 签名地址
     * @param pdf 文件是否为 PDF
     * @return 模型原始文本，供完整校验及可能的整理使用
     */
    @Override
    public String recognize(String url, boolean pdf) {
        if (pdf && "qwen".equals(provider)) return recognizeQwenPdf(url);
        ResponseInputItem.Message.Builder message = ResponseInputItem.Message.builder()
            .role(ResponseInputItem.Message.Role.USER);
        if (pdf) {
            message.addContent(ResponseInputFile.builder().fileUrl(url).build());
        } else {
            message.addContent(ResponseInputImage.builder().imageUrl(url).detail(ResponseInputImage.Detail.AUTO).build());
        }
        message.addInputTextContent(PolicyOcrPrompt.EXTRACTION);
        ResponseCreateParams.Builder params = ResponseCreateParams.builder()
            .model(ocrModel)
            .inputOfResponse(Collections.singletonList(ResponseInputItem.ofMessage(message.build())))
            .putAdditionalBodyProperty("text", JsonValue.from(Map.of("format", Map.of("type", "json_object"))));
        if ("doubao".equals(provider)) params.putAdditionalBodyProperty("thinking", JsonValue.from(Map.of("type", "disabled")));
        return invokeModel("保单OCR", ocrModel, params.build());
    }

    /**
     * 使用百炼原生 PDF 理解能力，让 Qwen3.8-Flash 直接读取 PDF 页面并返回业务 JSON。
     * 阿里当前不会把 Responses API 的 PDF 文件交给该模型，因此这里只切换 PDF 请求的 API 路径；
     * file_url 是百炼兼容接口的扩展字段，现有 SDK 的 FileObject 通过附加属性携带它。
     *
     * @param url 服务端生成的短时 OSS PDF GET 签名地址
     * @return 模型直接提取的三险 JSON 文本；空内容视为识别失败
     */
    private String recognizeQwenPdf(String url) {
        ChatCompletionContentPart.File.FileObject file = ChatCompletionContentPart.File.FileObject.builder()
            .putAdditionalProperty("file_url", JsonValue.from(url)).build();
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
            .model(ocrModel)
            .addUserMessageOfArrayOfContentParts(List.of(
                ChatCompletionContentPart.ofFile(ChatCompletionContentPart.File.builder().file(file).build()),
                ChatCompletionContentPart.ofText(ChatCompletionContentPartText.builder()
                    .text(PolicyOcrPrompt.EXTRACTION).build())))
            .putAdditionalBodyProperty("response_format", JsonValue.from(Map.of("type", "json_object")))
            .build();
        return invokePdfModel(params);
    }

    /**
     * 记录 PDF 视觉理解调用的模型、耗时和限长输出，便于与图片和整理阶段比较。
     * 请求包含 OSS 签名地址，日志只记录响应摘要；空 choices 或空文本不能当作成功。
     *
     * @param params 已构造的 Chat Completions PDF 请求
     * @return 首个候选项的文本内容
     */
    private String invokePdfModel(ChatCompletionCreateParams params) {
        long started = System.nanoTime();
        /* 请求与失败诊断已启用，保单输出正文仍不写入日志。 */
        log.info("大模型开始调用 stage=保单OCR, provider={}, model={}, endpoint={}/chat/completions", provider, ocrModel, diagnosticText(baseUrl));
        try {
            ChatCompletion completion = getClient().chat().completions().create(params);
            String output = completion.choices().isEmpty() ? "" : completion.choices().get(0)
                .message().content().orElse("");
            if (output.isBlank()) throw new IllegalStateException("模型未返回识别内容");
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            String preview = output.length() > MAX_LOG_OUTPUT_CHARS
                ? output.substring(0, MAX_LOG_OUTPUT_CHARS) : output;
            /* 请求与失败诊断已启用，保单输出正文仍不写入日志。 */
            // log.info("大模型调用完成 stage=保单OCR, provider={}, model={}, elapsedMs={}, outputChars={}, truncated={}, outputPreview={}",
            //     provider, ocrModel, elapsedMs, output.length(), output.length() > MAX_LOG_OUTPUT_CHARS,
            //     preview.replace("\r", "\\r").replace("\n", "\\n"));
            return output;
        } catch (RuntimeException error) {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            /* 请求与失败诊断已启用，保单输出正文仍不写入日志。 */
            // log.warn("大模型调用失败 stage=保单OCR, provider={}, model={}, elapsedMs={}, errorType={}, message={}",
            //     provider, ocrModel, elapsedMs, error.getClass().getSimpleName(), error.getMessage());
            log.warn("大模型调用失败 provider={}, endpoint={}, elapsedMs={}, details={}", provider, diagnosticText(baseUrl), elapsedMs, diagnosticText(error.toString()));
            throw error;
        } catch (LinkageError error) {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            /* 请求与失败诊断已启用，保单输出正文仍不写入日志。 */
            // log.error("大模型依赖链接失败 stage=保单OCR, provider={}, model={}, elapsedMs={}", provider, ocrModel, elapsedMs, error);
            log.warn("大模型调用失败 provider={}, endpoint={}, elapsedMs={}, details={}", provider, diagnosticText(baseUrl), elapsedMs, diagnosticText(error.toString()));
            throw error;
        }
    }

    /**
     * 用严格 JSON Schema 整理已识别文本；系统指令限制事实来源，用户消息列出全部校验问题。
     * 整理结果仍须由本地校验器复核，Schema 只能保证结构不能保证保单内容真实。
     *
     * @param raw 首次 OCR 的原始结果
     * @param errors 当前完整校验问题列表
     * @return 整理模型返回的 JSON 文本
     */
    @Override
    public String organize(String raw, List<String> errors) {
        Map<String, Object> format = "doubao".equals(provider)
            ? Map.of("type", "json_schema", "name", PolicyOcrSchema.SCHEMA.get("name"),
                "strict", PolicyOcrSchema.SCHEMA.get("strict"), "schema", PolicyOcrSchema.SCHEMA.get("schema"))
            : Map.of("type", "json_schema", "json_schema", PolicyOcrSchema.SCHEMA);
        Map<String, Object> text = Map.of("format", format);
        ResponseCreateParams.Builder params = ResponseCreateParams.builder()
            .model(organizerModel)
            .instructions(PolicyOcrPrompt.ORGANIZER_SYSTEM)
            .input(PolicyOcrPrompt.organize(raw, errors))
            .putAdditionalBodyProperty("text", JsonValue.from(text));
        if ("doubao".equals(provider)) params.putAdditionalBodyProperty("thinking", JsonValue.from(Map.of("type", "disabled")));
        return invokeModel("结构整理", organizerModel, params.build());
    }

    /**
     * 在实际 SDK 调用边界记录模型名称、调用阶段、耗时及有界输出摘要。
     * 大篇幅模型输出仅记录前段和原始长度，完整内容仍交给校验和整理流程；
     * 不记录请求体、OSS 签名地址或 API Key。
     *
     * @param stage 当前调用的业务阶段，区分文件识别和文本整理
     * @param model 本次请求显式指定的供应商模型 ID
     * @param params 已构造完成的 Responses 请求
     * @return 模型原始文本，供业务校验与回填
     */
    private String invokeModel(String stage, String model, ResponseCreateParams params) {
        long started = System.nanoTime();
        /* 请求与失败诊断已启用，保单输出正文仍不写入日志。 */
        log.info("大模型开始调用 stage={}, provider={}, model={}, endpoint={}/responses", stage, provider, model, diagnosticText(baseUrl));
        try {
            String output = outputText(getClient().responses().create(params));
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            String preview = output.length() > MAX_LOG_OUTPUT_CHARS
                ? output.substring(0, MAX_LOG_OUTPUT_CHARS) : output;
            /* 请求与失败诊断已启用，保单输出正文仍不写入日志。 */
            // log.info("大模型调用完成 stage={}, provider={}, model={}, elapsedMs={}, outputChars={}, truncated={}, outputPreview={}",
            //     stage, provider, model, elapsedMs, output.length(), output.length() > MAX_LOG_OUTPUT_CHARS,
            //     preview.replace("\r", "\\r").replace("\n", "\\n"));
            return output;
        } catch (RuntimeException error) {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            /* 请求与失败诊断已启用，保单输出正文仍不写入日志。 */
            // log.warn("大模型调用失败 stage={}, provider={}, model={}, elapsedMs={}, errorType={}, message={}",
            //     stage, provider, model, elapsedMs, error.getClass().getSimpleName(), error.getMessage());
            log.warn("大模型调用失败 provider={}, endpoint={}, elapsedMs={}, details={}", provider, diagnosticText(baseUrl), elapsedMs, diagnosticText(error.toString()));
            throw error;
        } catch (LinkageError error) {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            /* 请求与失败诊断已启用，保单输出正文仍不写入日志。 */
            // log.error("大模型依赖链接失败 stage={}, provider={}, model={}, elapsedMs={}", stage, provider, model, elapsedMs, error);
            log.warn("大模型调用失败 provider={}, endpoint={}, elapsedMs={}, details={}", provider, diagnosticText(baseUrl), elapsedMs, diagnosticText(error.toString()));
            throw error;
        }
    }

    /**
     * 对配置和 SDK 错误中的密钥及 URL 查询凭证脱敏，保留供应商状态和错误详情供排查。
     * 空输入返回空串；日志限长并转义换行，不改变实际请求和异常传播。
     */
    private String diagnosticText(String value) {
        if (value == null) return "";
        String safe = apiKey == null || apiKey.isBlank() ? value : value.replace(apiKey, "[REDACTED]");
        safe = safe.replaceAll("(https?://[^\\s?]+)\\?[^\\s\"<>]*", "$1?[REDACTED]")
            .replace("\r", "\\r").replace("\n", "\\n");
        return safe.length() > MAX_LOG_OUTPUT_CHARS ? safe.substring(0, MAX_LOG_OUTPUT_CHARS) + "[truncated]" : safe;
    }

    /**
     * 延迟创建 SDK 客户端，使缺少所选供应商配置时应用仍可启动，调用时返回明确错误。
     * 文件识别与结果整理复用所选供应商的地址与密钥，不连接默认 OpenAI 服务地址。
     * 关闭 SDK 隐式重试，使文件 OCR 临时错误重试及整理次数分别由显式业务流程控制。
     */
    private OpenAIClient getClient() {
        if (apiKey == null || apiKey.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("qwen".equals(provider)
                ? "请配置 DASHSCOPE_API_KEY 和 DASHSCOPE_BASE_URL"
                : "请配置 ARK_API_KEY 和 ARK_BASE_URL");
        }
        OpenAIClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) client = OpenAIOkHttpClient.builder().apiKey(apiKey).baseUrl(baseUrl)
                    .maxRetries(0).build();
                current = client;
            }
        }
        return current;
    }

    /**
     * 合并图片识别或纯文本整理的 Responses 输出文本。
     * 没有可解析内容时抛出错误，防止把空响应误认为未识别到保单。
     */
    private String outputText(Response response) {
        String result = response.output().stream()
            .flatMap(item -> item.message().stream())
            .flatMap(message -> message.content().stream())
            .flatMap(content -> content.outputText().stream())
            .map(output -> output.text())
            .filter(part -> part != null && !part.isBlank())
            .collect(Collectors.joining("\n"));
        if (result.isBlank()) throw new IllegalStateException("模型未返回识别内容");
        return result;
    }

}
