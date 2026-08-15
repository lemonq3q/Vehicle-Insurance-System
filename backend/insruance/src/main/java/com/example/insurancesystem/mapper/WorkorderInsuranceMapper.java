package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.workorder.WorkorderInsurance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/**
 * 维护工单选择的险种、投保方案及险种级业务状态。
 */
public interface WorkorderInsuranceMapper extends BatchBaseMapper<WorkorderInsurance> {

    /**
     * 根据工单提交的险种快照批量更新、插入或逻辑删除险种关系。
     */
    int batchUpdateByWorkorderId(@Param("list") List<WorkorderInsurance> list, @Param("workorderId") Long workorderId, @Param("updateBy") Long updateBy);
}
