# 提醒生效状态与条件复核契约

## GET /api/monitor/reminders

权限与现有提醒列表一致（监控账号 JWT），原筛选及分页参数保持不变。
新增 query `isActive`（integer，可选）：默认 `1` 仅生效，`0` 仅失效，`-1` 全部。
响应外壳及排序不变，`data` 为 `{list,pageNo,pageSize,total}`；每条增加
`isActive`（0/1）、`invalidatedAt`（时间或null）、`lifecycleVersion`（long）。
`processStatus` 是人工处理状态，与生效状态互相独立，失效不等同于已处理。

示例：`GET /api/monitor/reminders?isActive=0&pageNo=1&pageSize=20`

```json
{"code":200,"data":{"list":[{"id":1,"enterpriseId":1,"reminderType":"WALLET_BALANCE_NEGATIVE","isActive":0,"invalidatedAt":"2026-09-28T12:00:00","lifecycleVersion":2,"processStatus":0}],"pageNo":1,"pageSize":20,"total":1}}
```

错误：非法生效状态返回400，未登录401，无权限403；失效提醒的人工处理/恢复操作返回409。
Mock 位于 `monitor-frontend/src/mock/reminders.js`，由现有 adapter 路由调用，含超过一个月仍生效、失效和已处理示例。

## GET /portal/reminders/recent

保留原路径和响应字段，不再限制最近一个月，也不再依据旧 `expires_at` 隐藏。
当前企业所有 `is_active=1` 提醒持续展示，排序沿用严重程度、最近触发时间及id倒序。
失效记录保留在数据库，门户不再展示。

## POST /internal/reminders/merge

沿用 `X-Maintenance-Secret` 和已有完整提醒快照；新增 body 字段：

| 字段 | 类型 | 必填 | 含义 |
|---|---|---|---|
| isActive | integer | 新版请求必填 | 0或1 |
| lifecycleVersion | long | 新版请求必填 | SaaS持久化递增版本，必须>0 |
| invalidatedAt | datetime/null | 失效时必填 | 条件复核时间 |

其他字段仍包括 enterpriseId、enterpriseName、reminderType、reminderKey、reminderStage、stageLevel、severity、title、content、businessDataJson、triggeredAt。
监控只接受更高生命周期版本，重复或乱序旧快照返回 `IGNORED`；失效保留客服处理记录，重新生效或风险升级恢复待处理。
响应 `data.action` 为 CREATED、UPDATED、INACTIVATED、REACTIVATED、IGNORED，HTTP200表示该版本已被接收或已被更新版本覆盖。
缺少必要字段/非法版本返回400，内部密钥错误403，数据库失败500。

## 每日维护及部署

不再运行独立的5分钟提醒扫描。既有每日维护先计算当前企业应有提醒，再按主键游标每批100条失效旧提醒，
随后用同一计算结果新增、恢复、升级或降级提醒，最后补偿待同步记录，不按提醒年龄失效。
降级保留门户已读状态、触发次数和最近触发时间，并保留监控人工处理状态；升级或重新生效重新提示。
充值等操作解除风险后，提醒将在下一次每日维护失效；跨服务失败也由后续每日维护重试。
失效及重新生效均保留原记录，SaaS持久化 `sync_pending`，网络失败不丢同步意图，后续扫描重试。
未知类型保留而非误失效。单实例每日生成与复核串行，数据库版本条件避免覆盖并发升级。
迁移文件 `backend/db/migration/V20260928_03_add_reminder_lifecycle.sql` 需审阅执行后再发布两端后端。
配置：REMINDER_RECONCILIATION_BATCH_SIZE（100，最大500）；原提醒扫描开关、间隔和首次延迟配置已移除。
接口参数、响应字段与Mock结构未改变，本次调整不新增数据库字段。
