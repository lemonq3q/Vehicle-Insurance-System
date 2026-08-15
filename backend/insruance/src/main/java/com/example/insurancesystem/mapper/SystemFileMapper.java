package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.SystemFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 维护 OSS 对象对应的企业文件元数据及其业务关联状态。
 */
public interface SystemFileMapper extends BatchBaseMapper<SystemFile> {
}
