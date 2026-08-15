package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.Insurance;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.mapper.InsuranceMapper;
import com.example.insurancesystem.service.InsuranceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
/**
 * 提供保险产品基础数据的查询能力。
 * 当前实现直接读取全部保险产品，供工单录入、报价等页面构建产品选项。
 */
public class InsuranceServiceImpl implements InsuranceService {

    @Autowired
    private InsuranceMapper insuranceMapper;

    @Override
    /**
     * 查询系统中维护的全部保险产品，不附加企业或区域过滤条件。
     *
     * @return 包含保险产品列表的统一响应
     */
    public ResponseResult<List<Insurance>> selectAll() {
        List<Insurance> insuranceList = insuranceMapper.selectList(null);
        return new ResponseResult(200, insuranceList);
    }
}
