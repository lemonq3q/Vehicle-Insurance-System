package com.example.insurancesystem.mapper;

import com.example.insurancesystem.domain.workorder.Workorder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
/**
 * 维护从工单主表拆分出的报价、佣金、缴费、核保和物流阶段数据。
 * 每类方法成对提供更新与插入，以兼容迁移前尚未生成聚合记录的历史工单。
 */
public interface WorkorderAggregateMapper {
    /**
     * 更新工单报价阶段记录。
     */
    int updateQuote(Workorder workorder);
    /**
     * 为旧工单补建报价阶段记录。
     */
    int insertQuote(Workorder workorder);
    /**
     * 更新指定上下游方向的佣金记录。
     */
    int updateCommission(@Param("workorder") Workorder workorder, @Param("side") String side);
    /**
     * 为指定上下游方向补建佣金记录。
     */
    int insertCommission(@Param("workorder") Workorder workorder, @Param("side") String side);
    /**
     * 更新工单缴费阶段记录。
     */
    int updatePayment(Workorder workorder);
    /**
     * 为旧工单补建缴费阶段记录。
     */
    int insertPayment(Workorder workorder);
    /**
     * 更新工单核保与承保阶段记录。
     */
    int updateUnderwriting(Workorder workorder);
    /**
     * 为旧工单补建核保与承保阶段记录。
     */
    int insertUnderwriting(Workorder workorder);
    /**
     * 更新保单寄送物流记录。
     */
    int updateLogistics(Workorder workorder);
    /**
     * 为旧工单补建保单寄送物流记录。
     */
    int insertLogistics(Workorder workorder);
}
