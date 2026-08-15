package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.merchant.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
/**
 * 访问统一商户主体，并提供上游保险机构、下游渠道和商户类别字典的聚合查询。
 */
public interface MerchantMapper extends BatchBaseMapper<Merchant> {
    /**
     * 按保险机构筛选条件查询上游商户展示模型。
     */
    List<UpstreamDTO> selectByUpstreamSearchDTO(UpstreamSearchDTO params);

    /**
     * 按渠道类型、名称等条件查询下游商户展示模型。
     */
    List<DownstreamDTO> selectByDownstreamSearchDTO(DownstreamSearchDTO params);

    /**
     * 查询在指定行政区域配置了承保范围的有效保险机构。
     */
    List<Merchant> selectInsuranceCompanyByAreaCode(String areaCode);

    /**
     * 将稳定的商户类别编码解析为数据库字典主键。
     */
    Long selectCategoryIdByCode(String code);
}
