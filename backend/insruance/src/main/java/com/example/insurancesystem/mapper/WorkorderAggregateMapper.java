package com.example.insurancesystem.mapper;

import com.example.insurancesystem.domain.workorder.Workorder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkorderAggregateMapper {
    int updateQuote(Workorder workorder);
    int insertQuote(Workorder workorder);
    int updateCommission(@Param("workorder") Workorder workorder, @Param("side") String side);
    int insertCommission(@Param("workorder") Workorder workorder, @Param("side") String side);
    int updatePayment(Workorder workorder);
    int insertPayment(Workorder workorder);
    int updateUnderwriting(Workorder workorder);
    int insertUnderwriting(Workorder workorder);
    int updateLogistics(Workorder workorder);
    int insertLogistics(Workorder workorder);
}
