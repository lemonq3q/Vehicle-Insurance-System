# SaaS 门户前端接口文档

本文档描述 `systemportal` 前端当前使用的接口契约。业务接口由 `systemportal/src/api/portal.js` 统一导出，请求通过 `systemportal/src/api/request.js` 中的 Axios 实例管理，并已对接 `backend/saas` 提供的真实接口。

宣传官网与仪表盘仍为静态样板；登录注册、企业信息、企业成员、邀请码、订阅服务、充值订单、订阅订单、资金明细、用户中心均调用真实 API。旧 mock 文件仅保留为历史联调样例，不再由请求层加载。

## 统一约定

### 统一响应外壳

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {}
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| code | number | 是 | 200 表示成功，400+ 表示业务或权限错误 |
| msg | string | 是 | 响应说明 |
| data | any | 否 | 业务数据 |

### Axios 请求与鉴权约定

| 配置项 | 当前约定 |
| --- | --- |
| API 根地址 | `VUE_APP_API_BASE_URL`，未配置时使用同源地址 |
| 本地开发代理 | `/portal/**` 默认转发至 `http://127.0.0.1:8081`，可通过 `PORTAL_API_TARGET` 覆盖 |
| 请求超时 | 60000 ms |
| 登录态请求头 | `token: <token>`，同时发送 `Authorization: Bearer <token>` |
| token 刷新响应头 | `new-token` |

登录采用单点会话：同一用户再次登录后，服务端会覆盖原 Redis 会话，之前签发的 token 立即失效。
| 登录态有效期 | 本地滑动续期 24 小时 |

登录、注册、发送短信验证码、找回密码接口不附加 token。业务响应 `code >= 400` 时 Axios 响应拦截器会转换为 Promise 异常；`code = 401` 或 HTTP 401 时同时清理登录态并返回登录页。

### 分页响应结构

分页接口的 `data` 对齐后端现有 `TableData`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 1,
    "table": []
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| total | number | 是 | 总条数 |
| table | array | 是 | 当前页数据 |

### 通用错误

| code | msg | 触发条件 |
| --- | --- | --- |
| 400 | 请求参数错误 | 必填字段为空、角色变更不合法、金额不合法 |
| 401 | 未登录或登录已过期 | token 缺失或无效 |
| 403 | 无操作权限 | 当前企业角色不允许执行该动作 |
| 404 | 数据不存在 | 邀请码、成员、套餐等不存在 |

## 1. 登录注册

### 1.1 登录

`POST /portal/auth/login`

作用：企业用户登录门户，返回 token、当前用户、可选企业和当前企业。

Body：

| 字段 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| username | string | 是 | 登录账号，可为手机号 | 13800000001 |
| password | string | 是 | 密码，至少 8 位 | Portal@123 |

Response data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| token | string | 登录 token |
| user | TenantUser | 当前用户 |
| enterprises | TenantEnterprise[] | 可进入的企业 |
| currentEnterpriseId | number \| null | 当前企业 ID |
| currentEnterprise | TenantEnterprise \| null | 当前企业完整信息 |
| currentMember | TenantMember \| null | 当前用户在当前企业中的成员关系与角色 |

Mock 示例：

```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "token": "mock-portal-token",
    "user": { "id": 10001, "username": "linxf", "phone": "13800000001", "realName": "林晓峰" },
    "enterprises": [{ "id": 20001, "name": "杭州小马车险服务有限公司", "code": "ENT-HZ-202607" }],
    "currentEnterpriseId": 20001,
    "currentEnterprise": { "id": 20001, "name": "杭州小马车险服务有限公司", "code": "ENT-HZ-202607" },
    "currentMember": { "id": 30001, "enterpriseId": 20001, "roleCode": "OWNER", "status": 1 }
  }
}
```

错误情况：

| code | msg | 触发条件 |
| --- | --- | --- |
| 400 | 用户名或密码错误 | 登录账号不存在或密码校验失败 |
| 403 | 账号未启用 | 用户账号状态为停用 |

### 1.2 发送短信验证码

`POST /portal/auth/sms-code`

作用：为注册或找回密码发送手机号验证码。同一手机号再次发送前应等待 `retryAfterSeconds`。

Body：

| 字段 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| phone | string | 是 | 11 位手机号 | 13800000005 |
| scene | string | 是 | `REGISTER` 注册，`RESET_PASSWORD` 找回密码 | REGISTER |

Response data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| expiresInSeconds | number | 验证码有效秒数 |
| retryAfterSeconds | number | 再次发送前等待秒数 |

Mock 示例：

```json
{
  "code": 200,
  "msg": "验证码已发送，mock 验证码为 123456",
  "data": {
    "expiresInSeconds": 300,
    "retryAfterSeconds": 60
  }
}
```

常见错误：手机号格式错误返回 `400`；验证码场景不支持返回 `400`；发送过于频繁时后端应返回 `429`。

### 1.3 注册

`POST /portal/auth/register`

作用：注册企业用户账号，默认不创建企业身份。

Body：

| 字段 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| phone | string | 是 | 手机号 | 13800000005 |
| smsCode | string | 是 | 6 位短信验证码 | 123456 |
| realName | string | 是 | 姓名 | 李明 |
| password | string | 是 | 密码 | password123 |

Response data：`TenantUser`

Mock 示例：

```json
{
  "code": 200,
  "msg": "注册成功",
  "data": {
    "id": 10099,
    "username": "13800000005",
    "phone": "13800000005",
    "realName": "李明",
    "status": 1
  }
}
```

常见错误：手机号格式错误、验证码错误或失效、手机号已注册时返回 `400`。

### 1.4 找回密码

`POST /portal/auth/forget-password`

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| phone | string | 是 | 绑定手机号 |
| smsCode | string | 是 | 6 位短信验证码 |
| password | string | 是 | 新密码 |

Response data：`true`

Mock 示例：

```json
{
  "code": 200,
  "msg": "密码已重置",
  "data": true
}
```

常见错误：手机号不存在、验证码错误或失效、新密码不符合安全要求时返回 `400`。

## 2. 账户上下文

### 2.1 查询当前账户上下文

`GET /portal/account/context`

作用：进入门户后获取当前用户、企业列表、当前企业、当前成员角色。

Response data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| user | TenantUser | 当前登录用户 |
| enterprises | TenantEnterprise[] | 用户加入的企业列表 |
| currentEnterpriseId | number | 当前企业 ID，无企业时为 null |
| currentEnterprise | TenantEnterprise | 当前企业，无企业时为 null |
| currentMember | TenantMember | 当前企业成员关系，无企业时为 null |

## 3. 企业管理

### 3.1 查询当前企业信息

`GET /portal/enterprise/current`

作用：企业信息页展示当前企业、当前用户在企业内的角色、钱包和订阅摘要。

Response data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| enterprise | TenantEnterprise | 当前企业 |
| member | TenantMember | 当前成员身份 |
| wallet | SaasWallet | 企业钱包 |
| subscription | SaasSubscription | 企业唯一的当前订阅状态；未订阅时 `status=0`、额度为 0，生效时包含 plan |

### 3.2 创建企业

`POST /portal/enterprise`

作用：无企业用户创建企业，创建后用户成为 OWNER。

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 企业名称 |
| contactName | string | 是 | 联系人 |
| contactPhone | string | 是 | 联系电话 |

Response data：`TenantEnterprise`

企业编码始终由后端的独立编码生成器创建，前端传入的 `code` 字段不会被采用。

### 3.3 更新当前企业

`PUT /portal/enterprise/current`

权限：仅 OWNER。ADMIN、ISSUER 调用时返回 `403`。

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 企业名称 |
| contactName | string | 是 | 联系人 |
| contactPhone | string | 是 | 联系电话 |

企业编码 `code` 由系统创建企业时生成，不允许通过更新接口修改。

Response data：`TenantEnterprise`

### 3.4 邀请码加入企业

`POST /portal/enterprise/join-by-invite`

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| code | string | 是 | 企业邀请码 |

Response data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| enterpriseId | number | 加入的企业 ID |
| roleCode | string | 默认 ISSUER |

## 4. 企业人员和邀请码

### 4.1 查询邀请码列表

`GET /portal/enterprise/invite-codes`

权限：OWNER、ADMIN。

Query：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | number | 否 | 页码，默认 1 |
| pageSize | number | 否 | 每页条数，默认 5 |

Response data：分页 `TenantInviteCode`

### 4.2 创建邀请码

`POST /portal/enterprise/invite-codes`

权限：OWNER、ADMIN。

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| maxUseCount | number | 否 | 最大使用次数，null 表示不限 |
| expiresAt | string | 是 | 过期日期，格式 `yyyy-MM-dd`；服务端按当日 `23:59:59` 保存 |

Response data：`TenantInviteCode`

### 4.3 删除邀请码

`DELETE /portal/enterprise/invite-codes`

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | number | 是 | 邀请码 ID |

Response data：`true`

### 4.4 查询企业成员

`GET /portal/enterprise/members`

权限：所有企业成员可查看。

Query：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | number | 否 | 页码 |
| pageSize | number | 否 | 每页条数 |
| keyword | string | 否 | 姓名、手机号、账号模糊搜索 |
| roleCode | string | 否 | OWNER、ADMIN、ISSUER |
| status | number | 否 | 0 停用，1 启用，2 待审核；已退出成员不再出现在成员关系表中 |

Response data：分页 `TenantMember`

### 4.5 修改成员角色

`PUT /portal/enterprise/members/role`

权限：OWNER、ADMIN。OWNER 角色不能通过该接口设置或取消。

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| memberId | number | 是 | 成员 ID |
| roleCode | string | 是 | ADMIN 或 ISSUER |

Response data：`TenantMember`

### 4.6 修改成员启停状态

`PUT /portal/enterprise/members/status`

作用：启用或停用企业成员。邀请成员不校验套餐人数；只有把成员状态修改为启用时，后端才校验当前套餐可用人数。

权限：OWNER、ADMIN。企业拥有者不能被停用。

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| memberId | number | 是 | 企业成员 ID |
| status | number | 是 | 0 停用，1 启用 |

Response data：`TenantMember`

人数校验规则：

1. 套餐占用人数只统计当前企业 `tenant_member.status = 1 AND deleted = 0` 的成员。
2. 停用成员不受套餐人数限制。
3. 启用成员时由后端在事务中重新统计人数；前端不预判、不缓存剩余席位。
4. 达到套餐上限时不更新成员状态，返回 `409`。

余额不足示例之外，本接口常见错误如下：

| code | msg | 触发条件 |
| --- | --- | --- |
| 400 | 企业拥有者不能被停用 | memberId 指向 OWNER |
| 400 | 成员状态参数不正确 | status 不是 0 或 1 |
| 404 | 成员不存在 | 成员不存在或不属于当前企业 |
| 409 | 企业当前未开通任何套餐，暂时无法启用成员 | 当前订阅人数额度为 0 |
| 409 | 当前套餐最多启用 N 名成员，请先升级套餐或停用其他成员 | 启用后会超过套餐人数上限 |

Mock response：

```json
{
  "code": 200,
  "msg": "成员已停用",
  "data": {
    "id": 30004,
    "enterpriseId": 20001,
    "userId": 10004,
    "roleCode": "ISSUER",
    "status": 0
  }
}
```

### 4.7 转让企业拥有者

`POST /portal/enterprise/owner-transfer`

权限：OWNER。

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| toMemberId | number | 是 | 同企业有效成员 ID |

Response data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| enterpriseId | number | 企业 ID |
| fromUserId | number | 原拥有者用户 ID |
| toUserId | number | 新拥有者用户 ID |
| transferredAt | string | 转让时间 |

转让事务会先锁定企业记录，再锁定目标成员。成员踢出接口使用相同锁顺序，避免目标成员在转让为拥有者的同时被移出企业。

### 4.8 查询企业人员变动记录

`GET /portal/enterprise/member-change-logs`

作用：分页查询当前企业成员加入、主动退出、踢出、角色修改和拥有者转让记录，所有企业成员可查看。

Query：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | number | 否 | 页码，默认 1 |
| pageSize | number | 否 | 每页条数，默认 5，最大 100 |
| eventType | string | 否 | JOIN、EXIT、KICK、ROLE_CHANGE、OWNER_TRANSFER |

Response data：分页 `TenantMemberChangeLog`，包含操作人和目标成员姓名快照、变更前后角色、发生时间及备注。

### 4.9 退出企业

`POST /portal/enterprise/members/exit`

权限：ADMIN、ISSUER。OWNER 不可退出企业。

退出事务成功提交后，SaaS 会调用车险内部用户登出接口，使该成员此前保存的车险 token 因 Redis 会话被删除而立即失效。管理员停用或移除成员时执行相同处理。

Response data：`true`

### 4.10 踢出企业成员

`POST /portal/enterprise/members/remove`

作用：软删除指定成员与企业的绑定关系，使其立即从企业成员列表消失。企业拥有者不能被踢出，操作者不能通过此接口踢出自己。

权限：OWNER、ADMIN。

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| memberId | number | 是 | 需要移出企业的成员 ID |

Response data：`true`

并发规则：踢出、拥有者转让、成员启停、角色修改和主动退出均在事务中依次锁定企业和目标成员。先完成踢出的成员无法再被转让；先完成转让的成员会成为 OWNER，后续踢出、停用或退出请求将被拒绝。退出和踢出都会写入企业人员变动记录。

## 5. 财务中心

### 5.1 财务概览

`GET /portal/finance/overview`

Response data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| wallet | SaasWallet | 企业钱包 |
| subscription | SaasSubscription | 企业唯一的当前订阅状态；未订阅时 `status=0`、额度为 0，生效时包含 plan |
| currentMemberCount | number | 当前有效成员数 |
| rechargeLimits.minimumAmount | number | 单笔充值最小金额，来自服务器实际生效的 `STRIPE_RECHARGE_MIN_AMOUNT` |
| rechargeLimits.maximumAmount | number | 单笔充值最大金额，来自服务器实际生效的 `STRIPE_RECHARGE_MAX_AMOUNT` |
| rechargeLimits.currency | string | Stripe 充值币种，例如 `cny` |

### 5.2 查询套餐

`GET /portal/finance/plans`

Response data：`SaasPlan[]`

### 5.3 创建充值订单

`POST /portal/finance/recharge-orders`

权限：OWNER、ADMIN。

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| amount | number | 是 | 充值金额，最多两位小数；生产上下限由 `STRIPE_RECHARGE_MIN_AMOUNT` 与 `STRIPE_RECHARGE_MAX_AMOUNT` 控制 |

Response data：`SaasRechargeOrder`

接口只创建状态为待支付的本地订单，`payChannel` 固定为 `STRIPE`，不会增加企业余额。创建前服务端会校验金额最多保留两位小数，且必须位于 `STRIPE_RECHARGE_MIN_AMOUNT` 和 `STRIPE_RECHARGE_MAX_AMOUNT` 配置的闭区间内；超限返回 HTTP 400 且不创建订单。前端不提交支付渠道，Stripe Checkout 根据 Dashboard 配置、币种、地区和设备展示实际可用方式。

常见错误：

| HTTP 状态 | 错误信息 | 触发条件 |
| --- | --- | --- |
| 400 | 充值金额最多保留两位小数 | `amount` 精度超过两位小数 |
| 400 | 单笔充值金额不能低于 `{minimumAmount}` 元 | `amount` 小于服务器配置的最小值 |
| 400 | 单笔充值金额不能超过 `{maximumAmount}` 元 | `amount` 大于服务器配置的最大值 |

从套餐订阅页面因余额不足进入充值时，前端会携带 `planId`、`periodCount`、`autoRenew` 和展示用途的 `orderType` 订阅意图。前端按用户、企业和充值订单在当前标签页的 sessionStorage 中临时保存意图，仅供本次付款回跳恢复。Stripe 整页回跳携带 `payment_return=1` 时一次性消费缓存并移除 URL 的回跳及套餐参数；嵌入组件的完成回调也可以激活本次确认入口，但必须等待服务端订单状态为已支付才显示确认订阅、续订或改订按钮。离开页面、刷新付款结果页或普通访问历史已支付订单都不再恢复该入口。用户中途退出后，可直接在订阅服务页面重新选择套餐并使用已到账的余额支付，无需寻找原充值订单。套餐操作仍由用户确认后发起，服务端重新计算金额、校验权限、扣减余额并更新套餐；不新增自动订阅或后台套餐任务，不改变接口和数据结构。

### 5.3.1 查询充值订单详情

`GET /portal/finance/recharge-orders/{id}`

权限：OWNER、ADMIN，且订单必须属于当前企业。

Response data：`SaasRechargeOrder`

### 5.3.2 创建 Stripe Embedded Checkout Session

`POST /portal/payment/stripe/recharge-orders/{orderId}/checkout-session`

作用：为当前企业的待支付充值单创建或复用 Stripe Checkout Session。新 Session 使用 `ui_mode=embedded_page`、`mode=payment` 和动态 `price_data`，前端通过 `createEmbeddedCheckoutPage` 挂载 Stripe Full embedded page，邮箱、支付方式和支付按钮均由 Stripe 渲染；不指定 `payment_method_types`。创建时显式设置 Session 在 30 分钟后过期，并将 Stripe 返回的过期时间保存到本地订单。同一订单重复请求只复用仍为 `open + unpaid` 的已绑定 Session，Stripe 创建请求同时使用业务单号作为幂等依据。接口返回 `uiMode`，前端保留对历史 `elements` Session 的兼容，避免替换正在支付的会话。

权限：OWNER、ADMIN，且订单必须属于当前企业。

Path：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| orderId | number | 本地充值订单 ID |

Response data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| orderId | number | 本地充值订单 ID |
| sessionId | string | Stripe Checkout Session ID |
| uiMode | string | Stripe Session UI 模式；新订单为 `embedded_page`，历史 Session 可能为 `elements` |
| publishableKey | string | Stripe.js 初始化用可公开密钥 |
| clientSecret | string | 当前嵌入式 Session 的客户端凭证，仅供 Stripe.js 使用 |

常见错误：订单不存在返回 `404`，订单非待支付返回 `409`，Stripe 未启用或缺少配置返回 `503`，Stripe API 调用失败返回 `502`。

Mock response：

```json
{
  "code": 200,
  "msg": "Stripe 结账会话已就绪",
  "data": {
    "orderId": 101,
    "sessionId": "cs_test_123",
    "publishableKey": "pk_test_123",
    "clientSecret": "cs_test_123_secret_456"
  }
}
```

### 5.3.3 Stripe Webhook

`POST /portal/payment/stripe/webhook`

作用：接收 Stripe 服务端事件。该接口不使用门户 JWT，而是通过 `Stripe-Signature` 和 `STRIPE_WEBHOOK_SECRET` 验签。对于 `checkout.session.*` 事件，服务端还要求事件对象必须能反序列化为非空 Session，否则返回错误让 Stripe 重试。`checkout.session.completed` 或 `checkout.session.async_payment_succeeded` 仅在 `payment_status=paid`、Session 已绑定本地订单且金额/币种一致时入账；事件 ID 唯一索引、充值订单行锁和待支付/支付处理中状态条件共同保证重复通知不会重复增加余额。

状态流转规则：

- `checkout.session.completed` 且 `payment_status=paid`：更新为已支付并增加余额。
- `checkout.session.completed` 且 `payment_status=unpaid`：更新为支付处理中，等待异步支付结果。
- `checkout.session.async_payment_succeeded` 且 `payment_status=paid`：从待支付或支付处理中更新为已支付并增加余额。
- `checkout.session.async_payment_failed`：更新为支付失败。
- `checkout.session.expired`：更新为已过期。

外部退款通知（不提供主动退款接口）：

- Stripe 控制台为同一 Webhook 地址额外开启 `refund.created`、`refund.updated`、`refund.failed`，保留上述四个 Checkout 事件；测试与生产分别配置。
- 退款事件对象必须为非空 Refund。验签并去重 `event.id` 后，按 `payment_intent` 匹配原充值企业，不信任 metadata 的企业或金额。原支付尚未入账时，查询 Stripe Checkout 补偿原充值，再处理退款；共享账号中非本系统充值付款仅审计，不操作余额。
- 原充值锁内通过 `GET /v1/refunds/{id}` 查询最新状态，防止迟到事件覆盖新状态。`succeeded` 按单笔退款金额回撤企业余额，允许负余额；`pending`、`requires_action` 不按创建通知提前扣款。此前已回撤的退款变为非成功状态时按差额恢复余额，防止退款失败却继续占用资金。
- 复用 `saas_stripe_webhook_event` 保存本地业务状态：`event_id=saas-refund-state:{refundId}`、`event_type=saas.refund.state`、`checkout_session_id=原Session`。`payload_json` 保存 `refundId`、`rechargeOrderId`、`paymentIntentId`、`currency`、`amount`、`appliedAmount`、`status`。真实 `evt_` 原始事件另行保存，不修改其负载。**不得把这些本地状态作为普通日志删除，否则会失去跨事件的退款幂等保障。** 不新增退款表，充值订单摘要不能代替 Refund 级别的幂等记录。
- 充值订单增加 `refundAmount`（数据库 `refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0`），表示累计已成功退款并实际回撤的金额。累计为0时状态为 `2 已支付`，大于0且小于充值额时为 `8 已部分退款`，等于充值额时为 `9 已完全退款`。退款失败补回时同步减少累计金额并恢复对应状态；2、8、9均视为原充值已入账，迟到付款通知不重复充值。退款回撤产生 `REFUND/OUT`，补回产生 `REFUND/IN`，与摘要、退款幂等记录及审计同一事务提交。迁移脚本 `backend/db/migration/V20260928_02_add_recharge_refund_summary.sql` 须审阅后执行，不新增表。
- 后续正金额充值可以部分冲抵负余额，不必一次补足全部欠款；套餐购买仍不允许透支。余额变化沿用既有欠费暂停/恢复规则，不自动取消套餐。
- **本接口仅处理退款，不处理银行卡争议或拒付（`charge.dispute.*`）。** 控制台直接退款不需要经过门户页面，但需配置这些退款事件后才能同步余额；本次不自动回填配置前已经发生的历史退款。

退款事件负载示例（联调时用 Stripe CLI 或控制台真实事件签名发送，不走门户前端 mock）：

```json
{
  "id": "evt_example",
  "object": "event",
  "type": "refund.updated",
  "data": {
    "object": {
      "id": "re_example",
      "object": "refund",
      "payment_intent": "pi_example",
      "amount": 10000,
      "currency": "cny",
      "status": "succeeded"
    }
  }
}
```

退款查询需要服务端 `STRIPE_SECRET_KEY`（与Webhook测试/生产环境一致），沿用现有 `STRIPE_WEBHOOK_SECRET`。创建事件不一定代表退款成功，服务端以查询的最新状态记账。币种/金额/关联不一致返回409，解析失败或数据库错误返回500，Stripe查询故障返回502；均不提交余额变更。

Header：`Stripe-Signature`（必填）。Body：Stripe 原始 JSON，不得由网关改写或重新序列化。成功或重复事件返回 HTTP 200；验签失败返回业务错误 `400`。

### 5.3.4 取消充值订单

`POST /portal/finance/recharge-orders/{id}/cancel`

作用：取消当前企业的待支付充值订单。只有 `status=1` 的订单可以取消；已取消订单重复调用时按成功返回，不重复修改。若订单已经绑定 Stripe Session，服务端先查询其远端状态：`open` 状态会调用 Stripe 的 Session Expire API 主动过期，确认远端已过期后才把本地订单更新为已取消；如果远端已完成，则拒绝取消，防止到账与取消并发造成状态冲突。

权限：OWNER、ADMIN，且订单必须属于当前企业。

Response data：更新为已取消状态的 `SaasRechargeOrder`。

常见错误：订单不存在返回 `404`，订单已支付或处于其他不可取消状态时返回 `409`。

### 5.4 查询充值订单

`GET /portal/finance/recharge-orders`

Query：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | number | 否 | 页码 |
| pageSize | number | 否 | 每页条数 |
| rechargeNo | string | 否 | 充值订单号 |
| startTime | string | 否 | 创建时间起点，格式 `yyyy-MM-dd HH:mm:ss` |
| endTime | string | 否 | 创建时间终点，格式 `yyyy-MM-dd HH:mm:ss` |
| status | number | 否 | 1 待支付，2 已支付，3 已取消，4 支付失败，5 已过期，6 已关闭，7 支付处理中，8 已部分退款，9 已完全退款 |

Response data：分页 `SaasRechargeOrder`

充值创建、列表、详情共用如下新增响应字段，不接受客户端提交退款金额或状态：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| refundAmount | number | 累计实际回撤退款金额，与 amount 同币种，新建为0 |
| status | number | 充值状态1至9，退款状态不允许发起付款或取消 |

部分退款 mock 示例（统一响应外壳不变）：

```json
{"code":200,"msg":"操作成功","data":{"id":90006,"rechargeNo":"RC_REFUND_PARTIAL","enterpriseId":1,"amount":1000,"refundAmount":300,"status":8,"payChannel":"STRIPE"}}
```

前端完整列表样例位于 `systemportal/src/mock/portalMock.js`，包含部分退款及完全退款订单。迁移从现有本地退款记账记录回填历史摘要，不重新修改余额；未曾处理的 Stripe 历史退款不在回填范围内。

### 5.4.1 充值订单超时补偿

SaaS 服务每五分钟执行一次小批量补偿扫描，默认每轮最多处理 100 条：

- 使用 `(status, stripe_checkout_session_id, created_at)` 复合索引，批量把未创建 Stripe Session 且创建超过 30 分钟的待支付订单更新为已过期。
- 使用 `(status, stripe_session_expires_at, id)` 复合索引，只查询本地记录已到 Session 截止时间的待支付订单；逐笔确认 Stripe 远端状态，已过期则更新为已过期，已完成且未到账则更新为支付处理中，已到账则执行幂等入账。
- 单批数量有上限、使用 `fixedDelay` 且单笔失败相互隔离，避免全表扫描、任务重叠或瞬间产生大量 Stripe API 请求。
- `status=6`（已关闭）保留给管理端或后续风控流程主动终止订单使用，本次自动过期与用户取消不会写入该状态。

可选环境变量：`STRIPE_RECONCILIATION_BATCH_SIZE`（默认 100）、`STRIPE_RECONCILIATION_INTERVAL_MS`（默认 300000）、`STRIPE_RECONCILIATION_INITIAL_DELAY_MS`（默认 60000）。

### 5.5 预览订阅订单

`POST /portal/finance/subscription-orders/preview`

作用：进入订阅订单详情页或调整连续订阅周期时，由服务端统一计算订单类型、改订最小周期、原套餐剩余价值、应付金额和应退金额。该接口不创建订单、不修改余额。

权限：OWNER、ADMIN。

Body：

| 字段 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| planId | number | 是 | 目标套餐 ID | 50001 |
| periodCount | number | 是 | 连续订阅周期数，必须为正整数 | 12 |

Response data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| plan | SaasPlan | 目标套餐 |
| currentPlan | SaasPlan \| null | 当前套餐 |
| orderType | string | BUY、RENEW、CHANGE_PLAN，由服务端根据当前订阅计算 |
| periodCount | number | 本次选择的周期数 |
| minimumPeriodCount | number | 允许提交的最小周期数；改订时向上取整以覆盖原有效期 |
| remainingDays | number | 按 Asia/Shanghai 自然日计算的整数剩余天数；到期日期减当前日期，最低为 0 |
| remainingPeriodCount | number | 当前套餐剩余周期数，可包含小数 |
| priceAmount | number | 目标套餐单价乘以周期数 |
| creditAmount | number | 原套餐剩余价值抵扣，仅改订时存在 |
| workorderOverageCount | number | 降低工单额度时需要立即计费的超额工单数 |
| workorderOverageAmount | number | 超额工单数乘以全局单周期单价后的费用 |
| workorderOverageUnitPrice | number | 当前全局单个工单单周期费用 |
| payableAmount | number | 最终需要从企业余额扣除的金额，不小于 0 |
| refundAmount | number | 差额为负时应退回企业余额的金额，不小于 0 |
| balanceAmount | number | 计算时企业可用余额 |
| shortfallAmount | number | 余额缺口 |
| startAt | string | 预计生效时间，`yyyy-MM-dd HH:mm:ss` |
| endAt | string | 预计到期时间，`yyyy-MM-dd HH:mm:ss` |
| memberCount | number | 当前企业有效成员数 |
| eligible | boolean | 周期数及成员人数是否满足提交条件 |
| validationMessage | string | 不满足条件时的原因 |

计算规则：

1. 改订最小周期数 = `ceil(当前订阅剩余天数 / 新套餐单周期天数)`。
2. 原套餐剩余价值 = `原套餐单周期价格 * 整数剩余天数 / 原套餐单周期天数`，最终金额四舍五入到两位小数。剩余天数按 `Asia/Shanghai` 业务日期计算：`max(到期日期 - 当前日期, 0)`，忽略时分秒，到期当天计价天数为 0。预览与实际提交共用规则；同一天内，在套餐、余额、工单费用及订阅有效状态未变化的情况下金额保持一致，跨日重新计价。
3. 新套餐金额 = `新套餐单周期价格 * periodCount`。
4. 变更后工单额度低于当前套餐时，按录入时间和 ID 排序，剔除新额度内最早的工单；剩余工单只有在最近扣费日至变更日期已达到完整计费周期，或从未扣费时，才按全局单价计算超额费。
5. 差额 = `新套餐金额 - 原套餐剩余价值 + 超额工单费`；差额为正计入 `payableAmount`，差额为负的绝对值计入 `refundAmount`。
6. 改订立即生效，新到期时间按目标套餐周期数重新计算，且所选周期必须覆盖原订阅有效期；支付成功后同步写入相关工单的扣费日。超额工单费记录在本次套餐订单中，并合并进套餐变更资金流水，不创建独立流水。

常见错误：套餐不存在时返回 `404`。

Mock response：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "orderType": "CHANGE_PLAN",
    "periodCount": 12,
    "minimumPeriodCount": 12,
    "remainingDays": 351,
    "remainingPeriodCount": 0.96,
    "priceAmount": 3588,
    "creditAmount": 2888.08,
    "workorderOverageCount": 50,
    "workorderOverageAmount": 10.00,
    "workorderOverageUnitPrice": 0.20,
    "payableAmount": 699.92,
    "refundAmount": 0,
    "balanceAmount": 12680.5,
    "shortfallAmount": 0,
    "eligible": true,
    "validationMessage": ""
  }
}
```

### 5.6 创建并支付订阅订单

`POST /portal/finance/subscription-orders`

作用：服务端重新计算订单金额并校验企业余额；余额充足时在同一事务中创建历史订单、扣款或退款、写资金流水并更新企业唯一的当前订阅状态。

权限：OWNER、ADMIN。

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| planId | number | 是 | 套餐 ID |
| periodCount | number | 是 | 连续订阅周期数，必须满足预览接口返回的最小周期数 |
| autoRenew | boolean | 否 | 是否自动续费 |

Response data：`SaasOrder`

错误情况：

| code | msg | 触发条件 |
| --- | --- | --- |
| 404 | 套餐不存在 | planId 无效或套餐已不存在 |
| 409 | 企业余额不足，请先充值 | payableAmount 大于企业可用余额，data 返回最新订单预览和 shortfallAmount |
| 422 | 改订周期不能少于 N 个周期 | periodCount 无法覆盖原订阅有效期 |
| 422 | 当前企业有 N 名成员，超过该套餐 M 人的成员上限 | 目标套餐人数上限不足 |

Mock response：

```json
{
  "code": 200,
  "msg": "订阅订单已支付，套餐已生效",
  "data": {
    "id": 80003,
    "orderNo": "SO20260714A2B3C",
    "orderType": "CHANGE_PLAN",
    "planId": 50001,
    "periodCount": 12,
    "priceAmount": 3588,
    "creditAmount": 2888.08,
    "payableAmount": 699.92,
    "refundAmount": 0,
    "paidAmount": 699.92,
    "payType": "BALANCE",
    "autoRenew": true,
    "status": 2
  }
}
```

### 5.7 设置自动续费

`PUT /portal/finance/subscription/auto-renew`

权限：OWNER、ADMIN。ISSUER 调用时返回 `403`；只有 `status=1` 的生效订阅可修改自动续费。

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| autoRenewEnabled | boolean | 是 | 是否开启自动续费 |

Response data：`SaasSubscription`

### 5.8 查询订阅订单

`GET /portal/finance/subscription-orders`

Query：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | number | 否 | 页码 |
| pageSize | number | 否 | 每页条数 |
| orderNo | string | 否 | 订阅订单号 |
| orderType | string | 否 | BUY、RENEW、AUTO_RENEW、CHANGE_PLAN |
| startTime | string | 否 | 创建时间起点，格式 `yyyy-MM-dd HH:mm:ss` |
| endTime | string | 否 | 创建时间终点，格式 `yyyy-MM-dd HH:mm:ss` |

Response data：分页 `SaasOrder`

### 5.9 查询订阅订单详情

`GET /portal/finance/subscription-orders/{id}`

作用：从 SaaS 门户订阅订单列表查看单笔历史订单，展示下单时套餐权益快照、费用构成、支付状态和失败原因。接口按当前登录用户所选企业校验订单归属，不能跨企业查询。

Path：

| 字段 | 类型 | 必填 | 说明 | 示例值 |
| --- | --- | --- | --- | --- |
| id | number | 是 | 订阅订单主键 | 80001 |

Response data：单个 `SaasOrder`。其中 `planSnapshot` 为下单时的套餐快照，`periodCount` 根据订单总服务时长和快照单周期时长计算。

成功示例：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 80001,
    "orderNo": "SO202607010001",
    "orderType": "BUY",
    "planId": 50002,
    "planName": "专业版",
    "planSnapshot": {
      "id": 50002,
      "name": "专业版",
      "userLimit": 30,
      "workorderLimit": 5000,
      "durationDays": 365,
      "billingPeriod": "YEAR"
    },
    "buyUserLimit": 30,
    "buyWorkorderLimit": 5000,
    "buyDurationDays": 365,
    "periodCount": 1,
    "priceAmount": 2999.00,
    "discountAmount": 0.00,
    "creditAmount": 0.00,
    "workorderOverageCount": 0,
    "workorderOverageAmount": 0.00,
    "payableAmount": 2999.00,
    "refundAmount": 0.00,
    "paidAmount": 2999.00,
    "payType": "BALANCE",
    "autoRenew": true,
    "status": 2,
    "failureReason": null,
    "paidAt": "2026-07-01 12:20:00",
    "createdAt": "2026-07-01 12:18:00"
  }
}
```

错误情况：

| HTTP/业务码 | 错误信息 | 触发条件 |
| --- | --- | --- |
| 401 | 未登录或登录已失效 | 请求未携带有效门户令牌 |
| 404 | 订阅订单不存在 | 订单不存在、已删除或不属于当前企业 |

### 5.10 查询资金流水

`GET /portal/finance/wallet-transactions`

Query：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | number | 否 | 页码 |
| pageSize | number | 否 | 每页条数 |
| transactionNo | string | 否 | 流水号 |
| startTime | string | 否 | 交易时间起点，格式 `yyyy-MM-dd HH:mm:ss` |
| endTime | string | 否 | 交易时间终点，格式 `yyyy-MM-dd HH:mm:ss` |
| direction | string | 否 | IN、OUT |
| transactionType | string | 否 | RECHARGE、BUY_PLAN、RENEW_PLAN、AUTO_RENEW、CHANGE_PLAN、WORKORDER_OVERAGE、REFUND、ADJUST |

Response data：分页 `SaasWalletTransaction`

## 6. 用户中心

### 6.1 查询个人资料

`GET /portal/user/profile`

Response data：`TenantUser`

### 6.2 更新个人资料

`PUT /portal/user/profile`

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | string | 是 | 登录账号 |
| phone | string | 是 | 手机号 |
| realName | string | 是 | 姓名 |
| idNum | string | 否 | 证件号，后端按需加密或脱敏 |

Response data：`TenantUser`

## 7. 核心数据结构

### TenantUser

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 用户 ID |
| username | string | 登录账号 |
| phone | string | 手机号 |
| realName | string | 姓名 |
| idNum | string | 证件号 |
| avatarFileId | number | 头像文件 ID |
| status | number | 1 启用，0 禁用 |
| lastLoginTime | string | 最后登录时间 |

### TenantEnterprise

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 企业 ID |
| name | string | 企业名称 |
| code | string | 企业编码 |
| ownerUserId | number | 当前拥有者用户 ID |
| contactName | string | 联系人 |
| contactPhone | string | 联系电话 |
| status | number | 1 正常，2 欠费限制，3 停用 |
| source | number | 1 用户自建，2 后台创建 |

### TenantMember

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 成员 ID |
| enterpriseId | number | 企业 ID |
| userId | number | 用户 ID |
| realName | string | 成员姓名 |
| phone | string | 手机号 |
| roleCode | string | OWNER、ADMIN、ISSUER |
| status | number | 1 启用，0 停用，2 待审核；退出后成员关系被软删除 |
| joinedByInviteId | number | 来源邀请码 |
| joinedAt | string | 加入时间 |

### SaasPlan

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 套餐 ID |
| code | string | 套餐编码 |
| name | string | 套餐名称 |
| description | string | 描述 |
| billingPeriod | string | MONTH、YEAR、DAY |
| durationDays | number | 有效天数 |
| userLimit | number | 最大成员数 |
| workorderLimit | number | 套餐包含的工单数量额度 |
| price | number | 售价 |
| originalPrice | number | 原价 |
| status | number | 1 上架，0 下架 |

### SaasSubscription

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 当前订阅状态 ID，每个企业固定一条 |
| enterpriseId | number | 企业 ID，数据库唯一 |
| planId | number \| null | 当前或最近一次套餐 ID |
| orderId | number \| null | 最近一次生效订单 ID |
| status | number | 0 未订阅，1 生效中，2 已过期，3 已暂停，4 已取消 |
| userLimit | number | 当前可启用成员上限，未订阅或到期时为 0 |
| workorderLimit | number | 当前订阅的工单数量额度，未订阅或到期时为 0 |
| ocrQuota | number | 当前周期 OCR 额度 |
| requestQuota | number | 当前周期请求额度 |
| startAt | string \| null | 当前订阅生效时间 |
| endAt | string \| null | 当前订阅到期时间 |
| autoRenewEnabled | boolean | 是否开启自动续费，仅生效订阅可修改 |
| nextRenewAt | string \| null | 下次自动续费时间 |
| plan | SaasPlan \| null | 套餐详情，未订阅时为空 |

### SaasOrder

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 套餐订单 ID |
| orderNo | string | 套餐订单号 |
| orderType | string | BUY、RENEW、AUTO_RENEW、CHANGE_PLAN |
| planId | number | 目标套餐 ID |
| planName | string | 套餐名称快照 |
| planSnapshot | SaasPlan | 下单时完整套餐快照 |
| periodCount | number | 连续订阅周期数 |
| buyUserLimit | number | 下单时成员上限 |
| buyWorkorderLimit | number | 下单时套餐工单额度 |
| buyDurationDays | number | 单周期天数乘以周期数 |
| workorderOverageCount | number | 套餐变更时立即计费的超额工单数 |
| workorderOverageAmount | number | 套餐变更时立即计收的超额工单费用 |
| priceAmount | number | 新套餐原始金额 |
| discountAmount | number | 优惠金额 |
| creditAmount | number | 原套餐剩余价值抵扣 |
| payableAmount | number | 最终应付金额 |
| refundAmount | number | 应退回企业余额金额 |
| paidAmount | number | 企业余额实际支付金额 |
| walletTransactionId | number \| null | 关联资金流水 ID |
| originalSubscriptionId | number \| null | 改订时的原订阅 ID |
| oldPlanId | number \| null | 改订前套餐 ID |
| newPlanId | number \| null | 改订后套餐 ID |
| autoRenew | boolean | 是否自动续费 |
| status | number | 1 待支付，2 已支付，3 已取消，4 已退款，5 已关闭，6 自动续费失败 |
| failureReason | string | 自动续费失败原因，仅失败订单有值 |

`SaasWallet`、`SaasSubscription` 与 `SaasOrder` 直接对应 `other/saas数据库设计.md` 中的同名表，前端字段采用小驼峰命名，例如 `balance_amount` 对应 `balanceAmount`，`auto_renew_enabled` 对应 `autoRenewEnabled`。
# 订阅欠费状态扩展

财务概览及包含订阅快照的接口在 `subscription` 中增加以下字段：

| 字段 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `status` | number | 是 | `0` 未订阅、`1` 正常、`2` 已过期、`3` 已暂停 | `3` |
| `suspendReason` | string/null | 否 | `ARREARS` 欠费、`MANUAL` 人工、`RISK_CONTROL` 风控 | `ARREARS` |
| `suspendedAt` | string/null | 否 | 最近一次暂停时间，格式 `yyyy-MM-dd HH:mm:ss` | `2026-08-16 04:00:00` |
| `resumedAt` | string/null | 否 | 最近一次欠费恢复时间 | `2026-08-16 09:30:00` |

只有尚未到期且已经订阅套餐的企业参与余额联动。充值成功后如果余额大于等于恢复阈值（默认0），欠费暂停会在充值事务内即时恢复；前端无需调用额外恢复接口。
