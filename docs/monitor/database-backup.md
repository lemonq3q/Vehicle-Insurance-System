# 数据库备份部署与恢复说明

架构、目录、状态流程和保留规则统一见[数据库备份架构](../system-architecture/database-backup.md)。备份仅由coordinator写入，监控提供按目录、日期浏览和单文件下载，无数据库表变更。

## 部署

部署coordinator、monitor及monitor-frontend。`maintenance.backup.enabled`默认false；未启用时不运行工具、不创建备份目录、不连接OSS。启用后自动建立`/var/data/MaintenanceCoordinator/mysql-backup`及子目录，运行用户须有父目录写权限。本地仅暂存待处理大文件，COMPLETED后可删；状态在窗口内保留，每日清理过期信息及孤立文件，不额外保留30天大文件副本。

配置样例在`base_conf/backup/coordinator-backup.yml.example`、`mysql-backup.cnf.example`和`monitor-download.yml.example`。安装与服务端匹配的mysql、mysqldump、mysqlbinlog工具，准备权限600的独立client配置文件。默认`--source-data=2`，旧客户端按版本改用`--master-data=2`。密码只在受限client文件，不出现在命令行和索引。

备份账号需目标库SELECT、SHOW VIEW、TRIGGER、EVENT及导出存储程序所需权限，还需读取binlog/坐标、FLUSH BINARY LOGS及mysqldump短暂全局读锁相关权限，由DBA按版本审阅配置。本功能不自动创建账号、授权或修改MySQL参数。

当前导出只支持InnoDB一致性快照，非InnoDB拒绝备份；导出期间不能执行DDL。协调器和监控内部维护密钥必须一致，monitor-url指向实际监控实例。限单协调器写入同一OSS实例前缀、单监控日志消费实例，同本地目录有文件锁避免多进程写入，多机不得共用前缀。

## 维护与上传

业务维护末尾、FINISH之前暂停监控请求日志消费者并等待在途事务/ACK结束，执行一致性导出并记录真实binlog快照坐标；导出后再轮转，finally恢复消费。上传和压缩不延长暂停。默认工具超时1800秒、暂停租约1920秒；恢复请求失败租约自动到期。暂停期间Redis仍接收消息，不能裁剪未消费Stream。维护心跳继续续租，其他业务任务并未被强制禁写，一致性依赖InnoDB快照。

每五分钟读取SHOW BINARY LOGS，对比本地统一状态：新增/增长采集稳定副本，关闭变化更新索引，不变跳过。工具通过mysqlbinlog远程raw读取和checksum验证，不直接复制MySQL活动数据文件。未完成版本先按状态处理，完成后才采集下一版本。上传失败按原所属日期及固定key重试，不在上传前删除旧对象。

所有文件统一走待上传、待更新云端索引、已完成三个状态。索引更新按objectKey幂等覆盖；没有针对不同宕机位置的恢复分支。文件已生成但没有登记状态的情况不补救，归入每日孤立文件清理。状态无法读取时任务报错停止，不能用空状态覆盖。

OSS使用系统OSSClientSingleton及现有Bucket配置，独立`database-backup/prod/`前缀；协调器RAM需该前缀Put/Get/Delete，监控只需Get。远端只使用`full/index.json`、`binlog/index.json`两份统一信息文件，不再创建单文件manifest、binlog附属JSON或latest.json。索引保存大小、SHA256、所属日、版本及恢复坐标，完整字段见架构文档。旧对象不由本次升级直接删除或改名。

OSS启用版本控制时，同名覆盖和删除会保留旧版本或删除标记，需另外配置相应生命周期。本模块不修改共享Bucket策略。MySQL源binlog自动过期设置与OSS保留独立，必须监控源端保留期和待上传积压，不可因OSS计划保留30天而让MySQL过早清除未采集日志。

## 保留与恢复

默认保留北京时间最近30个自然日。索引保存固定backupDate用于检索，binlog周期结束日作为保守清理参考。漏维护时沿用前一周期，直到下次成功划分才闭合，允许延后删除。未知日期、未闭合周期和仍被全量恢复依赖的文件不会提前删除。保留窗口前最近全量作为恢复锚点及其后依赖binlog，实际保留可能超过30天。

全量发布前检查从快照起点开始的binlog连续且覆盖导出后轮转文件，缺口保持待更新索引，下周期继续处理。完整索引不能证明未来不会出现源日志缺口，仍需服务器告警和隔离恢复演练。

恢复只在隔离实例操作：下载full/index.json、所选全量及binlog/index.json，按索引SHA256校验，导入全量，从binlogStartFile/binlogStartPosition依次重放至目标完整事务。日期不能替代恢复坐标。binlog属于整个MySQL实例，涉及其他库或跨库事务应由DBA审阅恢复范围。OSS附件本体不在数据库导出中。本功能不自动恢复、覆盖线上库或PURGE MySQL原始日志。

上线先保持enabled=false，完成测试环境全量+binlog恢复、同名覆盖、失败续传、30天边界及暂停恢复验证后再启用。单元测试仅模拟数据库工具与OSS；五分钟是调度周期，实际异地恢复延迟还包含采集、上传及失败重试。


## 维护后端.env配置

维护后端通过启动环境处理器复用SystemCommonUtil现有.env读取方式，在Bean绑定及定时任务注册前应用备份变量，无需source、导出环境变量或修改YAML。样例见`base_conf/backup/coordinator-backup.env.example`。最少设置`MAINTENANCE_BACKUP_ENABLED=true`和`MAINTENANCE_BACKUP_DEFAULTS_FILE=/etc/vehicle-insurance/mysql-backup.cnf`，连接文件仍需存在。修改.env后重启维护后端。

沿用现有查找规则：优先当前工作目录.env，再尝试代码所在目录旁.env；同名配置.env优先于进程变量。备份变量使用MAINTENANCE_BACKUP_前缀，defaults-file等复合名称支持DEFAULTS_FILE和DEFAULTSFILE两种拼写，优先前者。所有已提供的备份变量覆盖原Spring配置；未提供的保留原配置及默认值。扫描间隔BINLOG_INTERVAL_MS也通过同一环境生效。仅作用于维护后端备份，无接口、前端、数据库结构变化。


无法确认日期归属的binlog现在直接跳过，不采集、不上传、不新增本地文件记录。首次成功完成每日备份并建立边界后，才保存有明确归属的文件。扫描INFO日志的skippedUnknown显示跳过数量。旧版已有未知记录停止补传和索引更新，已存在的本地/OSS文件不自动删除。此变更只影响维护后端，无数据库或前端接口变更。
