# 监控平台审计日志 API

## GET `/api/monitor/system-logs`

用途：管理员在审计日志页面按类别、严重等级和时间范围查询 `monitor_system_log`。

权限：仅监控平台 `ADMIN`。未登录返回 401，非管理员返回 403。

### Query 参数

| 字段 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `category` | string | 否 | `OPERATION/MAINTENANCE/SYSTEM/SECURITY/BUSINESS` | `OPERATION` |
| `severity` | string | 否 | `DEBUG/INFO/WARN/ERROR/CRITICAL` | `INFO` |
| `startDate` | string | 否 | 开始自然日，`yyyy-MM-dd`，包含当日 | `2026-08-01` |
| `endDate` | string | 否 | 结束自然日，`yyyy-MM-dd`，包含当日 | `2026-08-26` |
| `pageNo` | integer | 否 | 页码，默认 1 | `1` |
| `pageSize` | integer | 否 | 每页条数，默认 10，最大 100 | `10` |

开始日期晚于结束日期、枚举非法或日期格式错误时返回业务码 400。后端使用 `[startDate 00:00,endDate+1 00:00)` 范围，不对 `occurred_at` 列包裹日期函数。

### 返回示例

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "list": [
      {
        "id": 1,
        "logCategory": "OPERATION",
        "severity": "INFO",
        "eventCode": "SUBSCRIPTION_SET",
        "eventName": "人工设置企业套餐",
        "sourceSystem": "MONITOR",
        "sourceModule": "enterprise",
        "resultStatus": "SUCCESS",
        "operatorType": "MONITOR_USER",
        "operatorId": 1,
        "operatorNameSnapshot": "lemon",
        "enterpriseId": 3,
        "enterpriseNameSnapshot": "测试企业",
        "targetType": "SUBSCRIPTION",
        "targetId": "3",
        "targetNameSnapshot": "专业版",
        "operationReason": "商务赠送",
        "summary": "开通了专业版套餐，到期时间为2027-08-26",
        "beforeJson": null,
        "afterJson": "{\"status\":1,\"planId\":50002}",
        "errorCode": null,
        "errorMessage": null,
        "requestId": "web-1787757432-a1b2",
        "ipAddress": "127.0.0.1",
        "occurredAt": "2026-08-26T23:40:00.000"
      }
    ],
    "pageNo": 1,
    "pageSize": 10,
    "total": 1
  }
}
```

### 查询与排序

- 数量和列表各执行一条 SQL，不执行逐行追加查询。
- 按 `occurred_at DESC,id DESC` 排序，相同毫秒的日志仍具有稳定顺序。
- JSON 快照原样返回，前端仅在用户点击“查看”后格式化展示。

### Mock 说明

该页面已接入真实后端，`systemLogApi.list` 不使用 `useMock`。
