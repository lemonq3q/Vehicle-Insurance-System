package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.merchant.MerchantStaff;
import com.example.insurancesystem.domain.user.MerchantUserDTO;
import com.example.insurancesystem.domain.user.MerchantUserSearchDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MerchantStaffMapper extends BatchBaseMapper<MerchantStaff> {
    List<MerchantUserDTO> selectStaff(MerchantUserSearchDTO params);
    MerchantUserDTO selectStaffById(Long id);
    List<MerchantUserDTO> selectOptionsByMerchantId(Long merchantId);
    Long countRole(Long merchantId, String roleCode, Long excludeStaffId);
}
