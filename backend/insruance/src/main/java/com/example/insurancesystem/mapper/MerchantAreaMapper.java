package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.merchant.MerchantArea;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 维护商户与可经营或承保行政区域之间的关系。
 */
public interface MerchantAreaMapper extends BatchBaseMapper<MerchantArea> {
}
