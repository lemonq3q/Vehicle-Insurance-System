package com.example.insurancesystem.ai;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.domain.workorder.Workorder;
import com.example.insurancesystem.mapper.SystemFileMapper;
import com.example.insurancesystem.mapper.WorkorderMapper;
import com.example.insurancesystem.utils.OSSUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证保单预识别不受工单流程阶段限制，同时继续检查工单企业归属。
 * 使用登录线程和 Mapper 替身，不访问 OSS、数据库或模型；缺失文件使流程在外部调用前结束。
 */
class PolicyOcrServiceTest {
    /**
     * 新建、待报价及后续工单均可预识别已上传保单，识别不承担承保状态推进。
     * 直接验证任务提交使用的同一校验入口，隔离 OSS 与模型调用，仅保留企业及文件校验。
     */
    @Test
    void policyRecognitionDoesNotRequireUnderwritingStage() {
        WorkorderMapper workorders = mock(WorkorderMapper.class);
        SystemFileMapper files = mock(SystemFileMapper.class);
        PolicyOcrService service = new PolicyOcrService(workorders, files, mock(OSSUtil.class),
            mock(PolicyOcrCaller.class), mock(PolicyOcrWorkflow.class));
        Workorder order = new Workorder(); order.setId(101L); order.setEnterpriseId(9L);
        com.example.insurancesystem.domain.SystemFile file = new com.example.insurancesystem.domain.SystemFile();
        file.setId(900L); file.setEnterpriseId(9L); file.setFileName("policy.pdf");
        when(workorders.selectById(101L)).thenReturn(order);
        when(files.selectById(900L)).thenReturn(file);
        for (int status = 0; status <= 10; status++) {
            order.setStatus(status);
            org.junit.jupiter.api.Assertions.assertNull(
                service.validateRequest(new PolicyOcrRequest(101L, 900L), 9L, false),
                "识别不应受工单状态限制：" + status);
        }
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 待报价工单在通过租户校验后应继续查询上传文件，而不再因状态 2 返回 400。
     * 异企业工单仍返回 404，不能借预识别放宽租户边界。
     */
    @Test
    void quotationStageCanProceedToFileCheckWhileTenantBoundaryRemains() {
        User user = new User(); user.setId(7L); user.setEnterpriseId(9L);
        LoginUser login = new LoginUser(user, List.of("workorder:update"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(login, null, List.of()));
        WorkorderMapper workorders = mock(WorkorderMapper.class);
        SystemFileMapper files = mock(SystemFileMapper.class);
        PolicyOcrService service = new PolicyOcrService(workorders, files, mock(OSSUtil.class),
            mock(PolicyOcrCaller.class), mock(PolicyOcrWorkflow.class));
        Workorder workorder = new Workorder(); workorder.setId(101L);
        workorder.setEnterpriseId(9L); workorder.setStatus(2);
        when(workorders.selectById(101L)).thenReturn(workorder);

        assertEquals(404, service.recognize(new PolicyOcrRequest(101L, 900L)).getCode());
        verify(files).selectById(900L);
        clearInvocations(files);
        workorder.setEnterpriseId(10L);
        assertEquals(404, service.recognize(new PolicyOcrRequest(101L, 900L)).getCode());
        verify(files, never()).selectById(900L);
    }
}
