package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.importbatch.BatchImportTemplateConfig;
import com.example.insurancesystem.service.MerchantBatchImportService;
import com.example.insurancesystem.utils.OSSUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 暴露商户批量导入的模板地址、结构预检和正式导入接口。
 * 所有写入操作沿用 merchant:update 权限，模板地址和预检沿用 merchant:select 权限。
 */
@RestController
@RequestMapping("/merchant/batch-import")
public class MerchantBatchImportController {

    private final MerchantBatchImportService batchImportService;
    private final OSSUtil ossUtil;

    @Value("${insurance.batch-import.templates.upstream-object-name}")
    private String upstreamTemplateObjectName;

    @Value("${insurance.batch-import.templates.downstream-object-name}")
    private String downstreamTemplateObjectName;

    public MerchantBatchImportController(MerchantBatchImportService batchImportService, OSSUtil ossUtil) {
        this.batchImportService = batchImportService;
        this.ossUtil = ossUtil;
    }

    /**
     * 根据配置中的私有 OSS 对象路径返回临时签名地址。OSSUtil 会优先复用 Redis 中仍有效的地址，
     * 缓存到期后自动重新签名，前端无需了解 Bucket 权限和刷新策略。
     */
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('merchant:select')")
    public ResponseResult templates() {
        String upstreamUrl = ossUtil.getTmpUrl(upstreamTemplateObjectName);
        String downstreamUrl = ossUtil.getTmpUrl(downstreamTemplateObjectName);
        if (upstreamUrl == null || downstreamUrl == null) {
            return new ResponseResult(500, "批量导入模板临时下载地址生成失败");
        }
        return new ResponseResult(200, new BatchImportTemplateConfig(upstreamUrl, downstreamUrl));
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

    /** 确认后重新提交同一下游文件，导入机构及其有效人员。 */
    @PostMapping("/downstream")
    @PreAuthorize("hasAuthority('merchant:update')")
    public ResponseResult importDownstream(@RequestParam("file") MultipartFile file) {
        return batchImportService.importDownstream(file);
    }
}
