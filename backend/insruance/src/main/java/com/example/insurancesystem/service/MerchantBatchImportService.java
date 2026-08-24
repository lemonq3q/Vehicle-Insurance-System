package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 定义上游、下游及其商户人员的 Excel 模板校验和正式批量导入能力。
 * 模板校验与数据校验分阶段执行，防止用户仅因数据错误而被误判为使用了错误模板。
 */
public interface MerchantBatchImportService {
    /** 校验上游工作簿的 Sheet 与表头结构，不校验数据行。 */
    ResponseResult validateUpstreamTemplate(MultipartFile file);

    /** 校验下游工作簿的 Sheet 与表头结构，不校验数据行。 */
    ResponseResult validateDownstreamTemplate(MultipartFile file);

    /** 逐行筛选上游数据并批量写入全部有效记录。 */
    ResponseResult importUpstream(MultipartFile file);

    /** 逐行筛选下游及人员数据并按依赖顺序批量写入有效记录。 */
    ResponseResult importDownstream(MultipartFile file);
}
