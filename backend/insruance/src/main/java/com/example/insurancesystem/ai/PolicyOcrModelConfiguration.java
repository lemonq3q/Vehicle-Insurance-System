package com.example.insurancesystem.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按应用配置为保单异步识别选择阿里百炼、火山方舟或 Google Gemini 模型。
 * 任务、重试、整理和字段校验只依赖同一个客户端，不接触未选中供应商的密钥。
 * 配置在应用启动时固定；切换供应商或模型需更新后端环境变量并重启服务。
 */
@Configuration
public class PolicyOcrModelConfiguration {
    /**
     * 为完整保单识别流程创建唯一的模型客户端，识别和整理均使用同一供应商。
     * 保留既有阿里变量作为默认路径；各供应商变量独立配置，切换时不影响前端接口或 Redis 任务格式。
     *
     * @param provider 使用 qwen、doubao 或 gemini 的选择值
     * @param qwenApiKey 百炼密钥
     * @param qwenBaseUrl 百炼兼容接口地址
     * @param qwenOcrModel 百炼文件识别模型
     * @param qwenOrganizerModel 百炼文本整理模型
     * @param doubaoApiKey 方舟密钥
     * @param doubaoBaseUrl 方舟兼容接口地址
     * @param doubaoOcrModel 方舟文件识别模型
     * @param doubaoOrganizerModel 方舟文本整理模型
     * @param geminiApiKey Gemini Developer API 密钥
     * @param geminiBaseUrl Gemini Interactions API 基础地址
     * @param geminiOcrModel Gemini 文件识别模型
     * @param geminiOrganizerModel Gemini 文本整理模型
     * @param mapper 应用统一的 JSON 处理器
     * @return 本进程使用的模型客户端
     */
    @Bean
    public PolicyOcrModel policyOcrModelClient(
        @Value("${insurance.ai.provider:qwen}") String provider,
        @Value("${insurance.ai.api-key:}") String qwenApiKey,
        @Value("${insurance.ai.base-url:}") String qwenBaseUrl,
        @Value("${insurance.ai.ocr-model:qwen3.8-flash}") String qwenOcrModel,
        @Value("${insurance.ai.organizer-model:qwen3.8-flash}") String qwenOrganizerModel,
        @Value("${insurance.ai.doubao-api-key:}") String doubaoApiKey,
        @Value("${insurance.ai.doubao-base-url:https://ark.cn-beijing.volces.com/api/v3}") String doubaoBaseUrl,
        @Value("${insurance.ai.doubao-ocr-model:doubao-seed-2-1-lite-260915}") String doubaoOcrModel,
        @Value("${insurance.ai.doubao-organizer-model:doubao-seed-2-1-lite-260915}") String doubaoOrganizerModel,
        @Value("${insurance.ai.gemini-api-key:}") String geminiApiKey,
        @Value("${insurance.ai.gemini-base-url:https://generativelanguage.googleapis.com/v1beta}") String geminiBaseUrl,
        @Value("${insurance.ai.gemini-ocr-model:gemini-3.8-flash}") String geminiOcrModel,
        @Value("${insurance.ai.gemini-organizer-model:gemini-3.8-flash}") String geminiOrganizerModel,
        ObjectMapper mapper) {
        if ("qwen".equals(provider)) {
            return new PolicyOcrModelClient(provider, qwenApiKey, qwenBaseUrl, qwenOcrModel, qwenOrganizerModel);
        }
        if ("doubao".equals(provider)) {
            return new PolicyOcrModelClient(provider, doubaoApiKey, doubaoBaseUrl,
                doubaoOcrModel, doubaoOrganizerModel);
        }
        if ("gemini".equals(provider)) {
            return new GeminiPolicyOcrClient(geminiApiKey, geminiBaseUrl,
                geminiOcrModel, geminiOrganizerModel, mapper);
        }
        throw new IllegalArgumentException("POLICY_OCR_PROVIDER 只支持 qwen、doubao 或 gemini");
    }
}
