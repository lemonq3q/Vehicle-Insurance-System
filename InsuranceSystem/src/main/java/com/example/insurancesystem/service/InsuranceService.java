package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.Insurance;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;

import java.util.List;

public interface InsuranceService {
    ResponseResult<List<Insurance>> selectAll();
}
