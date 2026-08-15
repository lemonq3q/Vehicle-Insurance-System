package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.UpstreamDTO;
import com.example.insurancesystem.domain.merchant.UpstreamExcelDTO;
import com.example.insurancesystem.domain.merchant.UpstreamSearchDTO;
import com.example.insurancesystem.service.UpstreamService;
import com.example.insurancesystem.utils.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/upstream")
/**
 * 管理企业上游渠道及保险公司机构，提供列表、导出、选项、详情和维护接口。
 */
public class UpstreamController {

    @Autowired
    private UpstreamService upstreamService;

    @GetMapping
    @PreAuthorize("hasAuthority('merchant:select')")
    /**
     * 按筛选条件分页查询当前企业上游机构。
     */
    public ResponseResult select(UpstreamSearchDTO params) {
        return upstreamService.select(params);
    }

    @GetMapping("/excel")
    @PreAuthorize("hasAuthority('merchant:select')")
    /**
     * 按当前筛选导出上游机构 Excel，数据结构由 UpstreamExcelDTO 定义。
     */
    public void getExcel(UpstreamSearchDTO params, HttpServletResponse response){
        List<UpstreamExcelDTO> excelDTOList = upstreamService.getExcel(params);
        WebUtils.renderExcel(response, excelDTOList, UpstreamExcelDTO.class);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('merchant:select')")
    /**
     * 查询上游机构完整资料及区域关联。
     */
    public ResponseResult selectById(@PathVariable("id") Long id) {
        return upstreamService.selectById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('merchant:update')")
    /**
     * 新增上游渠道或保险公司，并维护机构分类与服务区域。
     */
    public ResponseResult insert(@RequestBody UpstreamDTO upstream) {
        return upstreamService.insert(upstream);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('merchant:update')")
    /**
     * 更新上游机构及关联资料，重复编码和引用边界由服务层校验。
     */
    public ResponseResult update(@RequestBody UpstreamDTO upstream) {
        return upstreamService.update(upstream);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('merchant:update')")
    /**
     * 删除指定上游机构及其区域、员工等关联记录。
     */
    public ResponseResult delete(Long id) {
        return upstreamService.delete(id);
    }

    @GetMapping("/option/insuranceCompany")
    @PreAuthorize("hasAuthority('merchant:select')")
    /**
     * 返回类别为保险公司的精简候选项，供工单承保公司选择。
     */
    public ResponseResult selectInsuranceCompanyOptions(String blurParam) {
        return upstreamService.selectInsuranceCompanyOptions(blurParam);
    }

    @GetMapping("/option")
    @PreAuthorize("hasAuthority('merchant:select')")
    /**
     * 按关键字返回全部上游机构精简选项，供业务表单复用。
     */
    public ResponseResult selectOptions(String blurParam) {
        return upstreamService.selectOptions(blurParam);
    }
}
