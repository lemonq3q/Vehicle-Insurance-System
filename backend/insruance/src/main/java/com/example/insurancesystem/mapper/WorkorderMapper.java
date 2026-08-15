package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.workorder.Workorder;
import com.example.insurancesystem.domain.workorder.WorkorderDTO;
import com.example.insurancesystem.domain.workorder.WorkorderSearchDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/**
 * 访问工单主体，并提供权限过滤、续保提醒和聚合展示所需的自定义查询。
 */
public interface WorkorderMapper extends BatchBaseMapper<Workorder> {
    /**
     * 按工单筛选条件查询包含商户、车辆和阶段信息的展示模型。
     */
    List<WorkorderDTO> selectByWorkorderSearchDTO(WorkorderSearchDTO params);

    /**
     * 查询进入指定续保提醒窗口且未关闭提醒的工单。
     */
    List<WorkorderDTO> selectRenewByWorkorderSearchDTO(WorkorderSearchDTO params);

    /**
     * 统计提醒窗口内的续保工单，可按创建人限定个人范围。
     */
    Integer selectRenewCount(@Param("renewalRemindDays") Integer renewalRemindDays,
                             @Param("createBy") Long createBy);

    /**
     * 在用户处理续保提醒状态后推进该工单的提醒周期标记。
     */
    int updateRenewalStatusCycle(@Param("id") Long id);

    /**
     * 重置已经跨过有效周期的临时续保状态，使其可进入下一周期计算。
     */
    int resetExpiredRenewalStatuses();
}
