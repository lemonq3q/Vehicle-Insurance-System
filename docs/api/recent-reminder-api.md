# 近期提醒 API

## 门户查询全部生效提醒

### `GET /portal/reminders/recent`

查询当前登录成员所属企业全部 `is_active=1` 提醒，不再限制一个月，也不依据旧 `expires_at` 隐藏。每日维护先复核失效，再新增、恢复或升降阶段，最后同步监控，保留历史记录；无独立5分钟扫描。服务端固定按严重程度 `CRITICAL > WARNING > NOTICE` 降序，同级按 `lastTriggeredAt DESC, id DESC` 排序；客户端不传时间和排序参数。

成功响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "id": 110002,
      "reminderType": "SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW",
      "reminderStage": "1D",
      "severity": "WARNING",
      "title": "套餐将在1天后到期",
      "content": "您的“专业版”套餐将于 2026-08-18 23:59 到期，当前未开启自动续费，请及时续费。",
      "occurredAt": "2026-08-17 04:00:00",
      "revision": 2
    }
  ]
}
```

| 失败状态 | 条件 |
| --- | --- |
| `400` | 当前账号尚未加入企业 |
| `401` | 未登录或令牌失效 |

## 监控系统内部合并提醒

### `POST /internal/reminders/merge`

由 SaaS 维护任务及周期复核调用。Header `X-Maintenance-Secret` 必填并必须与监控系统配置一致。新版按 `lifecycleVersion` 接收完整生效状态快照；失效保留人工处理记录，风险升级或重新生效恢复待处理。字段和补偿规则参见 [提醒生命周期契约](reminder-lifecycle-api.md)。

请求示例：

```json
{
  "enterpriseId": 20001,
  "enterpriseName": "杭州小马车险服务有限公司",
  "reminderType": "SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW",
  "reminderKey": "SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW:subscription-70001:end-2026-08-18T23:59:59",
  "reminderStage": "1D",
  "stageLevel": 20,
  "severity": "WARNING",
  "title": "【待续费】杭州小马车险服务有限公司套餐将在1天后到期",
  "content": "企业套餐将在2026-08-18 23:59到期，目前未开启自动续费。",
  "businessDataJson": "{\"subscriptionId\":70001}",
  "triggeredAt": "2026-08-17T04:00:00"
}
```

成功时 `data.action` 为 `CREATED`、`UPDATED` 或 `IGNORED`。重复或低阶段请求返回 `IGNORED`，仍使用 HTTP 200，便于维护任务幂等重试。

| 失败状态 | 条件 |
| --- | --- |
| `400` | 必填字段缺失或阶段等级小于零 |
| `403` | 内部密钥错误 |
| `500` | 数据库写入失败，SaaS 维护任务应失败并允许协调器重试 |
