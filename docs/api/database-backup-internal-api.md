# 备份暂停日志消费者内部接口

仅供维护协调器使用；Header X-Maintenance-Secret:string 必填，与maintenance.participant.internal-secret一致。没有用户JWT授权；维护过滤器放行/internal/maintenance/但控制器逐次校验密钥。无前端页面和mock需求。

## POST /internal/maintenance/log-consumer/pause

query runId:string 必填非空；seconds:integer 必填1至7200，暂停最长租约秒数。

等待在途数据库事务及消息确认完成，最多等待30秒，再设置暂停；同runId可续租，不同runId冲突409。不清空队列，不ACK待入库消息。响应：{"code":200,"msg":"已暂停"}。

参数不合法400，凭证无效403，其他备份占用/事务未排空409，服务器异常500。超时租约自动恢复，无需数据库写入或定时器。

## POST /internal/maintenance/log-consumer/resume

query runId:string 必填。仅解除属于此runId的暂停，重复或迟到请求不解除其他备份暂停。响应：{"code":200,"msg":"已恢复"}。

调用方finally必须尝试恢复，包括pause响应丢失的情况。调用失败不发送前端通知，由协调器服务器日志告警，租约最终兜底。
