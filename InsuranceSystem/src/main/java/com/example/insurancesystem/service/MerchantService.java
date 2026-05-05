package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;

public interface MerchantService {
    ResponseResult selectAreaByMerchantId(Long id);

    ResponseResult selectInsuranceCompanyByArea(String areaCode);
}
