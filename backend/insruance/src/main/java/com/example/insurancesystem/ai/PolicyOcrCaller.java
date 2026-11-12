package com.example.insurancesystem.ai;

import com.example.insurancesystem.statistics.UsageMetricRecorder;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 对文件 OCR 的临时供应商故障执行有界重试，并逐次记录实际发起的外部调用。
 * 只重试连接错误及 408、409、429、5xx；参数、权限和解析失败直接交还业务层。
 * 本类不处理结构整理，整理模型的次数由 PolicyOcrWorkflow 独立控制。
 */
@Component
public class PolicyOcrCaller {
    private final PolicyOcrModel client;
    private final UsageMetricRecorder usageMetricRecorder;
    private final int retries;

    public PolicyOcrCaller(PolicyOcrModel client, UsageMetricRecorder usageMetricRecorder,
                           @Value("${insurance.ai.ocr-request-retries:2}") int retries) {
        if (retries < 0 || retries > 5) throw new IllegalArgumentException("POLICY_OCR_REQUEST_RETRIES 必须在 0 到 5 之间");
        this.client = client;
        this.usageMetricRecorder = usageMetricRecorder;
        this.retries = retries;
    }

    /**
     * 识别已经通过企业、文件和 OSS 校验的附件；每次真实尝试前增加一次企业 OCR 计数。
     * 500 等短暂故障按配置最多重试指定次数，非暂时性错误不重试；最后一次错误原样交给服务层。
     *
     * @param url 后端签发的附件临时访问地址
     * @param pdf 是否为 PDF，决定 SDK 输入类型
     * @param enterpriseId 当前认证企业 ID，作为用量统计维度
     * @return OCR 模型原始文本，后续交给完整字段校验
     */
    public String recognize(String url, boolean pdf, long enterpriseId) {
        for (int attempt = 0; ; attempt++) {
            usageMetricRecorder.recordOcrInvocation(enterpriseId);
            try {
                return client.recognize(url, pdf);
            } catch (RuntimeException error) {
                if (attempt >= retries || !retryable(error)) throw error;
                try {
                    Thread.sleep(200L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("保单识别请求已中断", interrupted);
                }
            }
        }
    }

    /**
     * 仅将供应商的临时故障视为可重试；业务 4xx 除超时、冲突和限流外立即失败。
     * SDK 内部重试已关闭，因此此判断是文件 OCR 的唯一自动重试入口。
     */
    private boolean retryable(RuntimeException error) {
        if (error instanceof OpenAIIoException || error instanceof OpenAIRetryableException) return true;
        if (error instanceof PolicyOcrModelException modelError) {
            int status = modelError.statusCode();
            return status == 0 || status == 408 || status == 409 || status == 429 || status >= 500;
        }
        if (error instanceof OpenAIServiceException serviceError) {
            int status = serviceError.statusCode();
            return status == 408 || status == 409 || status == 429 || status >= 500;
        }
        return false;
    }
}
