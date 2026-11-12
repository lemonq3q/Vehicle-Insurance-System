package com.example.insurancesystem.ai;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.utils.RedisCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

/**
 * 验证承保 OCR 异步任务的快速入队、重复提交去重和企业结果隔离。
 * 以进程内 Map 模拟 Redis，不访问真实数据库、OSS 或模型供应商。
 */
class PolicyOcrTaskServiceTest {
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 同一企业、工单与文件重复提交应复用任务 ID，提交线程不能执行模型调用。
     * 任务状态查询对另一企业隐藏，避免 UUID 泄漏后跨租户读取结果。
     */
    @Test
    void duplicateSubmitReusesJobAndOtherEnterpriseCannotReadIt() {
        authenticate(9L);
        PolicyOcrService recognition = mock(PolicyOcrService.class);
        RedisCache cache = mock(RedisCache.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        Map<String, Object> values = new HashMap<>();
        when(cache.getCacheObject(any())).thenAnswer(call -> values.get(call.getArgument(0)));
        when(cache.putIfAbsent(any(), any(), anyLong())).thenAnswer(call -> {
            String key = call.getArgument(0);
            if (values.containsKey(key)) return false;
            values.put(key, call.getArgument(1));
            return true;
        });
        doAnswer(call -> {
            values.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(cache).setCacheObject(any(), any(), any(), any());
        PolicyOcrTaskService tasks = new PolicyOcrTaskService(recognition, cache, executor);
        PolicyOcrRequest request = new PolicyOcrRequest(101L, 900L);

        ResponseResult<?> first = tasks.submit(request);
        ResponseResult<?> second = tasks.submit(request);
        assertEquals(200, first.getCode());
        assertEquals(first.getData(), second.getData());
        String jobId = (String) ((Map<?, ?>) first.getData()).get("jobId");
        assertNotNull(jobId);
        assertEquals(200, tasks.status(jobId).getCode());
        verify(executor).execute(any(Runnable.class));
        verify(recognition, never()).recognize(eq(request), eq(9L));

        authenticate(10L);
        assertEquals(404, tasks.status(jobId).getCode());
    }

    /**
     * 后台识别失败也必须形成终态并保留原业务错误，轮询方才能停止等待。
     * 失败后释放同文件去重索引，用户可以再次提交而不等待缓存自然过期。
     */
    @Test
    void failedWorkerStoresResultAndReleasesActiveJob() {
        authenticate(9L);
        PolicyOcrService recognition = mock(PolicyOcrService.class);
        RedisCache cache = mock(RedisCache.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        Map<String, Object> values = new HashMap<>();
        AtomicReference<Runnable> worker = new AtomicReference<>();
        when(cache.getCacheObject(any())).thenAnswer(call -> values.get(call.getArgument(0)));
        when(cache.putIfAbsent(any(), any(), anyLong())).thenAnswer(call -> {
            values.put(call.getArgument(0), call.getArgument(1));
            return true;
        });
        doAnswer(call -> {
            values.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(cache).setCacheObject(any(), any(), any(), any());
        doAnswer(call -> {
            worker.set(call.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));
        PolicyOcrRequest request = new PolicyOcrRequest(101L, 900L);
        when(recognition.recognize(request, 9L)).thenReturn(new ResponseResult<>(502, "保单识别服务暂不可用"));
        PolicyOcrTaskService tasks = new PolicyOcrTaskService(recognition, cache, executor);

        String jobId = (String) ((Map<?, ?>) tasks.submit(request).getData()).get("jobId");
        worker.get().run();
        Map<?, ?> status = (Map<?, ?>) tasks.status(jobId).getData();
        assertEquals("FAILED", status.get("status"));
        assertEquals(502, ((Map<?, ?>) status.get("result")).get("code"));
        verify(cache).deleteIfEquals(eq("policy:ocr:active:9:101:900"), eq(jobId));
    }

    /**
     * 测试认证主体只提供企业索引，真实权限仍由控制器的 workorder:update 校验。
     */
    private void authenticate(Long enterpriseId) {
        User user = new User();
        user.setId(7L);
        user.setEnterpriseId(enterpriseId);
        LoginUser login = new LoginUser(user, List.of("workorder:update"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(login, null, List.of()));
    }
}
