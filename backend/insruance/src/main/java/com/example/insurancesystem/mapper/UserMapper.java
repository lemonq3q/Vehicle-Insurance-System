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

    /** 按已验证微信用户主键加载同等企业、成员与订阅聚合数据，支持无邮箱账号。 */
    User selectWechatUser(Long userId);

    /**
     * 校验 SaaS 单点登录用户是否属于指定企业，并返回车险系统登录资料。
     */
    User selectSsoUser(@Param("userId") Long userId, @Param("enterpriseId") Long enterpriseId);

    /**
     * 查询企业内全部未删除成员的用户主键，供套餐暂停时批量失效车险 Redis 会话。
     */
    List<Long> selectUserIdsByEnterprise(Long enterpriseId);


    /**
     * 查询单个用户及其企业或商户角色详情。
     */
    MerchantUserDTO selectMerchantUserDTOById(Long id);



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
    List<User> selectSystemUserOptions(@Param("blurParam") String blurParam, @Param("enterpriseId") Long enterpriseId);

    /**
     * 仅在当前企业有效成员中匹配所有非空身份条件，多人满足时按用户ID升序取首个。
     * 参数由工单导入提供，邮箱使用登录邮箱，不以用户名或手机号替代。
     */
    Long selectImportHandler(@Param("enterpriseId") Long enterpriseId, @Param("name") String name,
                             @Param("email") String email, @Param("idNum") String idNum);

}
