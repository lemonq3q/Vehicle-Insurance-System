package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.*;
import com.example.insurancesystem.service.DownstreamService;
import com.example.insurancesystem.utils.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/downstream")
public class DownstreamController {

    @Autowired
    DownstreamService downstreamService;

    @GetMapping
    @PreAuthorize("hasAuthority('merchant:select')")
    public ResponseResult select(DownstreamSearchDTO params) {
        return downstreamService.select(params);
    }

    @GetMapping("/excel")
    @PreAuthorize("hasAuthority('merchant:select')")
    public void getExcel(DownstreamSearchDTO params, HttpServletResponse response){
        List<DownstreamExcelDTO> excelDTOList = downstreamService.getExcel(params);
        WebUtils.renderExcel(response, excelDTOList, DownstreamExcelDTO.class);
    }

    @GetMapping("/option")
    @PreAuthorize("hasAuthority('merchant:select')")
    public ResponseResult selectOptions(String blurParam){
        return downstreamService.selectOptions(blurParam);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('merchant:select')")
    public ResponseResult selectById(@PathVariable("id") Long id) {
        return downstreamService.selectById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('merchant:update')")
    public ResponseResult insert(@RequestBody DownstreamDTO params) {
        return downstreamService.insert(params);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('merchant:update')")
    public ResponseResult update(@RequestBody DownstreamDTO params) {
        return downstreamService.update(params);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('merchant:update')")
    public ResponseResult delete(Long id) {
        return downstreamService.delete(id);
    }
}
