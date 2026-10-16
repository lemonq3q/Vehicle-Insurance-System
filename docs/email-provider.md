# 车险后端邮件发送渠道

`backend/saas` 与 `backend/insruance` 的注册、找回密码及首次邮箱绑定验证码通过 `backend/common/src/main/java/com/example/insurancesystem/utils/email/` 包中的 `EmailUtil` 接口发送纯文本邮件。`EMAIL_PROVIDER` 选择同包实现：`qq`（默认）使用 QQ SMTP，`gmail` 使用 Gmail API。两端均使用 `EmailAccountService` 和 `EmailVerificationService`；验证码五分钟有效、一分钟重发、最多五次错误尝试，成功原子消费。接口契约见 [共享邮箱账号接口](api/email-account-api.md)。

## QQ SMTP

设置 `EMAIL_PROVIDER=qq`、`QQ_EMAIL_USER`（发件邮箱）和 `QQ_EMAIL_AUTH_CODE`（QQ 邮箱 SMTP 授权码）。原有代码中的授权码已从实现中移除，部署时必须配置环境变量。

## Gmail API

1. 在 Google Cloud 项目中启用 Gmail API，配置 OAuth 同意屏幕并创建“Web 应用”类型的 OAuth 客户端。仅发送邮件时申请 `https://www.googleapis.com/auth/gmail.send`。个人 Gmail 账号选择“外部”受众；为了避免测试模式的刷新令牌 7 天到期，需要将应用发布为“正式运行”。个人用途可能仍出现未验证应用提示。
2. 下载的客户端 JSON 只包含 `client_id`、`client_secret` 等客户端信息，**不包含用户授权后的 `refresh_token`**。使用 [Google OAuth 2.0 Playground](https://developers.google.com/oauthplayground/) 获取：临时在 OAuth 客户端“已获授权的重定向 URI”中加入 `https://developers.google.com/oauthplayground`；Playground 设置中启用“Use your own OAuth credentials”，填入该客户端的 ID 和 secret，选择 Server-side、Offline 和 Consent Screen；在步骤 1 输入 `https://www.googleapis.com/auth/gmail.send` 并以发件 Gmail 账号授权；步骤 2 兑换并保存 refresh token。完成后可移除临时重定向 URI。不要使用 Playground 自带的默认客户端凭据生成长期令牌。
3. 在后端运行环境设置以下变量，**不要把 JSON 文件或令牌复制到代码、YAML 或 Git 仓库**：

   | 变量 | 值 |
   | --- | --- |
   | `EMAIL_PROVIDER` | `gmail` |
   | `GMAIL_CLIENT_ID` | JSON 的 `client_id` |
   | `GMAIL_CLIENT_SECRET` | JSON 的 `client_secret` |
   | `GMAIL_REFRESH_TOKEN` | 上一步获得的刷新令牌 |
   | `GMAIL_FROM` | 完成授权的 Gmail 地址 |

4. 将上述环境变量同时注入 `backend/saas` 和 `backend/insruance`，并重启两个服务。一次验证码发送会先向 Google OAuth 端点换取短期访问令牌，再调用 Gmail `users.messages.send`。Google 接受邮件请求后，业务层才把验证码保存到 Redis。Gmail 收件箱是否最终投递仍取决于 Google 的投递处理。

刷新令牌不保证永久有效。撤销授权、长期未使用、修改 Gmail 账号密码等情况可能使其失效；此时需要重新授权并更新 `GMAIL_REFRESH_TOKEN`。配置缺失或 Google 返回错误时，现有验证码接口返回“发送失败”，服务日志只记录失败阶段和 HTTP 状态，不记录密钥、令牌或邮件内容。

本次共享邮箱账号改造新增注册、找回和绑定接口，运行时直接调用真实邮件渠道；离线示例保留在 `systemportal/src/mock/portalMock.js`。上线前需审阅并执行邮箱唯一约束迁移，路径和接口契约见 [共享邮箱账号接口](api/email-account-api.md)。
