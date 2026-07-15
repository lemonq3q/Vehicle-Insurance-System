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
public class MerchantController {

    @Autowired
    private MerchantService merchantService;

    @GetMapping("/area/byMerchantId")
    @PreAuthorize("hasAuthority('merchant:select')")
    public ResponseResult selectAreaByMerchantId(Long id){
        return merchantService.selectAreaByMerchantId(id);
    }

    @GetMapping("/insurance/area")
    @PreAuthorize("hasAuthority('merchant:select')")
    public ResponseResult selectInsuranceCompanyByArea(String areaCode){
        return merchantService.selectInsuranceCompanyByArea(areaCode);
    }
}
