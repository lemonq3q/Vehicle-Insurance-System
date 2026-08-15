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
/**
 * 车险工单全流程入口，覆盖列表、续保、导出、建档、接单、报价、核保支付承保更新及删除。
 */
public class WorkorderController {

    @Autowired
    private WorkorderService workorderService;

    @GetMapping
    @PreAuthorize("hasAuthority('workorder:select')")
    /**
     * 按工单号、客户、机构、状态和时间范围分页查询当前企业工单。
     */
    public ResponseResult select(WorkorderSearchDTO params) {
        return workorderService.select(params);
    }

    @GetMapping("/renew")
    @PreAuthorize("hasAuthority('workorder:select')")
    /**
     * 查询进入续保提醒窗口且未关闭提醒的历史工单。
     */
    public ResponseResult selectRenew(WorkorderSearchDTO params) {
        return workorderService.selectRenew(params);
    }

    @GetMapping("/renew/count")
    @PreAuthorize("hasAuthority('workorder:select')")
    /**
     * 统计当前用户需要关注的续保工单数量，供菜单角标展示。
     */
    public ResponseResult selectRenewCount() {
        return workorderService.selectRenewCount();
    }

    @PutMapping("/renew/{id}/disable-reminder")
    @PreAuthorize("hasAuthority('workorder:update')")
    /**
     * 关闭指定工单当前续保提醒，避免已确认不跟进的客户持续出现在提醒列表。
     */
    public ResponseResult disableRenewReminder(@PathVariable("id") Long id) {
        return workorderService.disableRenewReminder(id);
    }

    @GetMapping("/excel")
    @PreAuthorize("hasAuthority('workorder:select')")
    /**
     * 按列表筛选条件导出工单 Excel，派生状态和金额由服务层计算。
     */
    public void getExcel(WorkorderSearchDTO params, HttpServletResponse response){
        List<WorkorderExcelDTO> excelDTOList = workorderService.getExcel(params);
        WebUtils.renderExcel(response, excelDTOList, WorkorderExcelDTO.class);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workorder:select')")
    /**
     * 查询工单主表以及车辆证件、文件、险种和各流程阶段的聚合详情。
     */
    public ResponseResult selectById(@PathVariable("id") Long id) {
        return workorderService.selectById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('workorder:update')")
    /**
     * 创建新工单及其全部关联资料，服务层在事务内生成企业内唯一工单号。
     */
    public ResponseResult insert(@RequestBody WorkorderDTO params) {
        return workorderService.insert(params);
    }

    @PutMapping("/accept")
    @PreAuthorize("hasAuthority('workorder:update')")
    /**
     * 当前处理人接收待处理工单并推进流程状态。
     */
    public ResponseResult acceptWorkorder(@RequestBody WorkorderDTO params){
        return workorderService.acceptWorkorder(params);
    }

    @PutMapping("/baseInfo")
    @PreAuthorize("hasAuthority('workorder:update')")
    /**
     * 更新工单客户、车辆、证件、险种和附件等基础聚合资料。
     */
    public ResponseResult updateBaseInfo(@RequestBody WorkorderDTO params) {
        return workorderService.updateBaseInfo(params);
    }

    @PutMapping("/quotation")
    @PreAuthorize("hasAuthority('workorder:update')")
    /**
     * 更新报价和渠道佣金信息，并按当前阶段推进后续工单状态。
     */
    public ResponseResult updateHandleInfo(@RequestBody WorkorderDTO params) {
        return workorderService.updateQuotation(params);
    }

    @PutMapping("/noCascade")
    @PreAuthorize("hasAuthority('workorder:update')")
    /**
     * 更新失败原因或无需级联保存关联表的阶段字段，避免覆盖已有聚合资料。
     */
    public ResponseResult updateFailedInfo(@RequestBody WorkorderDTO params) {
        return workorderService.updateNoCascade(params);
    }

    @PutMapping("/acceptInsurance")
    @PreAuthorize("hasAuthority('workorder:update')")
    /**
     * 保存承保结果、保单号及物流信息并完成或推进工单。
     */
    public ResponseResult acceptInsurance(@RequestBody WorkorderDTO params) {
        return workorderService.acceptInsurance(params);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('workorder:update')")
    /**
     * 删除指定工单，服务层负责企业范围校验和关联数据清理。
     */
    public ResponseResult delete(Long id) {
        return workorderService.delete(id);
    }
}
