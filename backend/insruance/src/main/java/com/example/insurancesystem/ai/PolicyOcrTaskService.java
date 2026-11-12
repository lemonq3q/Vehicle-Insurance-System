package com.example.insurancesystem.ai;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.security.EnterpriseContextHolder;
import com.example.insurancesystem.utils.RedisCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 管理承保 OCR 异步任务的提交、租户隔离、状态查询和短期结果存储。
 * Redis 保存成功及失败结果十五分钟；同企业同工单同文件的重复提交复用任务，
 * 避免浏览器或网络重发时重复计费。工作线程只持有提交时确认的企业 ID。
 */
@Service
public class PolicyOcrTaskService {
    private static final Logger log = LoggerFactory.getLogger(PolicyOcrTaskService.class);
    private static final int RESULT_TTL_MINUTES = 15;
    private static final long ACTIVE_TTL_SECONDS = 900;
    private static final String TASK_PREFIX = "policy:ocr:task:";
    private static final String ACTIVE_PREFIX = "policy:ocr:active:";

    private final PolicyOcrService policyOcrService;
    private final RedisCache redisCache;
    private final ThreadPoolTaskExecutor executor;

    public PolicyOcrTaskService(PolicyOcrService policyOcrService, RedisCache redisCache,
                                @Qualifier("policyOcrTaskExecutor") ThreadPoolTaskExecutor executor) {
        this.policyOcrService = policyOcrService;
        this.redisCache = redisCache;
        this.executor = executor;
    }

    /**
     * 校验当前企业的工单和已上传附件，原子创建短期任务并立即返回任务 ID。
     * 重复提交同一文件会返回已有任务；Redis 或线程池不可用时给出明确失败，
     * 不把实际模型调用留在 HTTP 请求线程内。
     *
     * @param request 前端上传完成后的工单和系统文件 ID
     * @return 任务标识与初始状态，或文件、权限和调度错误
     */
    public ResponseResult<?> submit(PolicyOcrRequest request) {
        Long enterpriseId = EnterpriseContextHolder.requireEnterpriseId();
        ResponseResult<?> invalid = policyOcrService.validateRequest(request, enterpriseId, false);
        if (invalid != null) return invalid;
        String activeKey = activeKey(enterpriseId, request);
        try {
            String existing = redisCache.getCacheObject(activeKey);
            if (existing != null && redisCache.getCacheObject(TASK_PREFIX + existing) != null) {
                return accepted(existing);
            }
            String jobId = UUID.randomUUID().toString();
            if (!redisCache.putIfAbsent(activeKey, jobId, ACTIVE_TTL_SECONDS)) {
                String claimed = redisCache.getCacheObject(activeKey);
                if (claimed != null && redisCache.getCacheObject(TASK_PREFIX + claimed) != null) {
                    return accepted(claimed);
                }
                return new ResponseResult<>(503, "识别任务正在初始化，请稍后重试");
            }
            save(jobId, enterpriseId, request, "QUEUED", null);
            try {
                executor.execute(() -> run(jobId, enterpriseId, request));
            } catch (RuntimeException ex) {
                redisCache.deleteObject(TASK_PREFIX + jobId);
                redisCache.deleteObject(activeKey);
                log.warn("承保 OCR 任务入队失败 jobId={}, errorType={}", jobId, ex.getClass().getSimpleName());
                return new ResponseResult<>(503, "识别任务繁忙，请稍后重试");
            }
            return accepted(jobId);
        } catch (RuntimeException ex) {
            log.warn("承保 OCR 任务创建失败 errorType={}", ex.getClass().getSimpleName());
            return new ResponseResult<>(503, "识别任务暂不可用，请稍后重试");
        }
    }

    /**
     * 按不可猜测的任务 ID 查询当前企业的结果，同时核对工单和文件索引。
     * 任务记录过期、跨企业或索引不符一律返回不存在，不泄漏其他企业的识别数据。
     *
     * @param jobId 提交接口返回的任务 ID
     * @return 排队、运行、成功或失败状态；成功时含原识别响应，失败时含业务错误
     */
    public ResponseResult<?> status(String jobId) {
        if (jobId == null || !jobId.matches("[0-9a-f-]{36}")) return new ResponseResult<>(404, "识别任务不存在或已过期");
        Long enterpriseId = EnterpriseContextHolder.requireEnterpriseId();
        try {
            Map<String, Object> task = redisCache.getCacheObject(TASK_PREFIX + jobId);
            if (task == null || !(task.get("enterpriseId") instanceof Number owner)
                    || owner.longValue() != enterpriseId) {
                return new ResponseResult<>(404, "识别任务不存在或已过期");
            }
            Map<String, Object> data = new LinkedHashMap<>(task);
            data.remove("enterpriseId");
            return new ResponseResult<>(200, "查询成功", data);
        } catch (RuntimeException ex) {
            log.warn("承保 OCR 任务查询失败 jobId={}, errorType={}", jobId, ex.getClass().getSimpleName());
            return new ResponseResult<>(503, "识别任务暂不可用，请稍后重试");
        }
    }

    /**
     * 在线程池内运行原有识别工作流，并将成功或失败都写入 Redis。
     * 供应商异常由原服务转换成业务错误；意外运行时异常也转为可查询的失败状态。
     */
    private void run(String jobId, Long enterpriseId, PolicyOcrRequest request) {
        try {
            save(jobId, enterpriseId, request, "RUNNING", null);
            ResponseResult<?> result = policyOcrService.recognize(request, enterpriseId);
            save(jobId, enterpriseId, request, result.getCode() == 200 ? "SUCCEEDED" : "FAILED", result);
            if (result.getCode() != 200) redisCache.deleteIfEquals(activeKey(enterpriseId, request), jobId);
            log.info("承保 OCR 任务结束 jobId={}, state={}, code={}", jobId,
                result.getCode() == 200 ? "SUCCEEDED" : "FAILED", result.getCode());
        } catch (RuntimeException ex) {
            log.warn("承保 OCR 任务异常 jobId={}, errorType={}", jobId, ex.getClass().getSimpleName());
            try {
                save(jobId, enterpriseId, request, "FAILED", new ResponseResult<>(502, "保单识别失败，请稍后重试"));
                redisCache.deleteIfEquals(activeKey(enterpriseId, request), jobId);
            } catch (RuntimeException cacheError) {
                log.error("承保 OCR 失败结果未能写入 Redis jobId={}", jobId, cacheError);
            }
        }
    }

    /**
     * 以独立 Redis 键保存任务索引、状态和最终业务响应，统一设置十五分钟过期。
     * 只保存结构化回填结果或错误，不缓存 OCR 原始全文与 OSS 临时签名地址。
     */
    private void save(String jobId, Long enterpriseId, PolicyOcrRequest request,
                      String state, ResponseResult<?> result) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("jobId", jobId);
        task.put("enterpriseId", enterpriseId);
        task.put("workorderId", String.valueOf(request.workorderId()));
        task.put("fileId", String.valueOf(request.fileId()));
        task.put("status", state);
        if (result != null) {
            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("code", result.getCode());
            outcome.put("msg", result.getMsg());
            if (result.getData() != null) outcome.put("data", result.getData());
            task.put("result", outcome);
        }
        redisCache.setCacheObject(TASK_PREFIX + jobId, task, RESULT_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 将同一任务 ID 返回给首次提交和重复提交的请求，前端据此开始短轮询。
     */
    private ResponseResult<?> accepted(String jobId) {
        return new ResponseResult<>(200, "识别任务已提交", Map.of("jobId", jobId));
    }

    /**
     * 按企业、工单和文件构造幂等索引，防止不同租户或附件相互复用任务。
     */
    private String activeKey(Long enterpriseId, PolicyOcrRequest request) {
        return ACTIVE_PREFIX + enterpriseId + ":" + request.workorderId() + ":" + request.fileId();
    }
}
