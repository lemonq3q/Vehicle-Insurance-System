package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.Insurance;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;

import java.util.List;

/**
 * 提供当前企业可选保险产品字典。
 */
public interface InsuranceService {
    /**
     * 查询全部启用保险产品并返回稳定列表。
     */
    ResponseResult<List<Insurance>> selectAll();
}
