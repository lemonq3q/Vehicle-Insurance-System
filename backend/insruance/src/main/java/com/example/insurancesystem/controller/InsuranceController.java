package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.service.InsuranceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/insurance")
/**
 * 提供企业当前可用保险产品字典，供工单录入和险种选择页面加载。
 */
public class InsuranceController {

    @Autowired
    private InsuranceService insuranceService;

    @GetMapping("/all")
    /**
     * 查询全部可用保险产品，具体租户范围和状态过滤由 InsuranceService 处理。
     */
    public ResponseResult selectAll(){
        return insuranceService.selectAll();
    }
}
