package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.*;
import com.example.insurancesystem.service.DownstreamService;
import com.example.insurancesystem.utils.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/downstream")
/**
 * 管理企业下游合作机构，提供分页查询、导出、选项、详情以及新增修改删除接口，并以商户权限控制访问。
 */
public class DownstreamController {

    @Autowired
    DownstreamService downstreamService;

    @GetMapping
    @PreAuthorize("hasAuthority('merchant:select')")
    /**
     * 按机构关键字、区域和状态分页查询当前企业下游机构。
     */
    public ResponseResult select(DownstreamSearchDTO params) {
        return downstreamService.select(params);
    }

    @GetMapping("/excel")
    @PreAuthorize("hasAuthority('merchant:select')")
    /**
     * 使用同一筛选条件查询下游导出模型，并直接向响应流写出 Excel。
     */
    public void getExcel(DownstreamSearchDTO params, HttpServletResponse response){
        List<DownstreamExcelDTO> excelDTOList = downstreamService.getExcel(params);
        WebUtils.renderExcel(response, excelDTOList, DownstreamExcelDTO.class);
    }

    @GetMapping("/option")
    @PreAuthorize("hasAuthority('merchant:select')")
    /**
     * 按模糊关键字返回精简下游机构选项，供工单表单选择来源或处理机构。
     */
    public ResponseResult selectOptions(String blurParam){
        return downstreamService.selectOptions(blurParam);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('merchant:select')")
    /**
     * 查询单个下游机构及其区域、联系人等完整资料。
     */
    public ResponseResult selectById(@PathVariable("id") Long id) {
        return downstreamService.selectById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('merchant:update')")
    /**
     * 新增下游合作机构，并由服务层维护机构编码、区域和员工关联。
     */
    public ResponseResult insert(@RequestBody DownstreamDTO params) {
        return downstreamService.insert(params);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('merchant:update')")
    /**
     * 更新下游机构及其关联资料，服务层负责事务一致性与重复校验。
     */
    public ResponseResult update(@RequestBody DownstreamDTO params) {
        return downstreamService.update(params);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('merchant:update')")
    /**
     * 删除下游机构及可安全移除的关联数据，引用约束由服务层判断。
     */
    public ResponseResult delete(Long id) {
        return downstreamService.delete(id);
    }
}
