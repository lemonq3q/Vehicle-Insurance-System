package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.merchant.MerchantStaffRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantStaffRoleMapper extends BatchBaseMapper<MerchantStaffRole> {
}
