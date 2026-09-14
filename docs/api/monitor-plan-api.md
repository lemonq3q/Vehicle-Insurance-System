# 监控平台套餐管理 API

## 1. 接口约定

- 基础路径：`/api/monitor/plans`（前端请求封装中的 `/plans` 会自动追加 `/api/monitor`）。
- 鉴权：监控平台登录 Token；操作人由后端从认证上下文读取，前端不得传入或覆盖。
- 统一响应：`{"code":200,"msg":null,"data":...}`。
- 套餐范围：仅维护普通正式套餐，当前不提供体验版、试用期或体验转正式等特殊能力。
- `workorderLimit` 表示企业订阅期内可免费存储的工单总数，允许为 `0`；企业开通套餐时会复制到订阅快照。
- 修改套餐模板不会追溯更新已经生效的 `saas_subscription` 权益快照。

## 2. 套餐数据结构

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 套餐主键 |
| code | string | 唯一业务编码，2~50 位大写字母、数字或下划线；创建后不可修改 |
| name | string | 套餐名称，最多 100 字符 |
| description | string | 套餐描述，最多 500 字符 |
| billingCycle | string | `DAY`、`MONTH` 或 `YEAR` |
| durationDays | integer | 套餐有效天数，大于 0 |
| memberLimit | integer | 企业成员上限，大于 0 |
| workorderLimit | integer | 免费工单存储额度，不小于 0 |
| price | decimal | 销售价格，不小于 0，最多两位小数 |
| listPrice | decimal | 划线价格；非 0 时不得低于销售价格 |
| status | integer | `0` 下架，`1` 上架 |
| sortOrder | integer | 展示顺序，不小于 0 |
| updatedAt | datetime | 最近更新时间 |
| reason | string | 仅写请求使用，必填，最多 500 字符；不作为套餐字段返回 |

## 3. 查询套餐列表

- 方法及路径：`GET /api/monitor/plans`
- 用途：套餐管理卡片页，返回所有未逻辑删除套餐，包括已下架套餐。
- 请求参数：无。
- 排序：`sortOrder ASC, id ASC`。

响应示例：

```json
{
  "code": 200,
  "msg": null,
  "data": [
    {
      "id": 50002,
      "code": "PRO_YEAR",
      "name": "专业版",
      "description": "适合稳定增长的保险服务团队",
      "billingCycle": "YEAR",
      "durationDays": 365,
      "memberLimit": 30,
      "workorderLimit": 5000,
      "price": 12800.00,
      "listPrice": 14800.00,
      "status": 1,
      "sortOrder": 20,
      "updatedAt": "2026-08-27T00:00:00"
    }
  ]
}
```

## 4. 查询套餐详情

- 方法及路径：`GET /api/monitor/plans/{id}`
- Path 参数：`id`，套餐主键，必填。
- 返回：第 2 节套餐数据结构。
- 常见错误：`404 套餐不存在`。

## 5. 新建套餐

- 方法及路径：`POST /api/monitor/plans`
- Body：第 2 节除 `id`、`updatedAt` 外全部字段，`reason` 必填。
- 数据变化：新增 `saas_plan`，同时写入 `monitor_system_log` 的 `PLAN_CREATE` 操作日志；二者共用事务。

请求示例：

```json
{
  "code": "STANDARD_YEAR",
  "name": "标准版",
  "description": "适合标准规模团队",
  "billingCycle": "YEAR",
  "durationDays": 365,
  "memberLimit": 20,
  "workorderLimit": 3000,
  "price": 6800,
  "listPrice": 7600,
  "status": 1,
  "sortOrder": 15,
  "reason": "补充标准档正式套餐"
}
```

常见错误：套餐编码格式错误、套餐编码已存在、额度越界、金额格式错误、缺少操作原因。

## 6. 修改套餐

- 方法及路径：`PUT /api/monitor/plans/{id}`
- Body：与新建相同；`code` 即使传入也不会修改数据库原编码。
- 数据变化：更新套餐模板并写入 `PLAN_UPDATE` 操作日志，日志保存修改前后快照。
- 重要约束：不更新已有企业订阅快照。

## 7. 上下架套餐

- 方法及路径：`PATCH /api/monitor/plans/{id}/status`
- Body 字段：`status` 必填，只能为 `0/1`；`reason` 必填。
- 数据变化：仅更新状态并写入 `PLAN_STATUS` 操作日志；重复提交相同状态幂等成功。

请求示例：

```json
{
  "status": 0,
  "reason": "产品策略调整，暂停新购"
}
```

## 8. 通用失败响应

```json
{
  "code": 400,
  "msg": "workorderLimit参数无效",
  "data": null
}
```

- `400`：请求字段、金额、额度、编码或原因无效。
- `401`：未登录或登录已失效。
- `404`：套餐不存在或已逻辑删除。
- `500`：数据库或审计日志写入失败；事务整体回滚。
