package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.workorder.WorkorderDTO;
import com.example.insurancesystem.domain.workorder.WorkorderExcelDTO;
import com.example.insurancesystem.domain.workorder.WorkorderSearchDTO;
import com.example.insurancesystem.service.WorkorderService;
import com.example.insurancesystem.utils.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/workorder")
public class WorkorderController {

    @Autowired
    private WorkorderService workorderService;

    @GetMapping
    @PreAuthorize("hasAuthority('workorder:select')")
    public ResponseResult select(WorkorderSearchDTO params) {
        return workorderService.select(params);
    }

    @GetMapping("/renew")
    @PreAuthorize("hasAuthority('workorder:select')")
    public ResponseResult selectRenew(WorkorderSearchDTO params) {
        return workorderService.selectRenew(params);
    }

    @GetMapping("/renew/count")
    @PreAuthorize("hasAuthority('workorder:select')")
    public ResponseResult selectRenewCount() {
        return workorderService.selectRenewCount();
    }

    @PutMapping("/renew/{id}/disable-reminder")
    @PreAuthorize("hasAuthority('workorder:update')")
    public ResponseResult disableRenewReminder(@PathVariable("id") Long id) {
        return workorderService.disableRenewReminder(id);
    }

    @GetMapping("/excel")
    @PreAuthorize("hasAuthority('workorder:select')")
    public void getExcel(WorkorderSearchDTO params, HttpServletResponse response){
        List<WorkorderExcelDTO> excelDTOList = workorderService.getExcel(params);
        WebUtils.renderExcel(response, excelDTOList, WorkorderExcelDTO.class);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workorder:select')")
    public ResponseResult selectById(@PathVariable("id") Long id) {
        return workorderService.selectById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('workorder:update')")
    public ResponseResult insert(@RequestBody WorkorderDTO params) {
        return workorderService.insert(params);
    }

    @PutMapping("/accept")
    @PreAuthorize("hasAuthority('workorder:update')")
    public ResponseResult acceptWorkorder(@RequestBody WorkorderDTO params){
        return workorderService.acceptWorkorder(params);
    }

    @PutMapping("/baseInfo")
    @PreAuthorize("hasAuthority('workorder:update')")
    public ResponseResult updateBaseInfo(@RequestBody WorkorderDTO params) {
        return workorderService.updateBaseInfo(params);
    }

    @PutMapping("/quotation")
    @PreAuthorize("hasAuthority('workorder:update')")
    public ResponseResult updateHandleInfo(@RequestBody WorkorderDTO params) {
        return workorderService.updateQuotation(params);
    }

    @PutMapping("/noCascade")
    @PreAuthorize("hasAuthority('workorder:update')")
    public ResponseResult updateFailedInfo(@RequestBody WorkorderDTO params) {
        return workorderService.updateNoCascade(params);
    }

    @PutMapping("/acceptInsurance")
    @PreAuthorize("hasAuthority('workorder:update')")
    public ResponseResult acceptInsurance(@RequestBody WorkorderDTO params) {
        return workorderService.acceptInsurance(params);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('workorder:update')")
    public ResponseResult delete(Long id) {
        return workorderService.delete(id);
    }
}
