package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.merchant.MerchantStaffRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 维护商户员工的业务角色及默认收款人标记。
 */
public interface MerchantStaffRoleMapper extends BatchBaseMapper<MerchantStaffRole> {
}
