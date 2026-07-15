package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.MerchantUserDTO;
import com.example.insurancesystem.domain.user.MerchantUserSearchDTO;

public interface MerchantStaffService {
    ResponseResult select(MerchantUserSearchDTO params);
    ResponseResult selectById(Long id);
    ResponseResult selectByMerchantId(Long merchantId);
    ResponseResult insert(MerchantUserDTO params);
    ResponseResult update(MerchantUserDTO params);
    ResponseResult delete(Long id);
    ResponseResult deleteByMerchantId(Long merchantId);
}
