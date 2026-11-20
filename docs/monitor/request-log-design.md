# 请求诊断日志部署与运行

此模块仅观察请求边界，不改变业务处理。不自动捕获业务内部已处理异常；未来业务可调用 RequestLogContext.setBusinessInfo / appendBusinessInfo / recordException。上下文是线程私有，不自动传播到业务线程池。

## 部署

1. 审阅并在监控数据库执行 backend/db/migration/V20261120_01_request_logging.sql（本次未执行）。两张新增表不使用deleted，不参加逻辑删除归档。
2. 四个后端连接同一Redis实例和逻辑库，Stream固定 monitor:request-logs:v1，不受原业务缓存前缀影响。Redis至少5.0，允许Stream命令。监控/协调器新增 REDIS_HOST / REDIS_PORT / REDIS_PASSWORD / REDIS_DATABASE 配置；其他后端仍沿用现有Redis配置。
3. 部署四个后端和监控前端。菜单“平台设置 / 请求日志”，仅ADMIN可见且后端校验角色。
4. 为服务运行用户提供 /var/data 下创建目录和写文件的权限。

默认目录按 spring.application.name：/var/data/InsuranceSystem、/var/data/InsuranceSystemSaas、/var/data/InsuranceSystemMonitor、/var/data/MaintenanceCoordinator，自动创建。部署路径不应位于代码仓库；容器挂载持久卷。

配置：request-log.queue-capacity 默认2000；request-log.spool-root 默认/var/data；request-log.replay-cron 默认每天北京时间04:30。各进程独立补投线程调度，在每日维护窗口读取.ready文件，Redis成功后删除，失败保留至下一次。多实例需独立文件系统/目录，不能共享同一后端的溢出目录。

请求结束构造快照，非阻塞入内存队列；满队列同步本地落盘（无Redis等待）。后台发送Redis失败也落盘。单条完整文件原子更名.ready，.writing异常残留不自动补投以避免半写JSON；需人工检查。内存日志允许在进程崩溃丢失，正常停机等待5秒。

监控消费线程先处理pending后读新消息，数据库主表+详情在同一事务入库，提交后ACK并删除Stream消息。log_id唯一键保证重复补投/消费幂等。数据库未建表或不可用时消息保留等待重试。监控多实例可能重复消费pending，但入库幂等；建议初期单实例消费。

请求开始时间为服务器接收时间；耗时为单调时钟差。has_exception只表示边界捕获异常，业务code失败不一定有异常。MVC入口从已认证主体提取企业用户；鉴权前拒绝和匿名请求身份为空。文件只记URI/状态，不保留文件内容。应用层之前的代理拒绝、JVM崩溃等不保证采集。

需监控Redis积压、数据库增长和本地磁盘空间；本次不自动裁剪未消费消息，不自动删除历史日志。无需新增业务状态表或接入现有OCR业务。
