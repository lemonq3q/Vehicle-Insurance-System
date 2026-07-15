package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.workorder.WorkorderInsurance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkorderInsuranceMapper extends BatchBaseMapper<WorkorderInsurance> {

    int batchUpdateByWorkorderId(@Param("list") List<WorkorderInsurance> list, @Param("workorderId") Long workorderId, @Param("updateBy") Long updateBy);
}
