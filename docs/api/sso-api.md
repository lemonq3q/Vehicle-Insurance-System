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
```

mock 模式只验证 SaaS 前端的按钮与跳转。完整的跨后端兑换需启动 SaaS 后端、车险后端和两个 Redis。
