package com.example.insurancesystem.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行一次完整 OCR 校验和有界文本整理，独立于文件权限与 OSS 访问。
 * 每轮整理均传入原始 OCR 文本和上一轮完整错误清单，直到全部通过或达到配置上限。
 */
@Component
public class PolicyOcrWorkflow {
    private static final Logger log = LoggerFactory.getLogger(PolicyOcrWorkflow.class);
    private final PolicyOcrModel client;
    private final PolicyOcrValidator validator;
    private final int organizerRetries;

    public PolicyOcrWorkflow(PolicyOcrModel client, PolicyOcrValidator validator,
                             @Value("${insurance.ai.organizer-retries:2}") int organizerRetries) {
        this.client = client;
        this.validator = validator;
        if (organizerRetries < 0 || organizerRetries > 5) {
            throw new IllegalArgumentException("POLICY_OCR_ORGANIZER_RETRIES 必须在 0 到 5 之间");
        }
        this.organizerRetries = organizerRetries;
    }

    /**
     * 校验首次 OCR 原文；失败时先整理一次，之后按配置最多再重试指定次数。
     * 整理网络失败也占用一次机会，后续轮次仍以原文为事实来源；最终保留最近一份包含有效字段的 JSON 校验结果供部分回填。
     *
     * @param raw 首次且唯一一次文件 OCR 的原始文本
     * @return 最终校验结果；若仍不完整，调用方决定是否返回部分有效字段
     */
    public PolicyOcrValidator.ValidationResult validateAndOrganize(String raw) {
        PolicyOcrValidator.ValidationResult result = validator.validate(raw);
        PolicyOcrValidator.ValidationResult fallback = result.values().isEmpty() ? null : result;
        logValidation("保单OCR", 0, result);
        for (int attempt = 0; !result.valid() && attempt <= organizerRetries; attempt++) {
            try {
                String organized = client.organize(raw, result.errors());
                result = validator.validate(organized);
                if (!result.values().isEmpty()) fallback = result;
                logValidation("结构整理", attempt + 1, result);
            } catch (RuntimeException ex) {
                List<String> errors = new ArrayList<>(result.errors());
                errors.add("第 " + (attempt + 1) + " 次整理调用失败");
                result = new PolicyOcrValidator.ValidationResult(result.values(), errors, result.warnings());
                /* 暂停大模型调用及校验日志，保留原语句便于恢复。 */
                // log.warn("保单识别结构整理失败 attempt={}, errorType={}, message={}",
                //     attempt + 1, ex.getClass().getSimpleName(), ex.getMessage());
            }
        }
        /*
         * 整理机会耗尽后仅使用同一次任务中最近一份可解析 JSON 的有效字段。
         * 后续畸形 JSON 或调用异常不能抹掉此前已校验的数据，不拼接不同轮次的起终期。
         */
        return !result.valid() && result.values().isEmpty() && fallback != null ? fallback : result;
    }

    /**
     * 记录每轮本地校验结论及可回填字段，便于区分模型成功输出与业务验证通过。
     * 错误列表保留所有本轮发现的问题，模型返回正文由调用日志的独立配置开关控制。
     *
     * @param stage 产生被校验文本的模型阶段
     * @param attempt 整理轮次；原始 OCR 为零
     * @param result 本轮本地校验结果
     */
    private void logValidation(String stage, int attempt, PolicyOcrValidator.ValidationResult result) {
        /* 暂停大模型调用及校验日志，保留原语句便于恢复。 */
        // log.info("保单识别格式校验 stage={}, attempt={}, valid={}, fieldCount={}, fields={}, errors={}, warnings={}",
        //     stage, attempt, result.valid(), result.values().size(), result.values().keySet(),
        //     result.errors(), result.warnings());
    }
}
