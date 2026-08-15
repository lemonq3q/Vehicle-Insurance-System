package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.*;

import java.util.List;

/**
 * 定义企业下游合作机构的查询、导出、维护及表单选项能力，并负责关联区域和员工数据的一致性。
 */
public interface DownstreamService {
    /**
     * 按当前企业及筛选条件返回下游机构分页。
     */
    ResponseResult select(DownstreamSearchDTO params);

    /**
     * 按同一筛选条件生成下游机构 Excel 行数据。
     */
    List<DownstreamExcelDTO> getExcel(DownstreamSearchDTO params);

    /**
     * 新建下游机构及其区域、员工关联。
     */
    ResponseResult insert(DownstreamDTO params);

    /**
     * 更新下游机构聚合资料。
     */
    ResponseResult update(DownstreamDTO params);

    /**
     * 删除机构及允许级联清理的关联数据。
     */
    ResponseResult delete(Long id);

    /**
     * 查询下游机构聚合详情。
     */
    ResponseResult selectById(Long id);

    /**
     * 按关键字返回下游机构精简选项。
     */
    ResponseResult selectOptions(String blurParam);
}
