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
- 企业成员只读；禁止提供企业工单、车主、车辆、险种、处理人员等业务隐私明细接口。

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
| 409 | 40901 | 登录手机号或邮箱重复 |
| 409 | 40902 | 删除当前账号或最后一个启用管理员 |
| 409 | 40903 | 停用当前账号或最后一个启用管理员 |
| 409 | 40910 | 企业余额不足以执行扣减 |
| 409 | 40911 | 套餐状态已变化，请刷新后重试 |
| 500 | 50000 | 服务内部错误 |

所有人工写接口的 `reason` 必填，1–500 字。后端必须在同一业务事务中写 `monitor_system_log`，人工事件固定使用 `log_category=OPERATION`。

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

权限：已登录监控平台用户。query：`keyword`、`status`（1正常/2欠费限制/3停用）、`planId`、`expireDays`（7/30）、`pageNo`、`pageSize`（最大100）。

返回分页企业。列表字段：`id,code,name,status,memberCount,balance,planId,planName,subscriptionEndDate,monthUsage`。`monthUsage` 包含本月 `workorderCount/requestCount/ocrCount`。分页查询固定为一次计数和一次聚合列表 SQL，不使用逐企业查询。

### GET `/enterprises/subscription-plans`

返回套餐筛选和人工设置弹窗使用的 `{id,code,name,status,memberLimit,workorderLimit,durationDays}[]`，按套餐排序值排序。

### GET `/enterprises/options`

权限：两角色。query：`keyword`（企业名称或编码关键词）。空关键词返回空数组；非空关键词返回最多 20 个当前未删除企业的 `{id,name,code}[]`，按名称、ID 排序，供自定义对比搜索使用。

### GET `/enterprises/usage-top`

权限：两角色。query：`metric`（`workorder/request/ocr`，默认 `request`）、`range`（`7d/15d/30d/3m/6m/1y`，默认 `30d`）、`top`（1–20，默认 5）。按所选周期汇总指标并降序返回系统推荐企业：

```json
{"code":200,"message":"success","data":[{"id":1,"code":"ENT-202607-0012","name":"杭州星途汽车服务有限公司","metricValue":82600}],"requestId":"request-id"}
```

同值时按企业 ID 升序。前端默认勾选全部推荐结果，但允许用户取消部分企业；没有选中企业时不发起用量对比。

### GET `/enterprises/{id}`

权限：两角色。返回企业概览：

```json
{ "code":200,"message":"success","data":{"id":1,"code":"ENT-202607-0012","name":"杭州星途汽车服务有限公司","status":1,"contactName":"陈晓峰","contactPhone":"138****6821","source":"后台创建","remark":null,"memberCount":32,"memberLimit":50,"balance":28640.00,"planId":3,"planName":"企业版","subscriptionEndDate":"2027-03-16","lastActiveAt":"2026-07-25 18:16:00","createdAt":"2026-01-18 09:20:00","monthUsage":{"workorderCount":286,"requestCount":82600,"ocrCount":12400}},"requestId":"request-id" }
```

### GET `/enterprises/{id}/usage`

query `range`：`7d/15d/30d/3m/6m/1y`，默认 `30d`。7/15/30 天按日、3 个月按周、6 个月和 1 年按月。返回 `{range,interval,points}`，point 字段为 `label/workorderCount/requestCount/ocrCount`，无数据周期由后端补零。

### GET `/enterprises/usage-comparison`

query：`enterpriseIds`（逗号分隔，去重后 1–20 个）、`range`（`7d/15d/30d/3m/6m/1y`，默认 `30d`）。7/15/30 天按日、3 个月按周、6 个月和 1 年按月聚合并补零。返回：

```json
{ "code":200,"message":"success","data":{"range":"30d","interval":"DAY","labels":["2026-07-24","2026-07-25"],"enterprises":[{"enterpriseId":1,"enterpriseName":"杭州星途汽车服务有限公司","points":[{"label":"2026-07-24","workorderCount":180,"requestCount":8700,"ocrCount":1240}]}]},"requestId":"request-id" }
```

常见错误：企业 ID 格式错误、数量不在 1–20（400）；范围或指标不在白名单（400）；部分企业不存在或已删除（404）。该模块不提供导出接口。

### GET `/enterprises/{id}/members`

query：`keyword`、`roleName`、`status`、`pageNo/pageSize`。返回分页 `{id,enterpriseId,realName,username,phone,roleName,status,joinedAt,lastLoginAt}`。手机号按既有数据权限脱敏。无成员写接口。

### GET `/enterprises/{id}/finance-summary`

返回 `{balance,totalRecharge,totalSubscriptionExpense,monthTransactionCount}`。累计充值仅已支付充值订单；套餐支出读取已支付订阅订单；钱包余额读取 `saas_wallet`。

该接口读取真实 SaaS 财务表：`balance` 来自未删除钱包，`totalRecharge` 汇总 `status=2` 的充值订单，`totalSubscriptionExpense` 汇总 `status=2` 的订阅订单实际支付金额，`monthTransactionCount` 按 Asia/Shanghai 自然月统计钱包流水。企业不存在返回 404。

### GET `/enterprises/{id}/recharge-orders`

query：`businessNo`（充值订单号包含匹配）、`startDate/endDate`（创建日期闭区间，格式 `yyyy-MM-dd`）、`pageNo/pageSize`。返回分页字段：`id,enterpriseId,orderNo,amount,channel,status,paidAt,createdAt`。

### GET `/enterprises/{id}/subscription-orders`

query：`businessNo`（订阅订单号包含匹配）、`startDate/endDate`（创建日期闭区间，格式 `yyyy-MM-dd`）、`pageNo/pageSize`。返回分页字段：`id,enterpriseId,orderNo,planName,amount,status,startedAt,endedAt,createdAt`；套餐名称优先来自订单快照，不使用当前套餐配置覆盖历史。

### GET `/enterprises/{id}/wallet-transactions`

query：`businessNo`（钱包流水号包含匹配）、`startDate/endDate`（发生日期闭区间，格式 `yyyy-MM-dd`）、`pageNo/pageSize`。返回分页字段：`id,enterpriseId,transactionNo,type,amount,balanceAfter,referenceNo,remark,createdAt`。

三个财务分页接口均按 `created_at DESC,id DESC` 排序。开始日期晚于结束日期、日期格式非法或业务编号超过 64 个字符时返回 400；企业不存在时返回 404。

### POST `/enterprises/{id}/balance-adjustments`

请求：`{"amount":1000.00,"reason":"线下到账补录"}`。`amount` 非零，最多2位小数。返回 `{"balance":29640.00}`。

事务：`SELECT ... FOR UPDATE` 钱包 → 校验扣减后余额非负 → 写 `saas_wallet_transaction(type=ADJUST)` → 更新钱包 → 写审计日志 → 提交。

正金额写 `direction=IN`，负金额写 `direction=OUT`，流水 `amount` 始终保存绝对值并生成全局业务格式的 `TX` 编号。原因必填且不超过 500 字；钱包未初始化或扣减后余额为负返回 409，金额为零、格式或精度非法以及调整后超出 `DECIMAL(12,2)` 上限返回 400。审计事件代码为 `BALANCE_ADJUST`，保存调整前余额、调整金额、调整后余额和流水编号。

### PUT `/enterprises/{id}/subscription`

请求：`{"planId":3,"endDate":"2027-07-25","reason":"商务赠送一年企业版"}`。套餐必须存在且上架，到期日必须晚于当天。返回更新后的企业概览。

后台设置套餐不伪造支付成功订单；在同一事务内更新每企业唯一的当前订阅状态，并写入 `monitor_system_log(event_code=SUBSCRIPTION_SET)`。摘要为“开通了xxx套餐，到期时间为xxx”，同时保存订阅变更前后快照。

### DELETE `/enterprises/{id}/subscription`

body：`{"reason":"客户确认终止服务"}`。当前定义为立即失效，订阅改为已取消并清零权益，保留历史订单和套餐标识；返回 `data:null`。同事务写入 `monitor_system_log(event_code=SUBSCRIPTION_CANCEL)`。

## 4. 套餐管理

套餐结构：`id,code,name,description,billingCycle(YEAR/MONTH/DAY),durationDays,price,listPrice,memberLimit,workorderLimit,sortOrder,status,updatedAt`。`workorderLimit` 是企业订阅期内可免费存储的工单总数，允许为 0；开通订阅时复制到订阅快照，后续修改套餐模板不追溯修改已生效订阅。当前不实现体验版或试用期特殊逻辑，完整契约与示例见 [monitor-plan-api.md](./monitor-plan-api.md)。

### GET `/plans`

权限：两角色。返回全部未删除套餐，按 `sortOrder,id` 排序。

### GET `/plans/{id}`

权限：两角色。返回单个套餐；不存在返回 40400。

### POST `/plans`

权限：已登录监控平台用户。请求为套餐结构去除 `id/updatedAt` 并增加必填 `reason`。`code` 必须符合 `^[A-Z0-9_]{2,50}$` 且唯一；金额非负且最多两位小数；`durationDays/memberLimit` 大于 0；`workorderLimit/sortOrder` 不小于 0。返回新套餐，并同事务写入 `PLAN_CREATE` 系统日志。

### PUT `/plans/{id}`

权限：已登录监控平台用户。请求同上，但 `code` 不允许修改。历史订单和已有订阅继续使用快照。返回更新后套餐，并同事务写入 `PLAN_UPDATE` 系统日志。

### PATCH `/plans/{id}/status`

请求：`{"status":0,"reason":"产品策略调整"}`。下架只禁止新购，不终止已有订阅。返回套餐；状态发生变化时同事务写入 `PLAN_STATUS` 系统日志。

## 5. 平台用户管理

以下接口仅 `ADMIN`。`username` 固定承载 11 位登录手机号，不再维护重复的 `phone` 字段。用户结构：`id,username,realName,email,roleCode,roleName,status,lastLoginAt,passwordChangedAt,createdByName,createdAt,current`。

本节接口已接入真实 `monitor_user`、`monitor_user_role` 和 `monitor_role` 表。创建、修改、启停、重置密码及删除操作都会写入 `monitor_system_log`；停用、删除、重置密码或改变登录手机号/角色时会撤销目标账号的 Redis 登录会话。

### GET `/users`

query：`keyword`、`roleCode(ADMIN/CUSTOMER_SERVICE)`、`status`、`pageNo/pageSize`。返回分页用户，不返回密码摘要和已删除用户。

### POST `/users`

请求：

```json
{"username":"13800138000","realName":"张悦","email":"zhangyue@example.com","roleCode":"CUSTOMER_SERVICE","reason":"新增客服入职"}
```

账号只能由管理员创建，`username` 必须是未被使用的 11 位手机号。后端生成符合密码策略的一次性初始密码，只在本次响应返回：`{"user":{...},"initialPassword":"Xm@123456"}`；持久化 BCrypt 摘要。

### GET `/users/{id}`

返回用户详情；`current=true` 表示当前登录账号，前端禁止停用和删除，但后端仍必须校验。

### PUT `/users/{id}`

请求：`username,realName,email,roleCode,status,reason`。`username` 必须是 11 位登录手机号；不能停用当前账号；不能移除最后一个启用 ADMIN。返回更新后用户。

### PATCH `/users/{id}/status`

请求：`{"status":0,"reason":"员工离职"}`。执行最后管理员及当前账号保护，返回更新后用户。

### POST `/users/{id}/reset-password`

请求：`{"reason":"用户忘记密码"}`。响应：`{"initialPassword":"Xm@654321"}`，密码只返回一次并更新 `password_changed_at`。

### DELETE `/users/{id}`

body：`{"reason":"测试账号清理"}`。软删除，不能删除当前账号或最后一个启用 ADMIN。返回 `data:null`。

## 6. Mock 与联调切换

- Mock 数据：`src/mock/database.js`；路由行为：`src/mock/adapter.js`。
- mock 与真实后端按接口封装中的 `useMock` 显式标记分流；环境文件仅配置接口基址，不控制功能开发阶段。
- 阶段 4 将环境变量改为 `false` 并设置真实 `VUE_APP_API_BASE_URL`，页面和 API 函数无需修改。
- 后端每完成一个接口，应使用本文示例做契约测试，尤其检查金额精度、空值、分页边界、角色权限和审计日志事务。
