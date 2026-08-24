package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.merchant.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/**
 * 访问统一商户主体，并提供上游保险机构、下游渠道和商户类别字典的聚合查询。
 */
public interface MerchantMapper extends BatchBaseMapper<Merchant> {
    /**
     * 批量写入商户并将数据库自增主键回填到各实体。调用方必须等本方法返回后再组装业务区域和人员关系，
     * 从而让所有关联使用数据库实际分配的商户 ID。
     */
    int insertImportBatch(@Param("list") List<Merchant> merchants);

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

    /**
     * 批量查询当前企业已存在的有效商户名称，供导入前排除数据库冲突行。
     */
    List<String> selectExistingNames(@Param("enterpriseId") Long enterpriseId,
                                     @Param("names") List<String> names);
}
