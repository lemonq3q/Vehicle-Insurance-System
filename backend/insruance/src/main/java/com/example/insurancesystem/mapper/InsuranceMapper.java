package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.Insurance;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 访问保险产品字典，支持通用单条和批量持久化操作。
 */
public interface InsuranceMapper extends BatchBaseMapper<Insurance> {
}
