package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.workorder.WorkorderDTO;
import com.example.insurancesystem.domain.workorder.WorkorderExcelDTO;
import com.example.insurancesystem.domain.workorder.WorkorderSearchDTO;

import java.util.List;

public interface WorkorderService {
    ResponseResult select(WorkorderSearchDTO params);

    List<WorkorderExcelDTO> getExcel(WorkorderSearchDTO params);

    ResponseResult selectById(Long id);

    ResponseResult insert(WorkorderDTO params);

    ResponseResult updateBaseInfo(WorkorderDTO params);

    ResponseResult delete(Long id);

    ResponseResult acceptWorkorder(WorkorderDTO params);

    ResponseResult updateQuotation(WorkorderDTO params);

    ResponseResult updateNoCascade(WorkorderDTO params);

    ResponseResult acceptInsurance(WorkorderDTO params);

    ResponseResult selectRenew(WorkorderSearchDTO params);

    ResponseResult selectRenewCount();
}
