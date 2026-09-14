# 监控平台提醒处理 API

## 每日维护新增规则

提醒类型 `AUTO_RENEW_PLAN_UNAVAILABLE`（自动续费套餐已下架）属于“套餐与续费”类别。企业开启自动续费、
实际续费目标套餐不可用且进入现有 7 天/1 天到期窗口时，SaaS 使用以下稳定键向企业提醒表和监控提醒表合并：

`AUTO_RENEW_PLAN_UNAVAILABLE:subscription-{subscriptionId}:renew-{nextRenewAt}`

目标套餐在同一续费周期恢复上架后，SaaS 删除企业端相同键的提醒，并调用监控内部清理接口删除客服端提醒。
两个删除操作均为幂等操作；每日维护会重复请求监控端，以补偿此前跨服务失败。

### POST `/internal/reminders/delete`

仅供 SaaS 维护任务调用，请求头必须携带 `X-Maintenance-Secret`。该接口只允许删除
`AUTO_RENEW_PLAN_UNAVAILABLE` 类型，不能用于清理其他业务提醒。

```json
{
  "enterpriseId": 1,
  "reminderKey": "AUTO_RENEW_PLAN_UNAVAILABLE:subscription-8:renew-2026-09-01T00:00"
}
```

返回动作是 `DELETED` 或 `IGNORED`。

所有接口均使用监控平台 JWT，统一响应外壳为 `{ "code": 200, "message": "...", "data": ... }`。

## GET /api/monitor/reminders/filter-options

一次返回页面使用的提醒类别和具体类型。类别中的 `types` 为级联选择项；停用字典不会用于新筛选选项。企业选项不在此接口中全量返回。

```json
{"code":200,"data":{"categories":[{"code":"ACCOUNT_BALANCE","name":"账户资金","types":[{"code":"WALLET_BALANCE_NEGATIVE","name":"账户余额为负"}]}]}}
```

## GET /api/monitor/reminders/enterprise-options

根据企业名称或编码进行远程模糊搜索，只有用户输入关键词时调用，最多返回 20 条。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `keyword` | string | 是 | 企业名称或编码关键词，最长 100 个字符 |

```json
{"code":200,"data":[{"id":1,"name":"示例企业","code":"ENT0001"}]}
```

## GET /api/monitor/reminders

组合筛选提醒并分页返回。Query 参数如下：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `severity` | string | 否 | `NOTICE`、`WARNING`、`CRITICAL` |
| `categoryCode` | string | 否 | 业务类别编码 |
| `typeCodes` | string | 否 | 具体提醒类型编码，多个编码使用英文逗号分隔；选择完整大类时可省略 |
| `typeCode` | string | 否 | 兼容旧版客户端的单个提醒类型编码；新页面使用 `typeCodes` |
| `enterpriseId` | long | 否 | 所属企业 ID |
| `processStatus` | integer | 否 | `0` 待处理，`1` 已处理 |
| `pageNo` | integer | 否 | 默认 1 |
| `pageSize` | integer | 否 | 默认 10，最大 100 |

返回 `data` 包含 `list`、`pageNo`、`pageSize`、`total`。列表按紧急程度降序、最近触发时间降序排列；类型字典无法匹配时，`typeName` 回退为原始 `reminderType`。每条记录额外返回 `enterpriseNameSnapshot`、`enterpriseCode`、`enterpriseContactName` 和 `enterpriseContactPhone`，供所属企业列以不可点击文本展示企业名称、编码和联系方式。

## PATCH /api/monitor/reminders/{id}/processed

将当前待办标记为已处理。处理人取当前 JWT 中的监控用户，不接受客户端指定。
处理状态更新与 `monitor_system_log(event_code=REMINDER_PROCESSED)` 在同一事务内提交，日志保存企业、提醒标题、操作人、处理备注及处理前后快照。

```json
{"revision":2,"remark":"已电话联系企业管理员并确认充值安排"}
```

`revision` 必填。提醒在弹窗打开期间被升级、已由其他用户处理或版本发生变化时返回 `409`，前端应提示并刷新列表。提醒不存在返回 `404`，参数错误返回 `400`，未登录返回 `401`。

## PATCH /api/monitor/reminders/{id}/unprocessed

将已处理提醒恢复到待处理队列。接口会把当前处理时间、处理人和备注保存为最近一次处理快照，清空当前处理信息，
并写入 `monitor_system_log(event_code=REMINDER_REOPENED)`。请求必须携带页面所见版本号：

```json
{"revision":3}
```

提醒已是待处理状态或版本发生变化时返回 `409`；提醒不存在返回 `404`。

## 内部合并约束

`POST /internal/reminders/merge` 继续以 `enterpriseId + reminderKey` 合并。此次重构增加 `reminderType` 注册及启用校验；未知或停用类型返回 `400`。新增提醒类型的发布顺序必须是先执行字典迁移，再发布 SaaS 产生逻辑。
