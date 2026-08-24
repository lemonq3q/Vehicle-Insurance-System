package com.example.insurancesystem.domain.merchant.importbatch;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 向前端公开由服务端配置管理的官方模板 OSS 地址。
 * 地址只用于下载，不参与上传文件解析，便于运维替换模板版本而无需重新构建前端。
 */
@Data
@AllArgsConstructor
public class BatchImportTemplateConfig {
    private String upstreamUrl;
    private String downstreamUrl;
}
