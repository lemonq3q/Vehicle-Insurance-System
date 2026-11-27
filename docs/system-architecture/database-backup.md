# 数据库备份架构

## 范围与职责

协调器是备份唯一写入方，负责维护末尾全量导出、五分钟binlog扫描、重试、轮转和清理。监控只读取两份OSS索引，按北京时间所属日期及目录展示，管理员手动下载原文件。复用系统OSS客户端与私有Bucket，不新增数据库表，不自动恢复或覆盖线上库，不解析binlog事件时间。

本地目录和OSS前缀分别由maintenance.backup.root及prefix/instance配置，目录不存在自动创建。默认关闭，配置工具、账号和权限并验证恢复后再启用。单协调器及单监控日志消费者部署；跨进程互斥使用本地文件锁保证同一暂存目录只有一个写入方，多机不得共用同一OSS实例前缀。

## 目录

```text
本地 /var/data/MaintenanceCoordinator/mysql-backup/
  full/{backupDate}/{backupId}/database.sql.gz
  binlog/{serverUuid}/{backupDate}/{稳定副本}
  state/full.json
  state/binlog.json

OSS database-backup/prod/
  full/index.json
  full/{backupDate}/{backupId}/database.sql.gz
  binlog/index.json
  binlog/{serverUuid}/{backupDate}/{原始binlog名}
```

不再产生单份manifest、单binlog JSON及latest.json。旧版对象不自动迁移、删除；本地旧版记录在首次初始化时导入统一状态，旧版未知日期binlog记录停止上传及索引更新，原有文件和记录保留，不自动删除；新扫描无法确定归属的文件直接跳过。迁移后的固定objectKey不改变，历史路径可通过新索引浏览。

## 索引与本地状态

统一协议schemaVersion=2；files以完整objectKey为唯一键。公共记录字段包含type、id、serverUuid、fileName、backupDate、retainThroughDate、objectKey、sizeBytes、sha256、revision、closed、startedAt、uploadedAt。全量另含database、runId、mysqlVersion、dumpCompletedAt、binlogStartFile、binlogStartPosition、afterDumpBinlog。远程不包含localPath、phase和lastError等本机信息。

本地另存localPath、phase、sourceSize、lastError及周期边界cycles。phase有PENDING_UPLOAD（待上传）、PENDING_INDEX（已上传待更新信息）、COMPLETED（已更新信息）。状态写入先落临时文件并force，再原子替换，保留上一份有效bak；读取主文件损坏则报错并中止采集/清理，不自动当空记录。binlog.json同时保存每次成功每日轮转的serverUuid、startFile和backupDate。

## 上传流程与状态推进

A1完整生成并校验本地文件；A2写PENDING_UPLOAD；A3同名覆盖上传并校验；A4写PENDING_INDEX；A5读取远程统一索引，按objectKey更新该版本并覆盖；A6写COMPLETED。全部完成后允许删除本地大文件，但状态在窗口内继续保留。

每个周期仅根据phase继续执行：待上传执行上传，待更新信息执行幂等更新索引，已完成跳过。无需针对宕机位置编写特殊判断、补偿或恢复流程。索引已更新但本地仍为待更新时，直接重复幂等更新即可。本地文件生成但尚未登记状态的情况不补救，按孤立文件清理。状态写失败中止当前批次，下周期重新读取；云端读写失败保留待处理状态。

路径、归属日期和该次副本大小/摘要在A2固定，不因补传改变。同一binlog上一版本完成前不生成下一版本，防止旧任务覆盖新数据。远程索引只有一个写入方，并且上传、清理在同一互斥内更新。远程仅NoSuchKey初始化空索引，网络/权限/解析错误不能当成空索引。

## 全量与日志暂停

原业务维护结束后读取本地状态，暂停监控日志新入库并等待在途事务，mysqldump一致性导出（含快照binlog坐标），导出后FLUSH BINARY LOGS，再持久化成功轮转边界，finally恢复消费。随后压缩、登记A2，处理本次和以前未完成记录。上传、压缩阶段不延长日志暂停；暂停有归属runId和有限租约，协调器失败时自动恢复。

轮转边界采用本次dump的起始binlog作为今日周期起点，覆盖dump附带轮转与导出后额外轮转之间的文件；只有每日明确完成的轮转切换日期。恢复必须使用全量中记录的真实binlog起始位置，不能使用导出后的新文件代替。仍依赖InnoDB快照，暂停日志不等于冻结所有后台写入；导出期间禁止DDL。

## binlog扫描与日期

每五分钟SHOW BINARY LOGS获取文件名、大小和活动文件；对照本地状态发现新增、增长、关闭或未完成记录。未知初始文件没有已知日期边界时直接跳过，不采集、不上传、不登记文件记录；已有文件归属确定后不改变。文件名在同一UUID/前缀内按数字序号判断周期，不按字符串日期或uploadedAt归属。

成功维护划定下一个周期时，前一周期retainThroughDate延后到新边界日。漏掉一天或多天维护时继续沿用上一个周期，不增加轮转意图恢复流程。未闭合周期、未知归属、缺失日期边界均不按推测日期提前删除。源文件消失且未完成时记录错误；已完成且源文件已清理不等于OSS记录需删除。

采集使用mysqlbinlog远程raw读取并校验，生成稳定副本后进入A2。源端binlog未变化且本地已完成时跳过；关闭状态变化必须更新远程索引。索引中同一文件仅一条记录。全量发布时保留原有连续性校验：从快照起点开始的binlog必须已上传并连续，覆盖导出后的轮转文件；缺口保留待更新索引状态。

## 保留与删除

窗口为北京时间今天及前retentionDays-1天，默认30天；查询按backupDate，删除按保守retainThroughDate并保护恢复依赖。按实例保留窗口前最近一份必要全量作为恢复锚点；其binlog恢复起点以及所有本地未完成全量的依赖日志均保护。无可信恢复起点、未知日期或未闭合周期不激进清理，允许超过30天。

远程：拉取统一索引，逐个删除已过期且不被依赖文件。删除成功或已不存在才移除索引；失败保留记录，最后覆盖索引。文件删成功而索引失败，下次利用删除幂等性完成，不增加删除状态文件。

本地：每日维护读取完整状态后扫描full/binlog目录。完成的大文件可删，窗口内待处理文件保留；过期未完成记录先报警再按保守边界及恢复依赖清理。状态未引用文件直接清理，两份主状态、bak和当前锁文件不参与孤立文件扫描；旧版单文件状态及工具/原子写遗留临时文件在有效状态读写完成后清除。先删文件后移除应过期状态，最后删除空目录。读取状态失败时禁止清理；不跟随符号链接，不操作暂存根目录之外路径。MySQL原始binlog不由本地清理删除。

COMPLETED记录即使无本地文件也在窗口内保留，防止再次采集；超窗源文件即使仍存在也不能重新上传。首次发现但无法确认归属的文件不保存，扫描日志以skippedUnknown汇总跳过数量。首次成功维护前不会新上传binlog；成功建立边界后正常保存已归属文件。旧版已存在未知记录不自动删除，也不继续处理。

## 监控查询与部署

监控读取full/index.json及binlog/index.json，以backupDate过滤近30天，不逐个列举OSS对象或读取附属清单；以索引中的objectKey构建目录、分页和下载入口。下载仍验证ADMIN及对象归属，签发10分钟私有链接。无日期参数兼容查看归属未知、因恢复依赖额外保留的文件。

部署coordinator、monitor及monitor-frontend。无需新增表、执行SQL或修改业务功能。工具、连接文件、权限配置参见docs/monitor/database-backup.md及base_conf/backup。OSS版本控制开启时，旧对象版本需要额外生命周期规则；本代码不擅自改变共享Bucket策略。

验证包括上传及索引失败后按状态继续执行、跨日补传固定路径、增长binlog版本、周期漏一天延后清理、删除幂等性、本地状态损坏中止清理和孤立文件回收。单元测试使用模拟工具及OSS；真实恢复演练在隔离MySQL进行，生产默认不启用。


## 运行日志

协调器默认INFO记录备份批次模式（DAILY_FULL或BINLOG_SCAN）、runId及耗时，全量预检、导出、日志暂停/恢复、轮转坐标和压缩文件登记，binlog新增/增长采集、扫描数量及跳过/延后数量，上传与索引更新的开始、完成及阶段。记录backupId、objectKey、backupDate、sizeBytes和revision，便于关联文件状态，不输出数据库内容或连接密码。

远程清理记录每个过期对象删除及每类索引确认后的删除、失败、保留数量；本地清理记录开始/结束和状态条目变化，单个已完成副本删除使用DEBUG。WARN/ERROR保留失败原因和异常堆栈。批次processingFinished仅表示本轮流程走完，不代表所有文件上传成功；需结合fullPending、binlogPending及各文件COMPLETED日志判断。备份未启用时不执行任务、不打印运行日志。


## 维护后端.env配置

维护后端通过启动环境处理器复用SystemCommonUtil现有.env读取方式，在Bean绑定及定时任务注册前应用备份变量，无需source、导出环境变量或修改YAML。样例见`base_conf/backup/coordinator-backup.env.example`。最少设置`MAINTENANCE_BACKUP_ENABLED=true`和`MAINTENANCE_BACKUP_DEFAULTS_FILE=/etc/vehicle-insurance/mysql-backup.cnf`，连接文件仍需存在。修改.env后重启维护后端。

沿用现有查找规则：优先当前工作目录.env，再尝试代码所在目录旁.env；同名配置.env优先于进程变量。备份变量使用MAINTENANCE_BACKUP_前缀，defaults-file等复合名称支持DEFAULTS_FILE和DEFAULTSFILE两种拼写，优先前者。所有已提供的备份变量覆盖原Spring配置；未提供的保留原配置及默认值。扫描间隔BINLOG_INTERVAL_MS也通过同一环境生效。仅作用于维护后端备份，无接口、前端、数据库结构变化。
