package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.workorder.WorkorderFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkorderFileMapper extends BatchBaseMapper<WorkorderFile> {

    List<WorkorderFile> selectByWorkorderId(Long id);

    int batchUpdateByWorkorderId(@Param("list") List<WorkorderFile> list, @Param("workorderId") Long workorderId, @Param("updateBy") Long updateBy);
}
