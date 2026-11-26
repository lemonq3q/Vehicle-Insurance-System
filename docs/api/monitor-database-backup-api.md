# 监控数据库备份浏览与单文件下载

仅ADMIN，沿用监控JWT、统一响应及错误通知。仅OSS只读，不查询业务数据库，不修改原备份或清理逻辑。不打包、不复制、不生成临时下载文件。

## GET /monitor/database-backups/files
Query path:string可选，默认根目录，必须为相对目录且以/结尾，不允许..、绝对路径、反斜杠；marker:string可选，OSS分页标记。响应data：{path,folders:[{path,name}],files:[{key,name,sizeBytes,modifiedAt}],nextMarker}。每批最多100项，按OSS键排序。路径不存在返回空数组。
示例：{"code":200,"data":{"path":"","folders":[{"path":"full/","name":"full"},{"path":"binlog/","name":"binlog"},{"path":"catalog/","name":"catalog"}],"files":[],"nextMarker":""}}

## GET /monitor/database-backups/download
Query key:string必填，目录列表返回的完整对象Key，必须位于配置的备份前缀内，且不是目录。后端检查存在性，再签发10分钟私有下载链接，保留原文件名并设置附件下载响应头。
示例：{"code":200,"data":{"name":"database.sql.gz","downloadUrl":"https://OSS域名/对象路径?临时签名","expiresAt":"2026-11-26T04:10:00Z"}}
前端每次点击文件右侧“下载”都重新申请链接，浏览器直接下载OSS原文件，不经过Axios Blob缓存或服务端转存。目录导航、加载更多和空状态保持原样。

错误：非法路径400、未认证401、无ADMIN403、文件不存在/已被过期清理404、OSS依赖失败502。统一请求层负责一次顶部通知，页面只恢复按钮状态。原/downloads后台打包接口已移除，不提供批量ZIP任务。

monitor.backup.bucket/prefix/instance须与协调器一致，默认lemonqwq、database-backup、prod。RAM仅需备份前缀List/Get，下载功能不再要求Put/Delete/Copy/Multipart权限。OSS文件ACL维持私有，禁止公开备份目录。

前端mock：monitor-frontend/src/mock/databaseBackups.js，VUE_APP_BACKUP_MOCK=true仅用于本模块。mock提供JSON样例下载，默认真实API。

如之前版本已部署，遗留database-backup-downloads/{instance}/临时包可经管理员审阅后清理或通过此前生命周期规则过期；本版不再写入或清理该前缀。本功能不创建本地目录，因为不再产生暂存文件。
