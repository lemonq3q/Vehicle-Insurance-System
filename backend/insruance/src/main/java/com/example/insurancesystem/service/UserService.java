package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.MerchantUserDTO;
import com.example.insurancesystem.domain.user.MerchantUserExcelDTO;
import com.example.insurancesystem.domain.user.MerchantUserSearchDTO;
import com.example.insurancesystem.domain.user.User;

import java.util.List;

/**
 * 定义车险账号、企业成员、系统角色和商户用户关系的查询与维护能力。
 */
public interface UserService {

    /**
     * 分页查询商户业务用户。
     */
    ResponseResult select(MerchantUserSearchDTO params);

    /**
     * 按邮箱查询用户，供注册和找回密码校验。
     */
    ResponseResult<User> selectByEmail(String email);

    /**
     * 按筛选条件生成用户 Excel 行数据。
     */
    List<MerchantUserExcelDTO> getExcel(MerchantUserSearchDTO params);

    /**
     * 创建用户及系统角色、企业成员或商户关联。
     */
    ResponseResult insert(MerchantUserDTO params);

    /**
     * 更新用户及关联角色资料。
     */
    ResponseResult update(MerchantUserDTO params);

    /**
     * 删除用户及允许清理的关联关系。
     */
    ResponseResult delete(Long id);

    /**
     * 删除指定商户下的用户关系，供机构删除复用。
     */
    ResponseResult deleteByMerchantId(Long merchantId);

    /**
     * 查询用户聚合详情。
     */
    ResponseResult selectById(Long id);

    /**
     * 查询指定商户下用户精简选项。
     */
    ResponseResult selectUserOptionsByMerchantId(Long merchantId);

    /**
     * 按关键字查询普通用户选项。
     */
    ResponseResult selectUserOptions(String blurParam);

    /**
     * 校验当前密码后修改登录密码。
     */
    ResponseResult updatePassword(User params);

    /**
     * 由找回密码流程按邮箱直接设置新密码哈希。
     */
    ResponseResult updatePasswordByEmail(String phone, String password);

    /**
     * 分页查询系统管理用户。
     */
    ResponseResult selectSystemUser(MerchantUserSearchDTO params);

    /**
     * 分页查询待审核注册用户。
     */
    ResponseResult selectNotApprovalUser(MerchantUserSearchDTO params);

    /**
     * 注册个人账号并建立初始企业成员状态。
     */
    ResponseResult registerPersonal(User user);

    /**
     * 审批并启用待审核用户。
     */
    ResponseResult approvalUser(Long id);

    /**
     * 按关键字查询系统用户精简选项。
     */
    ResponseResult selectSystemUserOptions(String blurParam);
}
