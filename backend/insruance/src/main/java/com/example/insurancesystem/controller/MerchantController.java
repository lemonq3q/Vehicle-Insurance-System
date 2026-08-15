package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.service.MerchantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant")
/**
 * 提供上下游共用的商户区域与承保机构匹配查询，接口要求商户查看权限。
 */
public class MerchantController {

    @Autowired
    private MerchantService merchantService;

    @GetMapping("/area/byMerchantId")
    @PreAuthorize("hasAuthority('merchant:select')")
    /**
     * 查询指定商户已配置的服务行政区域，供机构详情回显和编辑。
     */
    public ResponseResult selectAreaByMerchantId(Long id){
        return merchantService.selectAreaByMerchantId(id);
    }

    @GetMapping("/insurance/area")
    @PreAuthorize("hasAuthority('merchant:select')")
    /**
     * 根据行政区划代码筛选覆盖该区域的保险公司，为工单选择承保机构提供候选项。
     */
    public ResponseResult selectInsuranceCompanyByArea(String areaCode){
        return merchantService.selectInsuranceCompanyByArea(areaCode);
    }
}
