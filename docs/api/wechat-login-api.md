# 微信与邮箱双登录契约

门户微信首次授权自动创建共享个人账号；车险微信入口只登录已有账号，不自动注册。绑定仅增加尚未绑定的登录方式，已占用身份返回 409，不合并、删除或转移任何账号。不获取或保存头像；首次注册的 realName 使用微信昵称，无法获取时生成「微信用户」加随机后缀，之后不随微信昵称覆盖用户编辑的姓名。

## HTTP 接口

沿用 `{code,msg,data}` 和请求头 `token: JWT`；外网前缀沿用 `/saasback`、`/insuranceback`。无分页、排序、文件或 path 参数。

| 方法、完整应用内路径 | 权限及作用 | JSON body | data |
| --- | --- | --- | --- |
| POST `/portal/auth/wechat/start`、`/auth/wechat/start` | 匿名，申请登录二维码参数 | challenge:string，必填，浏览器随机 verifier 的 64 位小写 SHA-256 十六进制摘要 | appId:string、redirectUri:string、state:string、expiresInSeconds:number |
| POST `/portal/user/profile/wechat/start`、`/auth/profile/wechat/start` | 当前 JWT，首次绑定微信 | 同上 | 同上 |
| POST `/portal/auth/wechat/complete`、`/auth/wechat/complete` | 匿名，完成登录 | code:string（微信返回，最多512）、state:string（服务端返回，32位十六进制）、verifier:string（本标签页保存的64位随机十六进制），均必填 | 与对应邮箱登录的 data 完全一致 |
| POST `/portal/user/profile/wechat/complete`、`/auth/profile/wechat/complete` | 当前 JWT，必须是发起绑定时的用户和会话 | 同上 | `{wechatBound:true}` |
| GET `/portal/user/profile`、`/auth/profile` | 当前 JWT，既有资料接口 | 无 | 在原资料上增加 wechatBound:boolean、wechatNickname:string\|null、wechatBoundAt:datetime\|null，不返回 openid、unionid、微信凭证 |
| POST `/portal/user/profile/email-code`、`/auth/profile/email-code` | 当前 JWT，无邮箱账号申请首次绑定码 | email:string | 沿用邮箱契约 |
| POST `/portal/user/profile/bind-email`、`/auth/profile/bind-email` | 当前 JWT，验证邮箱并设置登录密码 | email、emailCode、password:string | 沿用邮箱资料，含上述微信绑定状态 |

微信 redirect_uri 为各自前端 `/wechat/callback`，不是后端事件接收地址。官方 WxLogin iframe 显示二维码及扫码待确认状态，确认后顶层跳转；不伪造官方未提供的扫码事件。浏览器先移除 URL 参数，再通过既有请求实例提交 code/state/verifier，JWT 仅在 JSON 响应中返回。state 有效期 300 秒，服务端缓存 challenge、用途及绑定发起用户/sessionId；成功校验后一次消费，跨标签页、跨用途、跨账号及重放拒绝。二维码刷新产生新 state。

## 错误

400：授权取消、code/state/verifier 不合法、授权过期或已消费；401：绑定会话失效；403：账号停用、删除、绑定会话不一致或车险企业/成员/套餐不可用；404：车险微信身份不存在；409：当前用户已有不同微信或微信被其他用户占用；429：频繁申请；502：微信 API/内部车险服务异常；503：微信功能未配置。网络故障不当作未注册，用户资料 API 失败可降级生成姓名，但身份交换失败禁止登录。

## 示例与 Mock

微信身份交换的网络/HTTP/解析故障保留业务码 502，微信返回无效身份或非零 errcode 保留 400。
微信端点响应先按原始字节读取，再解析为单个 JSON 对象，兼容 `text/plain` 等非 JSON 媒体类型；
不依赖文本默认字符集，保留 UTF-8 中文昵称。空响应、HTML、损坏 JSON、非对象及尾随内容
均按解析故障拒绝登录，昵称查询的相同故障仅降级姓名。此调整不改变前端接口与 Mock 结构。
这两类失败的 `msg` 追加排查编号，`data.traceId:string` 为本次调用生成的 32 位十六进制编号；
编号对应服务器 `WECHAT_AUTH_FAILURE` 日志，不包含微信凭据。其他错误（包括账号不存在 404）不改变。
示例：

```json
{"code":502,"msg":"微信授权服务暂不可用，请重试（排查编号：0123456789abcdef0123456789abcdef）","data":{"traceId":"0123456789abcdef0123456789abcdef"}}
```

车险完成登录接口 `/auth/wechat/complete` 对未关联账号的微信保持禁止注册，返回：

```json
{"code":404,"msg":"账号不存在，请先前往企业门户注册或绑定微信","data":null}
```

车险回调页将该业务失败显示为“账号不存在”，说明该微信尚未关联已注册账号，并提示已有邮箱账号先登录后绑定，或到 SaaS 门户注册。只有登录事务且业务码为 404、消息明确以“账号不存在”开头时使用此分类；网关路径 404 不视为账号缺失。

502/503 显示“微信登录服务暂不可用”并保留实际错误原因；授权取消、过期、权限拒绝及绑定冲突分别保留服务端提示，页面标题使用“微信登录失败”或“微信绑定失败”。失败不写入 JWT、不进入工作台，也不自动注册。错误由回调页持续呈现，不重复弹全局通知；401 保留统一失效会话处理。

```json
{"code":200,"msg":"操作成功","data":{"appId":"wx_example","redirectUri":"https://portal.example.com/wechat/callback","state":"0123456789abcdef0123456789abcdef","expiresInSeconds":300}}
```

Mock 示例位于 `systemportal/src/mock/wechatMock.js`、`frontend/src/mock/wechatMock.js`，不接入生产请求，不模拟真实微信授权或签发可用 JWT。

## 撤回事件

SaaS 独占 GET/POST `/portal/auth/wechat/events`：GET 验证接收地址，POST 接收微信加密 XML/JSON 事件。query 为 timestamp、nonce、msg_signature（加密），GET 还含 echostr；仅支持安全模式 AES，校验签名、AppID、时效及数据长度。配置的 Token 是消息签名共享参数，不是用户 access_token。事件 `user_authorization_revoke` 按 AppID/OpenID 定位账号，`CreateTime` 为秒级撤回时间。

撤回不修改或删除账号、微信绑定、昵称、邮箱或任何业务资料，仅撤销 SaaS 及车险撤回时间之前的 Redis 会话。不主动通知浏览器，下次请求沿用 401 处理；重复/延迟事件不能清除事件发生后新建立的会话。SaaS 调用既有 `/internal/session/logout-user`，新增可选 body `beforeEpochSeconds:number`；不传时保留既有无条件撤销语义，传入时只撤销更早会话。内部鉴权沿用 `X-Insurance-Client-Secret`，禁止终端直接调用。远端失败返回非成功，允许微信重试；同时保存 Redis 待办并定时重试，成功后才标记事件完成。

本需求的资料保留规则来自用户明确要求；上线前由运营方自行核对微信平台撤回资料处理要求。
