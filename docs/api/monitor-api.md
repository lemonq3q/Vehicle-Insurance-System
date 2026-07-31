# 运营监控平台 API 契约

> 版本：v1.0（阶段 2）  
> 前端实现：`monitor-frontend/src/api/monitor.js`  
> Mock 实现：`monitor-frontend/src/mock/adapter.js`  
> 后端基址：`/api/monitor`

## 1. 通用约定

### 1.1 认证与权限

- 除登录接口外统一使用 `Authorization: Bearer <token>`。
- 请求头携带 `X-Request-Id`，后端应原样返回并写入审计日志。
- `ADMIN`：全部接口；`CUSTOMER_SERVICE`：除 `/users/**` 外全部接口。
- 企业员工只读；禁止提供企业工单、车主、车辆、险种、处理人员等业务隐私明细接口。

### 1.2 响应外壳

```json
{ "code": 200, "message": "success", "data": {}, "requestId": "web-..." }
```

分页结构：

```json
{ "list": [], "pageNo": 1, "pageSize": 10, "total": 0 }
```

日期时间为 `yyyy-MM-dd HH:mm:ss`，日期为 `yyyy-MM-dd`，金额为 JSON number（元），计数为非负整数。列表默认按 `created_at DESC, id DESC`；统计点按 `stat_date ASC`。

### 1.3 通用错误码

| HTTP | code | 场景 |
|---|---:|---|
| 400 | 40000 | 参数校验失败，`data.fieldErrors` 返回字段错误 |
| 401 | 40100 | 未登录或令牌失效 |
| 403 | 40300 | 无角色权限 |
| 404 | 40400 | 资源不存在或已删除 |
| 409 | 40901 | 登录账号、手机号或邮箱重复 |
| 409 | 40902 | 删除当前账号或最后一个启用管理员 |
| 409 | 40903 | 停用当前账号或最后一个启用管理员 |
| 409 | 40910 | 企业余额不足以执行扣减 |
| 409 | 40911 | 套餐状态已变化，请刷新后重试 |
| 500 | 50000 | 服务内部错误 |

所有写接口的 `reason` 必填，1–500 字。后端必须在同一业务事务中写 `monitor_operation_log`。

## 2. 仪表盘

### GET `/dashboard`

权限：`ADMIN`、`CUSTOMER_SERVICE`。参数：query `days`，可选值 7/14/30，默认 14。

```json
{
  "code": 200, "message": "success", "requestId": "mock-1",
  "data": {
    "enterpriseCount": 286, "userCount": 1842, "monthRechargeAmount": 428600.00,
    "today": { "statDate": "2026-07-25", "workorderCount": 5120, "requestCount": 96284, "ocrCount": 12736 },
    "trends": [{ "statDate": "2026-07-24", "workorderCount": 4980, "requestCount": 95020, "ocrCount": 12502 }],
    "ranking": [{ "enterpriseId": 1, "enterpriseName": "杭州星途汽车服务有限公司", "requestCount": 8942 }],
    "updatedAt": "2026-07-25 18:18:00"
  }
}
```

`enterpriseCount/userCount` 读取 Redis 启动及 04:00 快照；月流水只统计已支付充值；今日值允许合并 Redis 实时计数，历史趋势读取汇总表。

## 3. 企业管理

### GET `/enterprises`

权限：两角色。query：`keyword`、`status`（0停用/1正常/2限制）、`planId`、`expireDays`（7/30）、`pageNo`、`pageSize`（最大100）。

返回分页企业。列表字段：`id,code,name,status,contactName,contactPhone,memberCount,memberLimit,balance,planId,planName,subscriptionEndDate,lastActiveAt,createdAt,todayUsage`。`todayUsage` 包含 `statDate/workorderCount/requestCount/ocrCount`。

### GET `/enterprises/options`

权限：两角色。返回当前未删除企业的 `{id,name,code}[]`，供对比多选使用，按名称排序。

### GET `/enterprises/{id}`

权限：两角色。返回企业概览：

```json
{ "code":200,"message":"success","data":{"id":1,"code":"ENT-202607-0012","name":"杭州星途汽车服务有限公司","status":1,"contactName":"陈晓峰","contactPhone":"138****6821","source":"后台创建","remark":"重点企业客户","memberCount":32,"memberLimit":50,"balance":28640.00,"planId":3,"planName":"企业版","subscriptionEndDate":"2027-03-16","lastActiveAt":"2026-07-25 18:16:00","createdAt":"2026-01-18 09:20:00"},"requestId":"mock-1" }
```

### GET `/enterprises/{id}/usage`

query `days`：7/14/30。返回 `{enterpriseId,enterpriseName,points}`，point 字段为 `statDate/workorderCount/requestCount/ocrCount`。只能返回汇总计数。

### GET `/enterprises/usage-comparison`

query：`enterpriseIds`（逗号分隔，2–8个且去重）、`days`（7/14/30）。返回：

```json
{ "code":200,"message":"success","data":{"dates":["2026-07-24","2026-07-25"],"enterprises":[{"enterpriseId":1,"enterpriseName":"杭州星途汽车服务有限公司","points":[{"statDate":"2026-07-24","workorderCount":180,"requestCount":8700,"ocrCount":1240}]}]},"requestId":"mock-1" }
```

### GET `/enterprises/{id}/members`

query：`keyword`、`roleName`、`status`、`pageNo/pageSize`。返回分页 `{id,enterpriseId,realName,username,phone,roleName,status,joinedAt,lastLoginAt}`。手机号按既有数据权限脱敏。无成员写接口。

### GET `/enterprises/{id}/finance-summary`

返回 `{balance,totalRecharge,totalSubscriptionExpense,monthTransactionCount}`。累计充值仅已支付充值订单；套餐支出读取已支付订阅订单；钱包余额读取 `saas_wallet`。

### GET `/enterprises/{id}/recharge-orders`

query：`pageNo/pageSize`，可扩展 `status/startDate/endDate`。返回分页字段：`id,enterpriseId,orderNo,amount,channel,status,paidAt,createdAt`。

### GET `/enterprises/{id}/subscription-orders`

返回分页字段：`id,enterpriseId,orderNo,planName,amount,status,startedAt,endedAt,createdAt`；套餐名称和金额来自订单快照，不回读当前套餐覆盖历史。

### GET `/enterprises/{id}/wallet-transactions`

返回分页字段：`id,enterpriseId,transactionNo,type,amount,balanceAfter,referenceNo,remark,createdAt`。

### POST `/enterprises/{id}/balance-adjustments`

请求：`{"amount":1000.00,"reason":"线下到账补录"}`。`amount` 非零，最多2位小数。返回 `{"balance":29640.00}`。

事务：`SELECT ... FOR UPDATE` 钱包 → 校验扣减后余额非负 → 写 `saas_wallet_transaction(type=ADJUST)` → 更新钱包 → 写审计日志 → 提交。

### PUT `/enterprises/{id}/subscription`

请求：`{"planId":3,"endDate":"2027-07-25","reason":"商务赠送一年企业版"}`。套餐必须存在且上架，到期日不早于统计日。返回更新后的企业概览。

默认决策：后台设置套餐不伪造支付成功订单；写订阅和审计日志。若后续要求留订单痕迹，应新增明确的 `ADMIN_ADJUST` 零元订单类型。

### DELETE `/enterprises/{id}/subscription`

body：`{"reason":"客户确认终止服务"}`。当前阶段定义为立即失效，保留历史订单；返回 `data:null`。如需“周期末取消”应另加 `cancelAtPeriodEnd`，不可静默改变本接口语义。

## 4. 套餐管理

套餐结构：`id,code,name,description,billingCycle(YEAR/MONTH/DAY),durationDays,price,listPrice,memberLimit,sortOrder,status,updatedAt`。

### GET `/plans`

权限：两角色。返回全部未删除套餐，按 `sortOrder,id` 排序。

### GET `/plans/{id}`

权限：两角色。返回单个套餐；不存在返回 40400。

### POST `/plans`

权限：两角色。请求为套餐结构去除 `id/updatedAt` 并增加 `reason`。`code` 必须符合 `^[A-Z0-9_]{2,32}$` 且唯一；价格非负；`durationDays/memberLimit` 大于0。返回新套餐。

### PUT `/plans/{id}`

权限：两角色。请求同上，但 `code` 不允许修改。历史订单继续使用快照。返回更新后套餐。

### PATCH `/plans/{id}/status`

请求：`{"status":0,"reason":"产品策略调整"}`。下架只禁止新购，不终止已有订阅。返回套餐。

## 5. 平台用户管理

以下接口仅 `ADMIN`。用户结构：`id,username,realName,phone,email,roleCode,roleName,status,lastLoginAt,passwordChangedAt,createdByName,createdAt,current`。

### GET `/users`

query：`keyword`、`roleCode(ADMIN/CUSTOMER_SERVICE)`、`status`、`pageNo/pageSize`。返回分页用户，不返回密码摘要和已删除用户。

### POST `/users`

请求：

```json
{"username":"service01","realName":"张悦","phone":"13800138000","email":"zhangyue@example.com","roleCode":"CUSTOMER_SERVICE","reason":"新增客服入职"}
```

账号只能由管理员创建。后端生成符合密码策略的一次性初始密码，只在本次响应返回：`{"user":{...},"initialPassword":"Xm@123456"}`；持久化 BCrypt 摘要。

### GET `/users/{id}`

返回用户详情；`current=true` 表示当前登录账号，前端禁止停用和删除，但后端仍必须校验。

### PUT `/users/{id}`

请求：`username,realName,phone,email,roleCode,status,reason`。不能停用当前账号；不能移除最后一个启用 ADMIN。返回更新后用户。

### PATCH `/users/{id}/status`

请求：`{"status":0,"reason":"员工离职"}`。执行最后管理员及当前账号保护，返回更新后用户。

### POST `/users/{id}/reset-password`

请求：`{"reason":"用户忘记密码"}`。响应：`{"initialPassword":"Xm@654321"}`，密码只返回一次并更新 `password_changed_at`。

### DELETE `/users/{id}`

body：`{"reason":"测试账号清理"}`。软删除，不能删除当前账号或最后一个启用 ADMIN。返回 `data:null`。

## 6. Mock 与联调切换

- Mock 数据：`src/mock/database.js`；路由行为：`src/mock/adapter.js`。
- `.env.development/.env.production` 当前均为 `VUE_APP_USE_MOCK=true`。
- 阶段 4 将环境变量改为 `false` 并设置真实 `VUE_APP_API_BASE_URL`，页面和 API 函数无需修改。
- 后端每完成一个接口，应使用本文示例做契约测试，尤其检查金额精度、空值、分页边界、角色权限和审计日志事务。
