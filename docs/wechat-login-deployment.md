# 微信登录上线配置

本次实现门户微信自动注册登录、车险已有账号微信登录、两端首次绑定邮箱/微信，以及 SaaS 接收授权撤回后同步撤销两端 JWT 会话。整个流程不删除任何账号，不清空微信身份或用户资料，不获取/保存头像，不合并账号。昵称用于首次注册姓名初值，缺失时随机生成，已有账号姓名不被登录覆盖。

## 1. 数据库增量

先备份并审阅 `backend/db/migration/V20261016_01_wechat_login.sql`，在两端共享的 `insurance_saas` 数据库执行一次，再部署后端。脚本只增加五个可空字段与 `(wechat_app_id,wechat_open_id)` 唯一索引；没有 UPDATE、DELETE、DROP 或历史资料迁移。不可重复执行。2026-10-18 已按用户要求在本地 localhost:3306/insurance_saas 执行该脚本，五个字段及唯一索引核验通过；原有 6 个账号的所有既有字段逐行摘要一致。服务器数据库尚未执行。执行 SQL、变更前建表定义及验证报告保存在 outputs/db-migrations/20261018_wechat_login/。

邮箱、微信字段都保留在原 `tenant_user`，未绑定字段为 NULL。不需要新增关联表。车险和门户须使用同一个微信网站应用 AppID；更换 AppID 前须另行规划身份迁移，不能直接换配置后重新注册用户。

## 2. 两个后端分别配置

通过部署环境注入，禁止将真实 AppSecret 提交到前端或仓库。

| 环境变量 | SaaS 后端 | 车险后端 |
| --- | --- | --- |
| `WECHAT_ENABLED` | `true` | `true` |
| `WECHAT_APP_ID` | 网站应用 AppID | 同一个 AppID |
| `WECHAT_APP_SECRET` | 网站应用 AppSecret | 同一个 AppSecret |
| `PORTAL_WECHAT_REDIRECT_URI` | `https://www.idatag.com/saas/wechat/callback` | 不需要 |
| `INSURANCE_WECHAT_REDIRECT_URI` | 不需要 | `https://www.idatag.com/insurance/wechat/callback` |
| `WECHAT_MESSAGE_TOKEN` | 与开放平台消息推送设置一致的随机 Token | 不需要 |
| `WECHAT_ENCODING_AES_KEY` | 与开放平台一致的 43 字符 EncodingAESKey | 不需要 |
| `INSURANCE_LOGOUT_USER_URL` | 指向车险后端既有 `/internal/session/logout-user` 的内网完整地址 | 不需要 |
| `INSURANCE_SSO_CLIENT_SECRET` | 与车险内部接口共享的高强度密钥 | 必须与 SaaS 一致 |

以上 URL 使用当前项目生产构建前缀举例，若实际域名/前缀不同请整体调整。两端回调使用独立变量，兼容同一主机或同一份环境文件部署。后端代码读取 `insurance.wechat.*`；`account-enabled:true` 已在两端默认配置，用于隔离共享模块与客服监控应用，不需要额外手动开启。

`insurance.wechat.revocation-retry-ms` 默认 30000。SaaS Redis 存储未完成的跨服务撤回待办，建议启用原有 Redis 持久化与监控；网络故障后每轮重试最多 50 项。保留原有 Redis 命名空间隔离，不要求两端共享一个会话缓存。

未设置微信凭据或开关关闭时，邮箱登录仍可使用；微信面板显示不可用提示。数据库增量仍须在发布代码前完成，因为个人资料接口增加了微信绑定状态字段。

## 3. 微信开放平台后台

1. 网站应用已审核并取得微信登录能力；两端使用同一网站应用。
2. 授权回调域配置为实际域名，例如 `www.idatag.com`。两个前端回调必须落在允许域名下，不能填写后端事件地址作为 OAuth redirect_uri。
3. 在消息推送设置中配置服务器 URL：`https://www.idatag.com/saasback/portal/auth/wechat/events`。
4. 配置与 SaaS 后端相同的 Token、EncodingAESKey，选择**安全模式**。支持加密外层/内层 XML 或 JSON；不接受明文撤回 POST。接收地址验证 GET 支持官方普通 echostr 和加密 echostr。
5. 服务器保持时间同步。推送传输 timestamp 与本机时间差不可超过十分钟；CreateTime 用于旧会话撤销边界。微信事件秒级时间会将同一秒建立的会话视为旧会话，用户必要时重新登录即可。

官方参考：[网站应用微信登录](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.html)、[消息推送服务器设置](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/message_push.html)、[授权用户信息变更](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/authorization_change.html)。本次环境未能抓取微信官方正文，正式启用前请用后台真实消息联调安全模式。

## 4. 前端与反向代理

沿用现有 `VUE_APP_API_BASE_URL`、`VUE_APP_PUBLIC_PATH`。车险端新增可选 `VUE_APP_PORTAL_URL`，用于“注册账号”跳转门户，生产默认 `/saas`，本地默认 `http://localhost:8887`。跨域部署时填写门户完整根地址，不包含 `/login`。

反向代理须将 `/saas/wechat/callback`、`/insurance/wechat/callback` 回退到各自 SPA index.html；刷新和微信顶层跳转均应可打开。`/saasback/portal/auth/wechat/events` 转发给 SaaS 后端，GET/POST、原始请求体和签名 query 均保留。车险内部会话接口仅允许 SaaS 服务访问，并携带共享密钥。

二维码使用官方 `https://res.wx.qq.com/connect/zh_CN/htmledition/js/wxLogin.js`，如有 CSP，允许对应脚本域和 `https://open.weixin.qq.com` iframe，并允许后端连接 `https://api.weixin.qq.com`。浏览器使用 crypto.subtle，因此正式部署必须 HTTPS（localhost 调试例外）。两端需在原标签页完成 OAuth 回调，浏览器 verifier 放在 sessionStorage，复制回调链接到另一个标签页会被拒绝。

官方 iframe 自行展示扫码及待确认状态，不伪造“扫码成功”。参考图中的“自动创建企业工作台”改为“自动创建个人账号”，不会自动创建企业或授予角色。JWT 从完成接口 JSON 返回，不放在跳转 URL，也不保存微信 access_token/refresh_token。

二维码的默认「微信登录」标题及公司说明通过官方 `href` 样式参数隐藏，扫码确认和失效提示仍由微信展示。两端 `public/wechat-login.css` 会复制到构建根目录，发布时必须连同完整 dist 一起上传，保证 `/saas/wechat-login.css`、`/insurance/wechat-login.css` 可匿名访问并返回 CSS，不能被 SPA 回退为 HTML。个人中心点击「绑定新的登录方式」后在对话框中展示绑定内容，关闭会销毁二维码；登录页注册说明按语义分为两行。

## 5. 撤回及再次登录

撤回回调验签解密成功后，仅撤销两端在事件时间之前的会话；不改变账号、绑定、姓名、邮箱、企业或业务数据。当前会话无论由邮箱还是微信登录，都按此账号级规则处理。浏览器不接受主动强退通知，下次请求遇到失效 JWT 时沿用原 401 清理与登录页跳转。

重新扫码授权后按保留的微信身份进入原账号并签发新 JWT。重复或延迟事件及远端重试按截止时间判断，不清除更晚建立的会话；旧的在途请求也不能通过 Redis 续期重新创建已撤销会话。首次绑定冲突返回 409，不替换绑定或合并账号。账号被禁用/软删除时不允许重新注册相同微信身份。

该资料保留行为按本次明确业务要求实现；它与平台对撤回资料的处理要求是分别需要运营方确认的事项。

## 6. 验证与联调

- 两个前端执行 `npm run lint -- --no-fix`、`npm run build`。
- 后端无需外部服务的测试：`mvn -o -pl saas,insruance -am test "-Dtest=*Test,!MainTest,!UpstreamTest,!WrapperUtilTest,!InsuranceSystemApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=false"`。
- 配置真实微信凭据后验证：门户首次扫码注册/重复扫码、昵称不可用默认姓名、邮箱与微信互补绑定、占用冲突、车险未知账号拒绝、企业与套餐拒绝、取消授权、重复回调及跨服务失败重试。
- 原有四个 SpringBootTest 需要真实数据库等基础设施，当前沙箱连接受限；本次没有通过真实微信授权和服务器数据库迁移验证，不能将 Mock 或单元测试视为真实扫码成功。

接口契约见 `docs/api/wechat-login-api.md`，Mock 见两端 `src/mock/wechatMock.js`。

本次验证结果：两个前端 lint 和生产构建通过；上述离线后端命令通过车险 48 项、门户 71 项，共 119 项测试，其中微信相关 12 项。构建保留已有资源体积和 Browserslist 数据陈旧提示。完整 SpringBootTest 在最初开发验证时因环境禁止数据库网络连接而中止。2026-10-18 已完成本地增量迁移，服务器迁移及真实微信联调仍未完成。本地浏览器连接预览服务超时，未完成截图视觉验收。

## 7. 实现位置

- 门户登录/回调：`systemportal/src/views/AuthPage.vue`、`WechatCallbackPage.vue`；个人资料：`views/user/UserCenterPage.vue`。
- 车险登录/回调：`frontend/src/page/LoginPage.vue`、`WechatCallbackPage.vue`；个人资料：`components/PersonalCenter.vue`。
- 两端二维码组件、API、Mock：各自 `src/components/WechatLogin.vue`、`src/api/wechat.js`、`src/mock/wechatMock.js`，路由与既有请求白名单已同步。
- 共享协议与账号：`backend/common/src/main/java/com/example/insurancesystem/security/wechat/`；会话比较删除/续期在 `RedisCache`、`SingleLoginSessionManager`。
- 门户入口：`PortalWechatController`、`WechatEventController`、`WechatRevocationService`；复用 `PortalAuthServiceImpl` 和 `InsuranceSessionClient`。
- 车险入口：`WechatLoginController`；复用 `LoginServiceImpl`，增加按用户 ID 聚合企业权限查询以及内部按事件时间撤销会话。
- 现有邮箱资料查询增加微信绑定状态，邮箱业务规则未改；配置模板在 `backend/wechat.env.example`，实际项目仍使用原有部署环境加载方式，不会自动加载该示例文件。

## 8. 微信调用失败的日志排查

### 共享 .env 的回调变量冲突

旧版微信配置使用 `insurance.wechat.redirect-uri`。Spring 的环境变量等价名称匹配会将
`INSURANCE_WECHAT_REDIRECT_URI` 直接识别为 `insurance.wechat.redirect-uri`，其优先级高于
SaaS YAML 中的 `${PORTAL_WECHAT_REDIRECT_URI:...}`。因此两端加载同一份 `.env` 时，
即使门户变量正确，SaaS 仍会返回车险回调。此行为已由 `WechatPropertiesTest` 使用真实
Spring 属性源复现；不是 Nginx 修改了响应，也不是 systemd 未加载环境文件。

代码现已将内部属性改为 `insurance.wechat.callback-uri`，由两端 YAML 分别引用原有的
`PORTAL_WECHAT_REDIRECT_URI` 与 `INSURANCE_WECHAT_REDIRECT_URI`。两端仍可共用同一份 `.env`，
无需改动变量名；对外 start 接口继续返回 `data.redirectUri`，前端与 Mock 契约不变。
如果使用外部 application.yml 或自定义属性覆盖，需将其中的 `insurance.wechat.redirect-uri`
同步改为 `insurance.wechat.callback-uri`，旧属性不再参与绑定。

重新构建并部署 SaaS、车险两个后端（包含更新后的 common 模块），前端与数据库无需更新。
先前排查时添加的 SaaS systemd 临时覆盖需清理。执行 `systemctl edit idatag@saas.service`，
在有效编辑区域删除之前添加的以下内容，保留其他已有自定义设置：

```ini
[Service]
ExecStart=
ExecStart=/usr/bin/java -jar /usr/idatag/saas-0.0.1-SNAPSHOT.jar --insurance.wechat.redirect-uri=https://www.idatag.com/saas/wechat/callback
```

若添加过 `UnsetEnvironment=INSURANCE_WECHAT_REDIRECT_URI`，也一并删除。这样恢复原服务模板
启动方式及 `/usr/idatag/.env` 的完整环境注入。无需修改模板 `idatag@.service`，无需修改 Nginx。
注意：`systemctl edit` 清空新编辑内容可能取消本次编辑并保留旧覆盖，不能据此确认已删除。
若该实例 `override.conf` 只含本次排查设置，可检查后直接删除
`/etc/systemd/system/idatag@saas.service.d/override.conf`；有其他配置则仅删上述临时指令。
执行 `systemctl cat idatag@saas.service` 确认不再有临时指令，随后执行 `systemctl daemon-reload`、
重启两端后端，分别生成新二维码检查 start 响应的回调路径。

`WechatPropertiesTest` 读取两端实际 YAML，覆盖共享双变量、门户变量缺失及车险变量缺失，
保证各端仅使用自己的变量或本地默认地址，不再跨端取值。

### 调用失败定位

更新并重新部署车险、SaaS 后端后，重新扫码复现。旧版本已丢弃的异常无法从旧日志恢复。
无需新增 `.env` 配置；客户端使用 ERROR 记录身份交换异常、WARN 记录微信业务拒绝或昵称降级。
日志沿用现有控制台/进程管理器配置，本次不会自动创建日志文件。

前端失败提示及响应 `data.traceId` 带排查编号，在实际后端日志文件中搜索：

```sh
grep -n -A 35 'traceId=页面上的排查编号' /实际路径/后端日志文件
grep -n -A 35 'WECHAT_AUTH_FAILURE' /实际路径/后端日志文件
```

使用 systemd 时可通过 `journalctl -u 实际服务名 --since "15 minutes ago" --no-pager` 查看；
使用 Docker 时可通过 `docker logs --since 15m 实际容器名` 查看。以实际部署方式为准，
车险扫码优先查车险后端，门户扫码优先查 SaaS 后端。

诊断字段：`stage=access_token` 表示身份交换失败，会拒绝登录；`stage=userinfo` 为昵称查询，
失败仍允许有效身份继续登录，姓名由既有业务生成或保留。`elapsedMs` 是该阶段调用耗时。
已取得响应时同时记录 `httpStatus` 与不含参数的 `contentType`；网络错误尚未取得响应时
状态可能为 null、类型为 unknown，这不等于 HTTP 本身返回了空状态。

微信身份交换和昵称查询现均先读取原始字节再显式解析 JSON，兼容 `text/plain` 或缺失
Content-Type 的合法 JSON，并保留 UTF-8 中文。该适配仅作用于微信客户端，不修改全局
HTTP 转换器；JSON 仍须为完整的单个对象，业务错误与身份字段仍按原规则校验。
解析失败时不会记录原文，因此响应中可能存在的令牌、身份信息和代理页面内容均不进入日志。

| category | 原因及检查方向 |
| --- | --- |
| DNS | 无法解析 api.weixin.qq.com，检查服务器或容器 DNS |
| CONNECT | 无法建立连接，检查出口、防火墙、代理和路由 |
| TIMEOUT | 连接或读取超时，结合异常链、耗时检查出口网络；当前连接上限 4 秒、读取上限 6 秒 |
| TLS | HTTPS 握手/证书异常，检查 Java 信任库、系统时间和出口代理 |
| HTTP | 微信端点返回非成功 HTTP 状态，查看 httpStatus |
| RESPONSE_PARSE | 空响应、HTML、损坏 JSON、非对象或尾随内容，结合 httpStatus 与 contentType 检查 |
| RESPONSE_CONTENT_TYPE | HTTP 客户端定制转换器无法读取响应，记录实际状态和媒体类型，不再归为 UNKNOWN |
| CONFIG_OR_REQUEST | 请求构造或配置异常，核对实际加载的配置 |
| WECHAT_RESPONSE | 微信返回业务错误，查看 wechatErrcode；0/null 表示响应缺字段、为空或用户信息身份不一致 |
| UNKNOWN | 根据 causedBy 异常类型和随后的代码位置继续定位 |

在相同服务器/容器中运行以下无凭据探测可初步检查 DNS、TLS 和 HTTPS 可达性：

```sh
curl -v --connect-timeout 5 --max-time 10 'https://api.weixin.qq.com/sns/oauth2/access_token'
```

未传参数返回微信错误 JSON 是预期现象，只能证明该命令环境可达；Java 证书库、代理及真实授权仍需根据日志验证。
不要开启 HTTP wire 或 RestTemplate DEBUG 请求日志：授权参数位于 URL，可能泄露 AppSecret、code、token。
本次诊断仅输出固定端点、数值状态码、响应媒体类型、异常类型及堆栈位置，刻意不输出异常原文、响应体及身份资料。

响应兼容修复需重新部署 SaaS 与车险后端（同时包含更新后的 common），无需调整前端、
数据库、环境变量或 Nginx。使用 `WechatClientTest` 覆盖各媒体类型、UTF-8 昵称、HTTP 错误、
空白/HTML/损坏 JSON、微信业务拒绝、昵称降级及日志不泄露凭据；真实授权仍需部署后扫码验证。
