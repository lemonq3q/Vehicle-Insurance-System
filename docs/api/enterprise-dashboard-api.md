# 企业经营仪表盘 API

## GET `/portal/dashboard/statistics`

作用：查询当前登录账号所在企业的本月经营概览。企业 ID 从门户认证上下文读取，不接受客户端传参。月度数据来源于已终算的企业每日统计表，因此默认统计截至昨日。

请求头：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `Authorization: Bearer <token>` 或 `token` | 是 | 门户登录令牌 |

请求参数：无。

成功响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "currentMonthProcessedWorkorders": 286,
    "previousMonthProcessedWorkorders": 242,
    "workorderComparison": { "direction": "UP", "rate": 18.2 },
    "renewalReminderCount": 42,
    "renewalDueThisWeek": 12,
    "currentMonthNewCustomers": 18,
    "previousMonthNewCustomers": 15,
    "customerComparison": { "direction": "UP", "rate": 20.0 },
    "currentMonthProfit": 12680.50,
    "previousMonthProfit": 10942.30,
    "profitComparison": { "direction": "UP", "rate": 15.9 },
    "updatedThrough": "2026-08-20"
  }
}
```

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `currentMonthProcessedWorkorders` | integer | 本月已终算自然日的完成工单数 |
| `previousMonthProcessedWorkorders` | integer | 上一完整自然月完成工单数 |
| `renewalReminderCount` | integer | 当前企业处于 30 天续保提醒窗口的工单总数 |
| `renewalDueThisWeek` | integer | 上述提醒中未来 7 天内需要处理的工单数 |
| `currentMonthNewCustomers` | integer | 本月新增有效下游商户数 |
| `previousMonthNewCustomers` | integer | 上一完整自然月新增有效下游商户数 |
| `currentMonthProfit` | decimal | 本月上游合并费用减下游合并费用 |
| `previousMonthProfit` | decimal | 上一完整自然月盈利 |
| `updatedThrough` | date | 月度统计最近包含的自然日 |
| `workorderComparison/customerComparison/profitComparison.direction` | string | `UP`、`DOWN`、`FLAT`、`NEW` |
| `workorderComparison/customerComparison/profitComparison.rate` | decimal/null | 环比绝对百分比；上月为零且本月非零时为 null |

错误：

| code | 条件 |
| --- | --- |
| 400 | 当前账号尚未加入企业 |
| 401 | 登录令牌缺失或失效 |
| 403 | 当前企业成员状态不可用 |
| 500 | 数据库查询异常 |
