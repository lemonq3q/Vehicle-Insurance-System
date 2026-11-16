package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.SystemFile;
import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.domain.workorder.*;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.mapper.*;
import com.example.insurancesystem.service.impl.WorkorderServiceImpl;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 阶段保存服务回归，验证白名单、当前状态和附件边界。
 * 使用真实登录线程和持久层替身，测试不会连接数据库或上传文件。
 */
class WorkorderPolicyServiceTest {
    private final WorkorderAggregateMapper aggregate = mock(WorkorderAggregateMapper.class);
    private final WorkorderMapper orders = mock(WorkorderMapper.class);
    private final WorkorderFileMapper relations = mock(WorkorderFileMapper.class);
    private final SystemFileMapper files = mock(SystemFileMapper.class);
    private final WorkorderServiceImpl service = new WorkorderServiceImpl();
    private Workorder current;

    /**

     * 建立企业9的管理员身份并注入有效、已完成的本企业工单及空附件关系。

     */
    @BeforeEach
    void setup() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "workorder-policy-service-test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, WorkorderFile.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Workorder.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, SystemFile.class);
        User user = new User(); user.setId(7L); user.setEnterpriseId(9L);
        LoginUser login = new LoginUser(user, List.of("all"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, null, List.of()));
        ReflectionTestUtils.setField(service, "workorderAggregateMapper", aggregate);
        ReflectionTestUtils.setField(service, "workorderMapper", orders);
        ReflectionTestUtils.setField(service, "workorderFileMapper", relations);
        ReflectionTestUtils.setField(service, "systemFileMapper", files);
        current = new Workorder(); current.setId(101L); current.setEnterpriseId(9L); current.setHandleBy(7L);
        current.setStatus(10); current.setFinishTime(1793462400L);
        when(aggregate.lockWorkorder(101L, 9L)).thenReturn(current);
        when(relations.selectList(any())).thenReturn(List.of());
        when(relations.selectCount(any())).thenReturn(0L);
        SystemFile file = new SystemFile(); file.setId(900L); file.setEnterpriseId(9L);
        when(files.selectOne(any())).thenReturn(file);
    }

    /** 两项提醒允许在同一次更新中分别保存续保和流失，不能将结果合并为同一状态。 */
    @Test
    void renewalFollowUpSavesDifferentResultsTogether() {
        long start = java.time.LocalDate.of(2024, 1, 1)
                .atStartOfDay(com.example.insurancesystem.utils.RenewalCalendar.BUSINESS_ZONE).toEpochSecond();
        current.setCommercialInsuranceStartTime(start);
        current.setCompulsoryInsuranceStartTime(start);
        when(orders.selectOne(any())).thenReturn(current);
        when(orders.update(isNull(), any())).thenReturn(1);
        RenewalFollowUpRequest request = new RenewalFollowUpRequest();
        request.setCommercialStatus(1);
        request.setCompulsoryStatus(2);
        assertEquals(200, service.followUpRenewal(101L, request).getCode());
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper> update =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(orders).update(isNull(), update.capture());
        assertTrue(update.getValue().getSqlSet().contains("commercial_renewal_status"));
        assertTrue(update.getValue().getSqlSet().contains("compulsory_renewal_status"));
        assertTrue(update.getValue().getParamNameValuePairs().containsValue(1));
        assertTrue(update.getValue().getParamNameValuePairs().containsValue(2));
    }

    /** 已处理的当前险种允许改回未处理，保存零状态后重新进入待办条件。 */
    @Test
    void renewalFollowUpCanRestoreUnprocessedStatus() {
        long start = java.time.LocalDate.of(2024, 1, 1)
                .atStartOfDay(com.example.insurancesystem.utils.RenewalCalendar.BUSINESS_ZONE).toEpochSecond();
        current.setCommercialInsuranceStartTime(start);
        current.setCommercialRenewalStatus(1);
        when(orders.selectOne(any())).thenReturn(current);
        when(orders.update(isNull(), any())).thenReturn(1);
        RenewalFollowUpRequest request = new RenewalFollowUpRequest();
        request.setCommercialStatus(0);
        assertEquals(200, service.followUpRenewal(101L, request).getCode());
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper> update =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(orders).update(isNull(), update.capture());
        assertTrue(update.getValue().getParamNameValuePairs().containsValue(0));
        assertTrue(update.getValue().getSqlSet().contains("commercial_renewal_status"));
        assertFalse(update.getValue().getSqlSet().contains("compulsory_renewal_status"));
    }

    /** 截图中的商业已续保、交强未处理组合在新版接口中必须被接受。 */
    @Test
    void renewalFollowUpAcceptsRenewedAndUnprocessedTogether() {
        long start = java.time.LocalDate.of(2024, 1, 1)
                .atStartOfDay(com.example.insurancesystem.utils.RenewalCalendar.BUSINESS_ZONE).toEpochSecond();
        current.setCommercialInsuranceStartTime(start);
        current.setCompulsoryInsuranceStartTime(start);
        when(orders.selectOne(any())).thenReturn(current);
        when(orders.update(isNull(), any())).thenReturn(1);
        RenewalFollowUpRequest request = new RenewalFollowUpRequest();
        request.setCommercialStatus(1);
        request.setCompulsoryStatus(0);
        assertEquals(200, service.followUpRenewal(101L, request).getCode());
    }

    /**

     * 清理线程身份，后续测试不能继承当前用户权限。

     */
    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    /**

     * 恶意附带的保费、保单和支付金额不能通过报价入口保存，已完成状态不能倒退。

     */
    @Test
    void quoteCannotOverwritePolicyOrPayment() {
        WorkorderDTO request = WorkorderPolicyValidatorTest.request("COMMERCIAL");
        request.setStatus(8); request.setQuotationRemark("新反馈"); request.setWorkorderFileList(List.of());
        request.setRequiredPayAmount(BigDecimal.TEN);
        service.updateQuotation(request);
        ArgumentCaptor<Workorder> capture = ArgumentCaptor.forClass(Workorder.class);
        verify(aggregate).updateQuote(capture.capture());
        Workorder quote = capture.getValue();
        assertNull(quote.getCommercialAmount()); assertNull(quote.getCommercialPolicyNo());
        assertNull(quote.getRequiredPayAmount()); assertEquals(10, quote.getStatus());
        verify(aggregate, never()).replacePolicies(any());
        verify(aggregate, never()).replaceFinancials(any());
    }

    /**

     * 完整承保写两个目标且保留原完成时间；客户端无法伪造完成日期或修改支付录入金额。

     */
    @Test
    void acceptanceKeepsOriginalCompletionAndSeparatesPayment() {
        WorkorderDTO request = WorkorderPolicyValidatorTest.request("COMMERCIAL");
        request.setFinishTime(1L); request.setRequiredPayAmount(BigDecimal.TEN);
        service.acceptInsurance(request);
        ArgumentCaptor<Workorder> capture = ArgumentCaptor.forClass(Workorder.class);
        verify(aggregate).replacePolicies(capture.capture());
        Workorder update = capture.getValue();
        assertEquals(current.getFinishTime(), update.getFinishTime());
        assertNull(update.getRequiredPayAmount());
        assertNull(update.getCompulsoryPolicyNo());
        assertNull(update.getNonMotorAmount());
        verify(aggregate).replaceFinancials(update);
        verify(aggregate).updateMainStage(update);
    }

    /**
     * 旧客户端提交的单份非车险类型在保存前归并为其他单证类型。
     * 历史关系仍由同一次整体替换清理，不需要修改数据库字段或迁移既有记录。
     */
    @Test
    void legacyNonMotorFileIsNormalizedOnAcceptance() {
        WorkorderDTO request = WorkorderPolicyValidatorTest.request("NON_MOTOR");
        request.getWorkorderFileList().get(0).setType("acceptInsuranceNonMotor");
        service.acceptInsurance(request);
        assertEquals("acceptInsuranceOther", request.getWorkorderFileList().get(0).getType());
        verify(aggregate).replacePolicies(any());
    }

    /**

     * 外企业文件查不到时拒绝全部写入；另一工单已有引用也必须拒绝。

     */
    @Test
    void rejectsForeignOrAlreadyUsedFilesBeforeAnyWrite() {
        WorkorderDTO request = WorkorderPolicyValidatorTest.request("COMMERCIAL");
        when(files.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.acceptInsurance(request));
        verify(aggregate, never()).replacePolicies(any());
        when(files.selectOne(any())).thenReturn(new SystemFile());
        when(relations.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.acceptInsurance(request));
        verify(aggregate, never()).replaceFinancials(any());
    }

    /**

     * 未接单或不存在的工单不能更新；轻量入口不能绕过完整资料校验直接完成。

     */
    @Test
    void rejectsInvalidStagesAndCompletionBypass() {
        WorkorderDTO request = WorkorderPolicyValidatorTest.request("COMMERCIAL");
        current.setStatus(1);
        assertThrows(BusinessException.class, () -> service.acceptInsurance(request));
        request.setStatus(10);
        assertThrows(BusinessException.class, () -> service.updateNoCascade(request));
        when(aggregate.lockWorkorder(101L, 9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.updateQuotation(request));
        verify(aggregate, never()).updateMainStage(any());
    }

    /**
     * 所有已接单流程阶段均可直接承保，不依赖报价单或既有金额记录。
     * 承保成功补建金额行并置为完成状态；首次完成生成时间，不要求用户先推进报价流程。
     */
    @Test
    void acceptsDirectUnderwritingWithoutQuotationFromEveryActiveStage() {
        for (int status = 2; status <= 9; status++) {
            clearInvocations(aggregate);
            current.setStatus(status);
            current.setFinishTime(null);
            WorkorderDTO request = WorkorderPolicyValidatorTest.request("COMMERCIAL");
            request.setWorkorderFileList(List.of());
            assertEquals(200, service.acceptInsurance(request).getCode());
            ArgumentCaptor<Workorder> capture = ArgumentCaptor.forClass(Workorder.class);
            verify(aggregate).updateMainStage(capture.capture());
            assertEquals(10, capture.getValue().getStatus());
            assertNotNull(capture.getValue().getFinishTime());
            verify(aggregate).insertQuote(any());
            verify(aggregate).replacePolicies(any());
            verify(aggregate).replaceFinancials(any());
        }
    }
}

