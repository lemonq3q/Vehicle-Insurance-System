# 监控后台认证 API

监控账号来自 `monitor_user`，角色来自 `monitor_role`，与 SaaS 企业账号完全隔离。除登录外的接口均需携带 `Authorization: Bearer <token>`；响应使用项目统一 `{ code, msg, data }` 外壳。

全新环境第一次启动且 `monitor_user` 为空时，可显式设置 `MONITOR_INITIAL_ADMIN_USERNAME`（11 位登录手机号）、`MONITOR_INITIAL_ADMIN_PASSWORD`（至少 8 位）和可选的 `MONITOR_INITIAL_ADMIN_REAL_NAME`。服务只创建一次首个 ADMIN，不提供默认生产密码，也不会覆盖已有账号。

登录接口已完成真实后端接入，不使用 mock。mock 阶段由各 API 封装的 `useMock` 标记独立控制，与开发/生产环境无关。开发服务会把 `/api/monitor` 转发到 `http://127.0.0.1:8083/monitor`，可通过 `MONITOR_DEV_BACKEND_URL` 覆盖目标地址。

## POST `/monitor/auth/login`

用途：监控后台登录页建立 JWT 与 Redis 单登录会话。账号必须未删除、状态启用并至少具有一个启用角色。

请求体：

| 字段 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| username | string | 是 | 11 位手机号码，也是唯一登录账号 | `13812341028` |
| password | string | 是 | 明文密码，仅经 HTTPS 传输 | `Monitor@123` |

成功响应：

```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "id": 1,
      "username": "13812341028",
      "realName": "林嘉诚",
      "email": "linjc@xiaoma.com",
      "status": 1,
      "lastLoginAt": "2026-08-24 10:20:30",
      "roleCode": "ADMIN",
      "roleName": "管理员"
    }
  }
}
```

错误：`400` 手机号格式错误、手机号或密码错误、字段为空；`403` 账号停用；未配置有效角色按错误凭据处理。前端 mock 账号为 `13812341028`，密码为 `Monitor@123`。

## GET `/monitor/auth/me`

用途：浏览器刷新后验证 JWT 和 Redis 会话，并恢复最新用户及角色资料。返回结构为登录响应中的 `data.user`。令牌缺失、过期、被新登录覆盖或账号删除时返回 `401`。

## POST `/monitor/auth/logout`

用途：删除当前 jti 对应的 Redis 会话。成功返回：

```json
{ "code": 200, "msg": "退出成功" }
```

## PUT `/monitor/auth/profile`

用途：当前登录人修改自己的姓名和邮箱。登录手机号、角色与状态不允许通过个人中心修改。

请求体：

```json
{"realName":"林嘉诚","email":"linjc@xiaoma.com"}
```

`realName` 必填且最多 64 字；邮箱可为空，非空时需符合格式且不能与其他未删除账号重复。成功返回与 `GET /monitor/auth/me` 相同的最新用户资料，并写入 `PROFILE_UPDATE` 操作日志。常见错误：参数格式错误（400）、邮箱重复（409）、账号不存在（404）。

## PUT `/monitor/auth/password`

用途：当前登录人验证原密码后修改自己的登录密码。

请求体：

```json
{"currentPassword":"Monitor@123","newPassword":"NewMonitor@456","confirmPassword":"NewMonitor@456"}
```

新密码至少 8 位、两次输入必须一致且不能与当前密码相同。成功后更新 BCrypt 摘要及 `password_changed_at`，写入不包含任何密码内容的 `PASSWORD_CHANGE` 操作日志，并立即删除当前 Redis 会话；前端必须清理本地令牌并重新登录。常见错误：当前密码错误、新密码不合规（400）、账号不存在（404）。
