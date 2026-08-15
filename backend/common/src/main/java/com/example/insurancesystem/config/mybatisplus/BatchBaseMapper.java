package com.example.insurancesystem.config.mybatisplus;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 在 MyBatis-Plus 标准 BaseMapper 上补充通用批量插入能力，供归档和批量导入等场景减少逐行 SQL 往返。
 * 实际 SQL 由 InsertBatchSqlInjector 注入，所有继承该接口的实体 Mapper 共用相同方法契约。
 */
public interface BatchBaseMapper<T> extends BaseMapper<T> {

    /**
     * 将实体集合拼接为单条批量 INSERT 并返回受影响行数；调用方应控制批次大小，避免 SQL 过大。
     */
    int insertBatchSomeColumn(@Param("list") List<T> batchList);

}
