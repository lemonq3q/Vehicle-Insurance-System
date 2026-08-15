package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.UpstreamDTO;
import com.example.insurancesystem.domain.merchant.UpstreamExcelDTO;
import com.example.insurancesystem.domain.merchant.UpstreamSearchDTO;

import java.util.List;

/**
 * 定义上游渠道与保险公司机构的查询、导出、维护和精简选项能力。
 */
public interface UpstreamService {
    /**
     * 分页查询当前企业上游机构。
     */
    ResponseResult select(UpstreamSearchDTO params);

    /**
     * 生成符合筛选条件的上游 Excel 数据。
     */
    List<UpstreamExcelDTO> getExcel(UpstreamSearchDTO params);

    /**
     * 新建上游机构聚合资料。
     */
    ResponseResult insert(UpstreamDTO params);

    /**
     * 更新上游机构聚合资料。
     */
    ResponseResult update(UpstreamDTO params);

    /**
     * 删除上游机构及关联数据。
     */
    ResponseResult delete(long id);

    /**
     * 查询上游机构详情。
     */
    ResponseResult selectById(Long id);

    /**
     * 返回保险公司类别的上游选项。
     */
    ResponseResult selectInsuranceCompanyOptions(String blurParam);

    /**
     * 返回全部上游机构精简选项。
     */
    ResponseResult selectOptions(String blurParam);
}
