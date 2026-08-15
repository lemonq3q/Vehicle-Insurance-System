package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.PageTest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 为分页基础能力测试提供示例数据访问入口。
 */
public interface PageTestMapper extends BatchBaseMapper<PageTest> {
}
