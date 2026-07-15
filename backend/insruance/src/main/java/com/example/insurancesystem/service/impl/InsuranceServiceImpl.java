package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.Insurance;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.mapper.InsuranceMapper;
import com.example.insurancesystem.service.InsuranceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InsuranceServiceImpl implements InsuranceService {

    @Autowired
    private InsuranceMapper insuranceMapper;

    @Override
    public ResponseResult<List<Insurance>> selectAll() {
        List<Insurance> insuranceList = insuranceMapper.selectList(null);
        return new ResponseResult(200, insuranceList);
    }
}
