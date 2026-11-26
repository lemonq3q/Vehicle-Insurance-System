package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.monitor.service.MonitorBackupDownloadService;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 仅监控管理员可浏览数据库备份和手动下载文件，不提供原库恢复或删除入口。 */
@RestController
@RequestMapping("/monitor/database-backups")
@PreAuthorize("hasRole('ADMIN')")
public class MonitorBackupDownloadController {
    private final MonitorBackupDownloadService service;
    /** 注入OSS下载模块，不依赖业务数据库。 */
    public MonitorBackupDownloadController(MonitorBackupDownloadService service){this.service=service;}
    /** 按相对目录读取OSS一页文件，非法路径在服务端拒绝。 */
    @GetMapping("/files") public ResponseResult<?> files(@RequestParam(defaultValue="")String path,@RequestParam(defaultValue="")String marker){return new ResponseResult<>(200,service.files(path,marker));}
    /** 按管理员选择的文件生成短期私有链接，不允许访问备份目录外的对象。 */
    @GetMapping("/download") public ResponseResult<?> download(@RequestParam String key){return new ResponseResult<>(200,service.download(key));}
}
