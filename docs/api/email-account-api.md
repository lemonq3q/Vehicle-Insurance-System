# SaaS 门户与车险系统共享邮箱账号接口

本次实现邮箱认证和绑定；微信扫码登录后续单独接入。车险前端只提供登录和找回密码，共享账号注册统一在 SaaS 门户操作；后端注册接口保留兼容。两端持久化同一 `insurance_saas.tenant_user`，保留用户 ID、企业成员关系和既有 SSO。邮箱统一去首尾空白并转小写，最长 100 字符；新密码为 8–64 字符且 UTF-8 不超过 72 字节。登录可使用符合现有规则的历史密码。

## 接口及权限

| HTTP 方法和应用内路径 | 场景 | 认证 |
| --- | --- | --- |
| POST `/portal/auth/login`、`/auth/login` | 邮箱密码登录对应应用 | 匿名 |
| POST `/portal/auth/email-code`、`/auth/email-code` | 注册或重置验证码 | 匿名 |
| POST `/portal/auth/register`、`/auth/register` | 创建共享个人账号，暂不创建企业成员 | 匿名 |
| POST `/portal/auth/forget-password`、`/auth/forget-password` | 验证邮箱并重置共享密码 | 匿名 |
| GET `/portal/user/profile`、`/auth/profile` | 当前个人资料 | 当前应用 token |
| PUT `/portal/user/profile`、`/auth/profile` | 本人普通资料编辑 | 当前应用 token |
| POST `/portal/user/profile/email-code`、`/auth/profile/email-code` | 未绑定账号申请绑定验证码 | 当前应用 token |
| POST `/portal/user/profile/bind-email`、`/auth/profile/bind-email` | 首次绑定邮箱并设置密码 | 当前应用 token |

部署前缀分别为 `/saasback` 和 `/insuranceback`。认证请求头沿用 `token: <JWT>`；门户也接受 `Authorization: Bearer <JWT>`。绑定接口只取服务端会话中的用户 ID，不接受 body 的 ID。无 query、path、file 参数，所有写请求使用 JSON body，避免密码出现在 URL。

## 请求参数

| 操作 | body 字段 | 类型 | 必填 | 约束与示例 |
| --- | --- | --- | --- | --- |
| 登录 | email | string | 是 | `user@example.com`，邮箱身份；不再接受手机号或任意 username |
| 登录 | password | string | 是 | 原始密码，不去除首尾空白 |
| 发验证码 | email | string | 是 | 待验证邮箱 |
| 匿名发验证码 | scene | string | 是 | `REGISTER` 或 `RESET_PASSWORD`；注册邮箱不得已占用，重置邮箱必须存在且启用 |
| 注册 | email、emailCode、password | string | 是 | 邮箱、六位数字验证码、新密码 |
| 注册 | realName | string | 是 | 非空姓名，最多 100 字符 |
| 重置密码 | email、emailCode、password | string | 是 | 必须消费同一应用发出的 `RESET_PASSWORD` 验证码 |
| 绑定发码 | email | string | 是 | 服务端固定 `BIND_EMAIL`，并关联当前用户 ID |
| 完成绑定 | email、emailCode、password | string | 是 | 首次设置邮箱与登录密码，禁止覆盖已有绑定 |

验证码随机生成，通过既有 QQ SMTP 或 Gmail API `EmailUtil` 实现发送；有效期 300 秒，重发冷却 60 秒，最多五次错误尝试。场景与当前应用缓存隔离，绑定码还与当前用户关联。发送失败不保存验证码，不返回固定 mock 验证码、密码或任何令牌。验证成功仅允许消费一次。

## 响应

沿用 `{ "code": 200, "msg": "操作成功", "data": ... }`。门户登录仍返回 token、user、enterprises、currentEnterpriseId、currentEnterprise、currentMember；车险登录仍返回 token、user（id、username、name、perms），企业成员与套餐校验保持有效。

注册、查询个人资料、绑定返回 `TenantUser`：id:number、username:string|null（保留内部历史标识）、email:string|null、phone:string|null、realName:string|null、idNum:string|null、avatarFileId:number|null、status:number、lastLoginTime:string|null、createdAt:string|null、updatedAt:string|null。不包含 password。重置返回 `true`。

离线 mock 示例（位于 `systemportal/src/mock/portalMock.js`，未接入运行时请求实例）：

```json
{"code":200,"msg":"验证码已发送","data":{"expiresInSeconds":300,"retryAfterSeconds":60}}
```

```json
{"code":200,"msg":"邮箱绑定成功","data":{"id":10001,"username":"wechat-account","email":"user@example.com","phone":null,"realName":"李明","idNum":null,"avatarFileId":null,"status":1,"lastLoginTime":null,"createdAt":"2026-10-11 12:00:00","updatedAt":"2026-10-11 12:01:00"}}
```

## 错误与约束

| code | 典型消息 | 触发条件 |
| --- | --- | --- |
| 400 | 邮箱格式错误 / 验证码错误或已失效 / 密码需为 8 至 64 位 | 参数、过期验证码、用途不匹配或消费重放 |
| 401 | 未登录 | 绑定请求缺失或失效 token |
| 403 | 账号未启用 | 停用账号无法申请密码重置或完成认证 |
| 404 | 邮箱未注册 / 用户不存在 | 账号不存在或已软删除 |
| 409 | 邮箱已被使用 / 当前账号已绑定邮箱 | 唯一约束或禁止覆盖已有邮箱 |
| 429 | 请一分钟后再尝试发送 / 验证码尝试次数过多 | 重发限制或连续错误 |
| 502 | 邮件发送失败，请稍后重试 | 发信服务未配置、认证错误或网络失败 |

个人资料普通编辑 body 为 `realName:string`（必填，最多100字符）、`phone:string|null`（可选，11位大陆手机号）、`idNum:string|null`（可选，最多100字符）；返回 TenantUser。仅修改姓名、联系方式和证件号，邮箱必须通过绑定接口验证。微信账号尚未接入，本次也支持已有未绑定邮箱的有效账号使用相同绑定入口。历史无邮箱账号需在已有会话中绑定，或由后续微信登录进入个人中心。

## 支付邮箱自动填写

`POST /portal/payment/stripe/recharge-orders/{id}/checkout-session` 请求不变。服务端从当前用户的数据库资料读取邮箱，对新 Stripe Session 设置 `customer_email`；无邮箱不设置，保留 Stripe 的人工输入。响应新增 `email:string|null`。复用已有 Session 时保留该 Session 的邮箱，不覆盖付款人已填写的资料。前端保留的 Elements 实现同样优先使用 Session 邮箱，再回退当前账号邮箱。

## 部署

两个后端均需配置 `EMAIL_PROVIDER` 和对应 QQ/Gmail 环境变量，详见 `docs/email-provider.md`。应用真实验证码接口不依赖短信 mock。部署前审阅并执行 `backend/db/migration/V20261011_01_unique_active_user_email.sql`；其生成列唯一索引阻止并发抢占同一邮箱，软删除和无邮箱账号不占用该唯一值。脚本不会自动填充历史邮箱或改变账号归属。
