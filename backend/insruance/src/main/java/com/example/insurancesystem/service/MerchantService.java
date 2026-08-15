package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;

/**
 * 提供上下游商户共用的服务区域和区域承保公司查询。
 */
public interface MerchantService {
    /**
     * 查询指定商户配置的行政区域。
     */
    ResponseResult selectAreaByMerchantId(Long id);

    /**
     * 查询覆盖指定行政区划的保险公司。
     */
    ResponseResult selectInsuranceCompanyByArea(String areaCode);
}
