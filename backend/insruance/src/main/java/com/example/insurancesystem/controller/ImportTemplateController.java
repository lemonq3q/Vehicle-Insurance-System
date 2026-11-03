package com.example.insurancesystem.controller;

import com.example.insurancesystem.handler.exception.BusinessException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 通过现有鉴权提供与本版本导入契约配套的 Excel 模板，避免旧 OSS 文件与新校验脱节。
 * 仅允许固定业务类型的 classpath 资源下载，不接收任意文件路径，不修改服务器或数据库状态。
 */
@RestController
public class ImportTemplateController {
    /**
     * 下载上游、下游或工单模板，使用统一文件响应机制。
     * @param businessType 固定业务标识。
     * @param response 浏览器下载响应。
     * @throws IOException 模板资源缺失或响应流写入失败。
     */
    @GetMapping("/import-templates/{businessType}")
    @PreAuthorize("(#businessType == 'workorder' and hasAuthority('workorder:select')) or (#businessType != 'workorder' and hasAuthority('merchant:select'))")
    public void download(@PathVariable("businessType") String businessType, HttpServletResponse response) throws IOException {
        String title;
        switch (businessType) {
            case "upstream": title = "上游批量导入模板"; break;
            case "downstream": title = "下游批量导入模板"; break;
            case "workorder": title = "工单批量导入模板"; break;
            default: throw new BusinessException(400, "不支持的导入类型");
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + URLEncoder.encode(title + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20"));
        try (InputStream input = new ClassPathResource("import/" + businessType + ".xlsx").getInputStream()) {
            input.transferTo(response.getOutputStream());
        }
    }
}
