package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.UpstreamDTO;
import com.example.insurancesystem.domain.merchant.UpstreamExcelDTO;
import com.example.insurancesystem.domain.merchant.UpstreamSearchDTO;
import com.example.insurancesystem.service.UpstreamService;
import com.example.insurancesystem.utils.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/upstream")
public class UpstreamController {

    @Autowired
    private UpstreamService upstreamService;

    @GetMapping
    @PreAuthorize("hasAuthority('merchant:select')")
    public ResponseResult select(UpstreamSearchDTO params) {
        return upstreamService.select(params);
    }

    @GetMapping("/excel")
    @PreAuthorize("hasAuthority('merchant:select')")
    public void getExcel(UpstreamSearchDTO params, HttpServletResponse response){
        List<UpstreamExcelDTO> excelDTOList = upstreamService.getExcel(params);
        WebUtils.renderExcel(response, excelDTOList, UpstreamExcelDTO.class);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('merchant:select')")
    public ResponseResult selectById(@PathVariable("id") Long id) {
        return upstreamService.selectById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('merchant:update')")
    public ResponseResult insert(@RequestBody UpstreamDTO upstream) {
        return upstreamService.insert(upstream);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('merchant:update')")
    public ResponseResult update(@RequestBody UpstreamDTO upstream) {
        return upstreamService.update(upstream);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('merchant:update')")
    public ResponseResult delete(Long id) {
        return upstreamService.delete(id);
    }

    @GetMapping("/option/insuranceCompany")
    @PreAuthorize("hasAuthority('merchant:select')")
    public ResponseResult selectInsuranceCompanyOptions(String blurParam) {
        return upstreamService.selectInsuranceCompanyOptions(blurParam);
    }

    @GetMapping("/option")
    @PreAuthorize("hasAuthority('merchant:select')")
    public ResponseResult selectOptions(String blurParam) {
        return upstreamService.selectOptions(blurParam);
    }
}
