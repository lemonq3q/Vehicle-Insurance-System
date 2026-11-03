package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.service.MerchantBatchImportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 暴露上下游批量导入的结构预检和正式导入接口。
 * 所有写入操作沿用 merchant:update 权限，模板地址和预检沿用 merchant:select 权限。
 */
@RestController
@RequestMapping("/merchant/batch-import")
public class MerchantBatchImportController {

    private final MerchantBatchImportService batchImportService;
    /**
     * 注入既有上下游导入服务；模板由ImportTemplateController随当前后端版本提供。
     */
    public MerchantBatchImportController(MerchantBatchImportService batchImportService) {
        this.batchImportService = batchImportService;
    }

    /** 上传上游文件并且只校验模板结构。 */
    @PostMapping("/upstream/validate")
    @PreAuthorize("hasAuthority('merchant:select')")
    public ResponseResult validateUpstream(@RequestParam("file") MultipartFile file) {
        return batchImportService.validateUpstreamTemplate(file);
    }

    /** 确认后重新提交同一上游文件，执行数据筛选和正式导入。 */
    @PostMapping("/upstream")
    @PreAuthorize("hasAuthority('merchant:update')")
    public ResponseResult importUpstream(@RequestParam("file") MultipartFile file) {
        return batchImportService.importUpstream(file);
    }

    /** 上传下游文件并且只校验模板结构。 */
    @PostMapping("/downstream/validate")
    @PreAuthorize("hasAuthority('merchant:select')")
    public ResponseResult validateDownstream(@RequestParam("file") MultipartFile file) {
        return batchImportService.validateDownstreamTemplate(file);
    }

    /** 确认后重新提交同一下游文件，导入包含联系人和手机的下游基础信息。 */
    @PostMapping("/downstream")
    @PreAuthorize("hasAuthority('merchant:update')")
    public ResponseResult importDownstream(@RequestParam("file") MultipartFile file) {
        return batchImportService.importDownstream(file);
    }
}
