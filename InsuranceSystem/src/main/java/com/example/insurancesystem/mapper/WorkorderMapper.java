package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.workorder.Workorder;
import com.example.insurancesystem.domain.workorder.WorkorderDTO;
import com.example.insurancesystem.domain.workorder.WorkorderSearchDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkorderMapper extends BatchBaseMapper<Workorder> {
    List<WorkorderDTO> selectByWorkorderSearchDTO(WorkorderSearchDTO params);

    List<WorkorderDTO> selectRenewByWorkorderSearchDTO(WorkorderSearchDTO params);
}
