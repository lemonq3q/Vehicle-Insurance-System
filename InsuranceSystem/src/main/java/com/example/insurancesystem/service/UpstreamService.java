package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.UpstreamDTO;
import com.example.insurancesystem.domain.merchant.UpstreamExcelDTO;
import com.example.insurancesystem.domain.merchant.UpstreamSearchDTO;

import java.util.List;

public interface UpstreamService {
    ResponseResult select(UpstreamSearchDTO params);

    List<UpstreamExcelDTO> getExcel(UpstreamSearchDTO params);

    ResponseResult insert(UpstreamDTO params);

    ResponseResult update(UpstreamDTO params);

    ResponseResult delete(long id);

    ResponseResult selectById(Long id);

    ResponseResult selectInsuranceCompanyOptions(String blurParam);

    ResponseResult selectOptions(String blurParam);
}
