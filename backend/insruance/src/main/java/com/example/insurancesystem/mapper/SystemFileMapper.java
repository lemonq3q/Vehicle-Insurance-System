package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.SystemFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SystemFileMapper extends BatchBaseMapper<SystemFile> {
}
