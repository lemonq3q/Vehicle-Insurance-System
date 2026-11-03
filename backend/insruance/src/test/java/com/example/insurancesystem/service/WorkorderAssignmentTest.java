package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.domain.workorder.WorkorderDTO;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.mapper.UserMapper;
import com.example.insurancesystem.mapper.WorkorderMapper;
import com.example.insurancesystem.service.impl.WorkorderServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 接单和重新分配的租户边界回归，不连接数据库。
 * 验证客户端即使绕过下拉框提交外企业或停用账号，也不能触发工单更新。
 */
class WorkorderAssignmentTest {
    private final UserMapper users = mock(UserMapper.class);
    private final WorkorderMapper orders = mock(WorkorderMapper.class);
    private final WorkorderServiceImpl service = new WorkorderServiceImpl();

    /** 建立企业9的真实线程身份并注入只读成员匹配与工单写入替身。 */
    @BeforeEach
    void authenticate() {
        User operator = new User(); operator.setId(7L); operator.setEnterpriseId(9L);
        LoginUser login = new LoginUser(operator, List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, null, List.of()));
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "workorderMapper", orders);
    }

    /** 归还线程身份，避免后续测试沿用本企业上下文。 */
    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    /** 本企业查询找不到目标账号时拒绝，不回退到全局账号或商户人员。 */
    @Test
    void rejectsUserOutsideCurrentEnterprise() {
        WorkorderDTO request = new WorkorderDTO(); request.setId(21L); request.setHandleBy(8L);
        assertThrows(BusinessException.class, () -> service.acceptWorkorder(request));
        verify(users).selectSsoUser(8L, 9L);
        verifyNoInteractions(orders);
    }

    /** 有成员关系但账号停用时同样不能接收新分配，历史记录不在此处改写。 */
    @Test
    void rejectsInactiveSystemMember() {
        User inactive = new User(); inactive.setStatus(0); inactive.setMemberStatus(1);
        when(users.selectSsoUser(8L, 9L)).thenReturn(inactive);
        WorkorderDTO request = new WorkorderDTO(); request.setId(21L); request.setHandleBy(8L);
        assertThrows(BusinessException.class, () -> service.acceptWorkorder(request));
        verifyNoInteractions(orders);
    }
}
