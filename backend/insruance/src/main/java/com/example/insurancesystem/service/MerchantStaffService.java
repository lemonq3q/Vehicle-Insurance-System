package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.MerchantUserDTO;
import com.example.insurancesystem.domain.user.MerchantUserSearchDTO;

/**
 * 管理合作商户员工关系和机构内业务角色，并维护联系人、店员与收款人约束。
 */
public interface MerchantStaffService {
    /**
     * 按条件分页查询商户员工。
     */
    ResponseResult select(MerchantUserSearchDTO params);
    /**
     * 查询单个员工及角色详情。
     */
    ResponseResult selectById(Long id);
    /**
     * 查询指定商户下全部员工。
     */
    ResponseResult selectByMerchantId(Long merchantId);
    /**
     * 新增员工关系并分配业务角色。
     */
    ResponseResult insert(MerchantUserDTO params);
    /**
     * 更新员工资料与角色关联。
     */
    ResponseResult update(MerchantUserDTO params);
    /**
     * 删除单个商户员工及其角色。
     */
    ResponseResult delete(Long id);
    /**
     * 删除指定商户下全部员工关系，供机构删除事务复用。
     */
    ResponseResult deleteByMerchantId(Long merchantId);
}
