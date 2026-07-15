package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.*;

import java.util.List;

public interface DownstreamService {
    ResponseResult select(DownstreamSearchDTO params);

    List<DownstreamExcelDTO> getExcel(DownstreamSearchDTO params);

    ResponseResult insert(DownstreamDTO params);

    ResponseResult update(DownstreamDTO params);

    ResponseResult delete(Long id);

    ResponseResult selectById(Long id);

    ResponseResult selectOptions(String blurParam);
}
