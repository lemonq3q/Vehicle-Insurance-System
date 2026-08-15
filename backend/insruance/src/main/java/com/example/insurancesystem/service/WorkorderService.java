package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.workorder.WorkorderDTO;
import com.example.insurancesystem.domain.workorder.WorkorderExcelDTO;
import com.example.insurancesystem.domain.workorder.WorkorderSearchDTO;

import java.util.List;

/**
 * 定义车险工单聚合的查询、建档、阶段流转、续保提醒、导出和删除能力。
 */
public interface WorkorderService {
    /**
     * 分页查询当前企业工单并计算列表派生字段。
     */
    ResponseResult select(WorkorderSearchDTO params);

    /**
     * 生成符合筛选条件的工单 Excel 数据。
     */
    List<WorkorderExcelDTO> getExcel(WorkorderSearchDTO params);

    /**
     * 查询单个工单及所有关联对象。
     */
    ResponseResult selectById(Long id);

    /**
     * 在事务内创建工单主表、证件、文件、险种和流程关联数据。
     */
    ResponseResult insert(WorkorderDTO params);

    /**
     * 更新工单基础聚合资料和附件关联。
     */
    ResponseResult updateBaseInfo(WorkorderDTO params);

    /**
     * 删除工单聚合。
     */
    ResponseResult delete(Long id);

    /**
     * 接收待处理工单并设置处理人及下一状态。
     */
    ResponseResult acceptWorkorder(WorkorderDTO params);

    /**
     * 保存报价与渠道佣金并推进流程。
     */
    ResponseResult updateQuotation(WorkorderDTO params);

    /**
     * 更新无需级联保存关联对象的工单阶段字段。
     */
    ResponseResult updateNoCascade(WorkorderDTO params);

    /**
     * 保存承保、保单和物流结果并推进或完成工单。
     */
    ResponseResult acceptInsurance(WorkorderDTO params);

    /**
     * 分页查询进入当前续保提醒窗口的工单。
     */
    ResponseResult selectRenew(WorkorderSearchDTO params);

    /**
     * 统计待处理续保提醒数量。
     */
    ResponseResult selectRenewCount();

    /**
     * 关闭指定工单当前续保提醒。
     */
    ResponseResult disableRenewReminder(Long id);
}
