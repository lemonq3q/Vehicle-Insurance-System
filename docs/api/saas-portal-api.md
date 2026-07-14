# SaaS 门户前端接口文档

本文档描述 `systemportal` 前端当前使用的接口契约。业务接口由 `systemportal/src/api/portal.js` 统一导出，请求通过 `systemportal/src/api/request.js` 中的 Axios 实例管理；mock 模式由 `systemportal/src/mock/axiosMockAdapter.js` 转发至 `systemportal/src/mock/portalMock.js`。后端实现时应保持路径、方法、请求字段、响应字段和统一响应外壳一致。

当前前端已经按此文档接入 mock：宣传官网与仪表盘为静态样板；登录注册、企业信息、企业成员、邀请码、订阅服务、充值订单、订阅订单、资金明细、用户中心均通过 mock API 调用。

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
| mock 开关 | `VUE_APP_USE_MOCK`，仅显式设置为 `false` 时请求真实后端 |
| 请求超时 | 60000 ms |
| 登录态请求头 | `token: <token>`，同时发送 `Authorization: Bearer <token>` |
| token 刷新响应头 | `new-token` |
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
| password | string | 是 | 密码 | mock-password |

Response data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| token | string | 登录 token |
| user | TenantUser | 当前用户 |
| enterprises | TenantEnterprise[] | 可进入的企业 |
| currentEnterpriseId | number | 当前企业 ID |

Mock 示例：

```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "token": "mock-portal-token",
    "user": { "id": 10001, "username": "linxf", "phone": "13800000001", "realName": "林晓峰" },
    "enterprises": [{ "id": 20001, "name": "杭州小马车险服务有限公司", "code": "ENT-HZ-202607" }],
    "currentEnterpriseId": 20001
  }
}
```

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
| subscription | SaasSubscription | 当前订阅，包含 plan |

### 3.2 创建企业

`POST /portal/enterprise`

作用：无企业用户创建企业，创建后用户成为 OWNER。

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 企业名称 |
| code | string | 否 | 企业编码，不传时后端生成 |
| contactName | string | 是 | 联系人 |
| contactPhone | string | 是 | 联系电话 |

Response data：`TenantEnterprise`

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
| pageSize | number | 否 | 每页条数，默认 10 |

Response data：分页 `TenantInviteCode`

### 4.2 创建邀请码

`POST /portal/enterprise/invite-codes`

权限：OWNER、ADMIN。

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| maxUseCount | number | 否 | 最大使用次数，null 表示不限 |
| expiresAt | string | 是 | 过期时间，格式 `yyyy-MM-dd HH:mm:ss` |

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

### 4.6 转让企业拥有者

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

### 4.7 退出企业

`POST /portal/enterprise/members/exit`

权限：ADMIN、ISSUER。OWNER 不可退出企业。

Response data：`true`

## 5. 财务中心

### 5.1 财务概览

`GET /portal/finance/overview`

Response data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| wallet | SaasWallet | 企业钱包 |
| subscription | SaasSubscription | 当前订阅，包含 plan |
| currentMemberCount | number | 当前有效成员数 |

### 5.2 查询套餐

`GET /portal/finance/plans`

Response data：`SaasPlan[]`

### 5.3 创建充值订单

`POST /portal/finance/recharge-orders`

权限：OWNER、ADMIN。

Body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| amount | number | 是 | 充值金额，必须大于 0 |
| payChannel | string | 是 | WECHAT、ALIPAY、BANK |

Response data：`SaasRechargeOrder`

### 5.4 查询充值订单

`GET /portal/finance/recharge-orders`

Query：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | number | 否 | 页码 |
| pageSize | number | 否 | 每页条数 |
| rechargeNo | string | 否 | 充值订单号 |
| status | number | 否 | 1 待支付，2 已支付，3 已取消，4 支付失败 |

Response data：分页 `SaasRechargeOrder`

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
| remainingDays | number | 当前套餐剩余天数 |
| remainingPeriodCount | number | 当前套餐剩余周期数，可包含小数 |
| priceAmount | number | 目标套餐单价乘以周期数 |
| creditAmount | number | 原套餐剩余价值抵扣，仅改订时存在 |
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
2. 原套餐剩余价值 = `(当前订阅剩余天数 / 原套餐单周期天数) * 原套餐单周期价格`。
3. 新套餐金额 = `新套餐单周期价格 * periodCount`。
4. 差额 = `新套餐金额 - 原套餐剩余价值`；差额为正计入 `payableAmount`，差额为负的绝对值计入 `refundAmount`。
5. 改订立即生效，新到期时间按目标套餐周期数重新计算，且所选周期必须覆盖原订阅有效期。

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
    "remainingDays": 351.5,
    "remainingPeriodCount": 0.96,
    "priceAmount": 3588,
    "creditAmount": 2888.08,
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

作用：服务端重新计算订单金额并校验企业余额；余额充足时在同一事务中创建订单、扣款或退款、写资金流水并更新当前订阅。

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
    "orderNo": "SO20260714120000",
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

权限：OWNER、ADMIN。ISSUER 调用时返回 `403`。

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

Response data：分页 `SaasOrder`

### 5.9 查询资金流水

`GET /portal/finance/wallet-transactions`

Query：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | number | 否 | 页码 |
| pageSize | number | 否 | 每页条数 |
| transactionNo | string | 否 | 流水号 |
| direction | string | 否 | IN、OUT |
| transactionType | string | 否 | RECHARGE、BUY_PLAN、RENEW_PLAN、AUTO_RENEW、CHANGE_PLAN、REFUND、ADJUST |

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
| status | number | 1 正常，0 禁用，2 待审核，3 已退出 |
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
| price | number | 售价 |
| originalPrice | number | 原价 |
| status | number | 1 上架，0 下架 |

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
| buyDurationDays | number | 单周期天数乘以周期数 |
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
| status | number | 1 待支付，2 已支付，3 已取消，4 已退款，5 已关闭 |

`SaasWallet`、`SaasSubscription` 与 `SaasOrder` 直接对应 `other/saas数据库设计.md` 中的同名表，前端字段采用小驼峰命名，例如 `balance_amount` 对应 `balanceAmount`，`auto_renew_enabled` 对应 `autoRenewEnabled`。
