package com.example.insurancesystem.service;

import com.example.insurancesystem.mapper.UserMapper;
import com.example.insurancesystem.security.SingleLoginSessionManager;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 承担车险系统内部会话的管理端失效能力。
 * 服务只删除 Redis 单登录会话，不修改用户、企业或成员数据；套餐暂停使用企业维度批量退出，成员退出或
 * 被移除使用用户维度退出，JWT 随后会在认证过滤器中因缺少匹配会话而返回 401。
 */
@Service
public class InsuranceSessionInvalidationService {
    private final UserMapper userMapper;
    private final SingleLoginSessionManager sessionManager;

    public InsuranceSessionInvalidationService(
            UserMapper userMapper, SingleLoginSessionManager sessionManager) {
        this.userMapper = userMapper;
        this.sessionManager = sessionManager;
    }

    /**
     * 查询企业当前全部成员并逐一删除其会话，返回实际参与处理的用户数量。
     * 删除操作幂等，列表为空时安全返回零；成员状态不作为筛选条件，确保此前已停用但仍残留的会话也被清理。
     */
    public int logoutEnterprise(Long enterpriseId) {
        List<Long> userIds = userMapper.selectUserIdsByEnterprise(enterpriseId);
        userIds.forEach(sessionManager::removeAll);
        return userIds.size();
    }

    /**
     * 无条件删除单个用户当前车险会话，返回固定处理数量供内部调用方记录结果。
     */
    public int logoutUser(Long userId) {
        sessionManager.removeAll(userId);
        return 1;
    }
}
