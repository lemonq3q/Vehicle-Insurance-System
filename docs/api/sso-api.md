# SaaS 门户进入车险系统 SSO 接口

## 流程和部署配置

1. SaaS 前端使用已登录的门户 Token 请求一次性授权码。
2. 浏览器跳转到车险前端 `/sso/callback?code=...`。
3. 车险前端把授权码交给车险后端。
4. 车险后端通过 HTTPS 请求 SaaS 内部兑换接口。
5. 车险后端使用兑换得到的 `userId + enterpriseId` 重新查询共享数据库、加载车险权限，并在自己的 Redis 中创建会话。

| 应用 | 环境变量 | 作用 |
| --- | --- | --- |
| SaaS 后端 | `INSURANCE_FRONTEND_URL` | 车险前端根地址，例如 `https://insurance.example.com` |
| SaaS 后端 | `INSURANCE_SSO_CLIENT_SECRET` | 两个后端之间的认证密钥 |
| SaaS 后端 | `INSURANCE_SSO_CODE_TTL_SECONDS` | 授权码有效秒数，默认 60 |
| SaaS 后端 | `PORTAL_LOGIN_KEY_PREFIX` | SaaS Redis 登录会话前缀，默认 `portal:login:` |
| 车险后端 | `SAAS_SSO_EXCHANGE_URL` | SaaS 内部兑换接口的完整 HTTPS 地址 |
| 车险后端 | `INSURANCE_SSO_CLIENT_SECRET` | 必须与 SaaS 后端一致 |
| 车险后端 | `INSURANCE_LOGIN_KEY_PREFIX` | 车险 Redis 登录会话前缀，默认 `insurance:login:` |

即使本地测试时两套后端连接同一 Redis，会话 Key 也会分别为：

```text
portal:login:{userId}
insurance:login:{userId}
```

## 申请车险系统授权码

### `POST /portal/sso/authorize`

- 场景：SaaS 门户中已登录用户点击“进入车险系统”。
- 授权前置条件：用户账号、当前企业成员、企业状态均已启用，并且当前套餐 `status=1`、已关联套餐且尚未到期。任一条件不满足时返回 `403`，前端停留在门户并展示具体原因，不签发一次性 code。
- 权限：需要 SaaS 登录 Token，并且账号、企业、企业成员状态均可用。
- Header：`Authorization: Bearer <portalToken>` 或 `token: <portalToken>`。
- Body：空 JSON 对象 `{}`。

成功响应：

```json
{
  "code": 200,
  "msg": "授权成功",
  "data": {
    "redirectUrl": "https://insurance.example.com/sso/callback?code=64位随机授权码",
    "expiresIn": 60
  }
}
```

常见错误：

| code | 说明 |
| --- | --- |
| 401 | SaaS Token 无效或会话已过期 |
| 403 | 未加入企业，或用户/成员/企业状态不可用 |

## 车险前端兑换登录会话

### `POST /auth/sso/exchange`

- 场景：车险前端 SSO 回调页自动登录。
- 权限：公开接口，但授权码只有效一次且快速过期。

请求：

```json
{
  "code": "64位随机授权码"
}
```

成功响应与车险现有 `/auth/login` 保持一致：

```json
{
  "code": 200,
  "msg": "login succeed",
  "data": {
    "token": "insurance-jwt",
    "user": {
      "id": 10001,
      "username": "13800138000",
      "name": "张三",
      "perms": ["workorder:list"]
    }
  }
}
```

常见错误：

| code | 说明 |
| --- | --- |
| 400 | 未提交授权码 |
| 401 | 授权码无效、已使用或已过期 |
| 403 | 用户、企业成员或企业不可用 |
| 502 | 车险后端无法连接 SaaS 认证服务 |

## SaaS 内部授权码兑换

### `POST /internal/sso/exchange`

- 调用方：仅车险后端。
- Header：`X-Insurance-Client-Secret: <server-secret>`。
- Body：与前述授权码请求相同。
- 成功返回：`userId`、`enterpriseId`、`target`、`issuedAt`、`expiresAt`。
- 生产要求：只允许 HTTPS，另外在防火墙或反向代理限制车险服务器 IP；不得将共享密钥写入前端。

## Mock 示例

SaaS 前端 mock 已支持 `/portal/sso/authorize`，默认跳转到：

```text
http://localhost:8888/sso/callback?code=mock-insurance-sso-code

## SaaS 主动失效车险会话

以下接口仅供 SaaS 后端调用，均使用 `X-Insurance-Client-Secret` 共享密钥，不接受终端用户 JWT 代替内部鉴权。

### `POST /internal/session/logout-enterprise`

- 场景：有效企业套餐从正常状态切换为欠费暂停后，立即清除该企业全部成员的车险 Redis 会话。
- 请求：`{"enterpriseId": 1}`。
- 成功响应：`data` 为参与处理的企业成员数量。
- 幂等性：重复调用安全，未登录用户不产生额外影响。

### `POST /internal/session/logout-user`

- 场景：成员主动退出企业、被管理员移除或被停用后，立即清除该用户当前车险 Redis 会话。
- 请求：`{"userId": 10001}`。
- 成功响应：`data=1` 表示请求已处理。

SaaS 在本地数据库事务提交后调用上述接口，避免远程网络请求占用或回滚余额、订阅和成员关系事务。调用失败会写入包含范围和目标 ID 的错误日志。
```

mock 模式只验证 SaaS 前端的按钮与跳转。完整的跨后端兑换需启动 SaaS 后端、车险后端和两个 Redis。

## 车险系统返回 SaaS 门户

返回流程与进入车险系统相反，并使用独立的一次性授权码：

1. 已登录的车险前端请求 `POST /auth/sso/portal-authorize`。
2. 车险后端根据当前登录会话取得 `userId + enterpriseId`，通过共享密钥调用 SaaS 内部授权接口。
3. 浏览器跳转到 SaaS 前端 `/sso/callback?code=...`。
4. SaaS 前端调用 `POST /portal/sso/exchange` 兑换授权码。
5. SaaS 后端重新校验账号、企业和成员状态，并在自己的 Redis 命名空间创建门户会话。

### `POST /auth/sso/portal-authorize`

- 场景：车险系统 Header 中点击“返回门户”。
- 权限：需要有效的车险登录 Token。
- Header：`Authorization: Bearer <insuranceToken>` 或 `token: <insuranceToken>`。
- Body：空 JSON 对象 `{}`。

成功响应：

```json
{
  "code": 200,
  "msg": "授权成功",
  "data": {
    "redirectUrl": "http://localhost:8887/sso/callback?code=64位随机授权码",
    "expiresIn": 60
  }
}
```

### `POST /internal/sso/portal-authorize`

- 调用方：仅车险后端。
- Header：`X-Insurance-Client-Secret: <server-secret>`。
- Body：

```json
{
  "userId": 10001,
  "enterpriseId": 20001
}
```

- SaaS 后端会重新校验用户的企业成员关系及企业状态，然后签发仅可用于门户的短效一次性授权码。

### `POST /portal/sso/exchange`

- 场景：SaaS 门户回调页自动登录。
- 权限：公开接口，但授权码仅可使用一次并在 60 秒后过期。
- Body：

```json
{
  "code": "64位随机授权码"
}
```

成功响应与 `/portal/auth/login` 的数据结构一致：

```json
{
  "code": 200,
  "msg": "自动登录成功",
  "data": {
    "token": "portal-jwt",
    "user": { "id": 10001, "username": "13800138000", "realName": "张三" },
    "enterprises": [{ "id": 20001, "name": "示例企业" }],
    "currentEnterpriseId": 20001,
    "currentEnterprise": { "id": 20001, "name": "示例企业" },
    "currentMember": { "enterpriseId": 20001, "userId": 10001, "roleCode": "OWNER", "status": 1 }
  }
}
```

常见错误：

| code | 说明 |
| --- | --- |
| 400 | 授权码或用户/企业标识缺失 |
| 401 | 车险或门户授权码无效、已使用或已过期 |
| 403 | 用户、企业成员或企业不可用，或内部共享密钥不正确 |
| 502 | 车险后端无法连接 SaaS 认证服务 |

新增环境变量：

| 应用 | 环境变量 | 默认值/作用 |
| --- | --- | --- |
| SaaS 后端 | `PORTAL_FRONTEND_URL` | `http://localhost:8887`，门户前端根地址；8889 由监控前端使用 |
| 车险后端 | `SAAS_PORTAL_AUTHORIZE_URL` | `http://localhost:8081/internal/sso/portal-authorize` |
