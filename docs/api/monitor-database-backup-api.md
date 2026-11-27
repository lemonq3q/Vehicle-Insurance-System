# 监控数据库备份浏览与下载接口

权限：监控JWT及ADMIN角色。复用统一响应、失败顶部通知。监控只读取OSS两份统一索引，不访问非监控数据库表，不上传或删除备份。

## GET /monitor/database-backups/files

按数据所属日期浏览当前层目录，每次读取`full/index.json`及`binlog/index.json`，不逐对象列举或读取附属JSON。

| Query参数 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- |
| path | string | 否 | 相对目录，默认根目录；非空以`/`结尾，如`full/2026-11-27/`。禁止绝对路径、反斜杠、空路径段、`.`和`..` |
| marker | string | 否 | 上一页返回的nextMarker；新查询或换目录清空，不解析游标内容 |
| startDate | yyyy-MM-dd | 否 | 所属日期下限，含边界；与endDate一起传 |
| endDate | yyyy-MM-dd | 否 | 所属日期上限，含边界；与startDate一起传 |

日期限制为北京时间今天及前29个自然日。使用索引的`backupDate`，不依据uploadedAt、OSS LastModified或binlog事件时间；每个文件只有一个固定所属日期。未知日期文件只在不传日期参数时显示。binlog错过维护边界可沿用前一周期日期，过期删除参考更保守的周期结束日，允许延后清理。

分页先筛选再按目录、文件分组，各组按名称升序，每页合计最多100项。marker为目录或文件名称游标，索引正在更新时不保证跨页快照一致，刷新从第一页重新读取。没有下一页时nextMarker为空字符串。目录由符合条件文件构造，空目录不显示；路径不存在返回空列表。

响应外壳：`{code:200,data:{path,folders,files,nextMarker}}`。

| data字段 | 结构 |
| --- | --- |
| folders | `[{path:string,name:string,dataRangeKnown:boolean,dataStartDate?:string,dataEndDate?:string,backupDate?:string}]` |
| files | `[{key:string,name:string,sizeBytes:number,modifiedAt:string,dataRangeKnown:boolean,dataStartDate?:string,dataEndDate?:string,backupDate?:string}]` |

文件的dataStartDate、dataEndDate均等于backupDate；目录的日期是已筛选子文件所属日的最小值与最大值，仅用于展示。modifiedAt采用索引uploadedAt，缺少时为空。未知日期dataRangeKnown=false，不填日期字段。

mock响应：

```json
{"code":200,"data":{"path":"full/2026-11-27/example/","folders":[],"files":[{"key":"database-backup/prod/full/2026-11-27/example/database.sql.gz","name":"database.sql.gz","sizeBytes":1024,"modifiedAt":"2026-11-27T00:00:00Z","dataRangeKnown":true,"backupDate":"2026-11-27","dataStartDate":"2026-11-27","dataEndDate":"2026-11-27"}],"nextMarker":""}}
```

仅明确NoSuchKey表示尚无索引；无权限、网络失败、索引损坏或对象路径越界返回502，不当成空结果。

## GET /monitor/database-backups/download

Query `key:string`必填，使用列表返回的完整对象Key。验证处于配置备份实例前缀及存在性，签发10分钟私有附件下载链接。每次点击重新申请，浏览器直连OSS，不打包、不经过服务端转存。

```json
{"code":200,"data":{"name":"database.sql.gz","downloadUrl":"https://example.test/database.sql.gz?signature=...","expiresAt":"2026-11-27T00:10:00Z"}}
```

错误：参数、路径或日期非法400；未登录401；非ADMIN403；对象不存在404；OSS依赖失败502。普通请求统一通知一次，页面仅恢复loading或显示局部重试状态。

## 配置与联调

`monitor.backup.bucket/prefix/instance`必须与协调器一致，默认lemonqwq、database-backup、prod。RAM需备份前缀Get权限，服务不再需要ListObjects进行查询。前端mock位于`monitor-frontend/src/mock/databaseBackups.js`，`VUE_APP_BACKUP_MOCK=true`仅开启本模块，默认调用真实API。

部署coordinator、monitor及monitor-frontend，无数据库结构变更。旧OSS清单没有统一索引时不自动列举迁移；本地仍有旧状态的备份可由协调器导入并幂等写入新索引。完整架构见[数据库备份架构](../system-architecture/database-backup.md)。
