# 维护数据库备份

## 行为与部署

备份在coordinator现有阶段执行完毕、FINISH释放参与服务之前运行；原任务顺序不变。maintenance.backup.enabled默认false，未开启时不运行工具、不创建目录、不连接OSS。启用后的首次作业会递归创建root及full、binlog、state目录，运行用户需有父目录写权限。无需新增数据库表或前端。

本地目录默认/var/data/MaintenanceCoordinator/mysql-backup，仅作暂存：全量.gz上传并验证后删除，留下小清单；失败的有效压缩备份保留并每5分钟重试。导出中断的.sql和无manifest目录不会被误发布，需运维检查后清理，不能当作可恢复备份。MySQL原始binlog与本地采集副本不是同一目录；本功能不会PURGE MySQL原始日志。

配置样例：base_conf/backup/coordinator-backup.yml.example、mysql-backup.cnf.example。启用前安装mysql、mysqldump、mysqlbinlog官方工具（匹配服务器版本），准备权限600的独立client配置文件；工具必须能访问同一实例。默认使用MySQL8.0.26+支持的--source-data=2；旧客户端显式改为--master-data=2。当前仅支持InnoDB一致性全量，发现非InnoDB表会拒绝备份。不要在导出期间执行DDL。

备份账号需要insurance_saas的SELECT、SHOW VIEW、TRIGGER、EVENT及导出存储程序所需权限（MySQL版本要求不同），以及读取binlog/坐标的复制相关权限、FLUSH BINARY LOGS和mysqldump启动短暂全局读锁所需权限。由DBA按版本审阅配置，不自动创建用户、授权或修改MySQL参数。不能使用无权限的纯业务账号直接替代。密码只放受限client文件，不出现在命令行和备份清单。

部署coordinator及monitor后端；其他后端和前端无需调整。协调器、监控的maintenance内部密钥保持一致，monitor-url指向实际监控实例。初期仅支持单协调器、单监控消费实例；多实例必须扩展为分布式锁和逐实例暂停确认后才能启用。

## 全量与暂停

业务维护结束 -> MySQL预检 -> 暂停日志入库并等待当前事务/ACK结束 -> mysqldump一致性导出（同时--flush-logs并记录快照坐标） -> FLUSH BINARY LOGS按用户要求在导出后再轮转 -> finally恢复日志 -> 压缩/上传binlog及全量 -> 发布清单 -> 过期清理。

暂停仅针对监控请求日志消费者，不停止JVM或业务线程。Redis仍接收消息，消费者不开始新事务，不提前ACK。暂停有runId归属和有限租约，默认工具超时1800秒，暂停租约1920秒；恢复请求丢失时自动到期。原业务后台任务未全部被禁写，一致性依赖InnoDB快照和正确binlog坐标，不能把导出后轮转位置当作恢复起点。

暂停期间积压可能占用Redis内存，需保证Redis保留消息，不在备份期间裁剪Stream。维护心跳依旧通过原多线程调度器续租，binlog上传使用独立执行器。全量等待现有binlog作业最多60秒，超时明确记录本次未备份；导出/工具/上传失败报警并释放维护流程，不无限阻塞。

## OSS与校验

复用OSSClientSingleton（现有Endpoint、区域、凭证），默认现有Bucket lemonqwq，可显式配置备份Bucket。独立前缀database-backup/prod/，不写sys_file，不生成业务预签名URL。RAM需要此前缀的Put/Get/List/Delete权限。

full/{backupId}/database.sql.gz与manifest.json保留每日版本。binlog/{serverUuid}/{原文件名}同名覆盖，不预删除；每5分钟先查询文件列表，没变化的副本跳过，已关闭文件不重复发送。通过mysqlbinlog远程raw读取完整事件，校验事件checksum后上传；不直接读取正在写入的MySQL数据文件。可恢复到最后完整提交事务，活动文件尾部未提交事务不代表已完成备份。不要重置同一serverUuid下binlog编号或改文件名前缀；若重装必须新UUID或新instance前缀。

SDK上传后检查远端长度及sha256自定义元数据，清单记录本地SHA256供恢复下载后再次核验。manifest包含数据库/版本/serverUuid、维护runId、导出开始/完成时间、上传完成时间、文件大小/摘要、快照binlog文件与位置、导出后轮转文件。startedAt是备份作业开始时间，不假定为精确快照时间，真实恢复边界以dump内文件/位置为准。catalog/latest.json只指向最近发布成功清单；恢复不依赖本地服务器。

全量发布前检查OSS从快照文件开始的binlog序号连续；缺失起点或日志缺口拒绝发布。发现失败要检查MySQL保留期和采集状态，不删除旧有效备份。

OSS未开启版本控制时同名覆盖替换旧副本；开启版本控制时会产生历史版本。必须另行在备份前缀配置非当前版本及删除标记生命周期，否则仅删除当前对象不代表释放历史版本空间。本模块不擅自更改共享Bucket生命周期。LastModified会随覆盖更新，不能用它代替数据库数据时间。

## 保留与恢复

每次成功全量后清理超过30天的全量版本；为覆盖30天边界保留必要的前一份快照（可能31份），按每个serverUuid最早保留快照的恢复起点保护binlog。仅删除超过窗口、已关闭且无保留备份依赖的binlog；没有有效备份的首次故障不能靠清理掩盖。OSS不可用或新全量失败时不执行清理，旧备份可暂时超过30天。

本地已上传binlog副本立即清理，state小JSON保留用于避免重复上传，不长期保存30天大文件。MySQL本地过期设置与OSS保留独立；自动过期不检查上传成功，必须监控源文件保留期与积压。

恢复只在隔离实例手动演练：下载manifest与全量并校验SHA256 -> 导入 -> 从快照坐标重放连续binlog到目标完整事务。binlog属于整个MySQL实例，若同时有其他数据库，不能盲目向线上实例重放或用简单文本过滤；需DBA审阅库范围和跨库事务。本功能不自动恢复/覆盖线上库；OSS附件本体不包含在数据库备份中。

## 验证边界

单元测试不连接线上数据库/OSS。上线先保持enabled=false，配置工具/账号/权限后在测试环境做全量+binlog恢复演练、OSS同名覆盖、失败重试、30天边界清理、暂停超时恢复测试；确认可恢复后再启用生产。备份错误及成功ID输出协调器日志，需接入现有服务器日志告警。五分钟是调度周期而非零丢失保证，实际异地可恢复延迟还含采集/上传及故障重试。
