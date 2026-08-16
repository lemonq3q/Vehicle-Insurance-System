package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.user.MerchantUserDTO;
import com.example.insurancesystem.domain.user.MerchantUserSearchDTO;
import com.example.insurancesystem.domain.user.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
/**
 * 访问系统用户主体，并聚合企业成员、角色和商户关系以支持认证及人员管理。
 */
public interface UserMapper extends BatchBaseMapper<User> {
    /**
     * 按登录账号读取认证所需的有效用户及企业成员状态。
     */
    User selectLoginUser(String username);

    /**
     * 校验 SaaS 单点登录用户是否属于指定企业，并返回车险系统登录资料。
     */
    User selectSsoUser(@Param("userId") Long userId, @Param("enterpriseId") Long enterpriseId);

    /**
     * 查询企业内全部未删除成员的用户主键，供套餐暂停时批量失效车险 Redis 会话。
     */
    List<Long> selectUserIdsByEnterprise(Long enterpriseId);

    /**
     * 按人员筛选条件查询兼容旧页面的商户用户聚合模型。
     */
    List<MerchantUserDTO> selectByMerchantUserSearchDTO(MerchantUserSearchDTO merchantUserSearchDTO);

    /**
     * 查询单个用户及其企业或商户角色详情。
     */
    MerchantUserDTO selectMerchantUserDTOById(Long id);

    /**
     * 查询指定商户和历史角色下的用户主键，用于兼容旧角色关系。
     */
    Long selectUserByRoleAndMerchantId(Long roleId, Long merchantId);

    /**
     * 查询商户中可承担收款职责的用户。
     */
    List<User> selectPayeeByMerchantId(Long merchantId);

    /**
     * 分页条件查询当前企业已纳入管理的系统账号。
     */
    List<MerchantUserDTO> selectSystemUserBySearchDTO(MerchantUserSearchDTO searchDTO);

    /**
     * 查询当前企业尚未审核通过的成员申请。
     */
    List<MerchantUserDTO> selectNotApprovalUser(MerchantUserSearchDTO searchDTO);

    /**
     * 按关键字查询可供选择的企业系统账号。
     */
    List<User> selectSystemUserOptions(String blurParam);

    /**
     * 查询商户全部有效人员，供工单自动选择收款人时按角色优先级判断。
     */
    List<User> selectUserByMerchantId(Long merchantId);
}
