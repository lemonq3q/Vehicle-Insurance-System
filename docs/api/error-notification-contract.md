# 接口失败与顶部通知契约

## 默认规则

三个前端（车险、门户、监控）均兼容 HTTP 错误状态和 `{code,msg,data}` 业务外壳。普通请求失败由请求拦截器唯一提示：400—499 为 warning，500—599 为 error。HTTP 5xx 优先于响应体错误码，避免服务故障被较低级别掩盖。成功消息由业务页面按操作结果决定，拦截器不提示成功。

文案优先后端 `msg`，其次 `message`；有后端消息时不得改成页面自定义通用错误，包括 503。无消息、网络无响应、不可解析的网关错误使用中文兜底。401 的会话清理和跳转继续执行，不替代普通请求的失败提示；静默会话恢复可明确例外。

拦截器将失败转为 rejected Promise，保留 `error.code/data/response`，发送顶部通知后标记 `error.notificationHandled=true`。普通 catch 仅管理 loading、导航、表单保留或局部错误文本，不再弹同一消息。公共反馈函数必须检查该标记。

## 特殊接口

| 场景/接口 | 例外原因 | 提示责任方 |
| --- | --- | --- |
| `/file/oss/sign` 与 OSS 直传 | 签名、外部上传和界面回滚组成一个流程 | 上传组件；签名请求显式 skipErrorNotification |
| `/ocr/idCard` 等证件识别 | 与证件上传状态及失败恢复绑定 | 上传组件；显式 skipErrorNotification |
| `/ocr/policy`、`/ocr/policy/tasks/{jobId}` | HTTP 查询成功也可能返回 FAILED 任务结果，不应每轮重复提醒 | 保单识别流程终态失败一次，成功/部分成功静默 |
| 微信二维码请求、SSO 回调 | 登录恢复与专用界面展示错误 | 调用方使用既有 skipErrorNotification 或 silentAuthFailure |
| 门户官网套餐、游客线索提交 | 局部加载、重试或表单内错误 | 原调用方显式静默并承担界面反馈 |
| 用户取消、本地输入校验、浏览器存储、外部 URL 下载 | 无后端业务外壳 | 页面自行反馈，输入错误用 warning |

普通承保保存 `/workorder/acceptInsurance` 不属于例外，恢复为默认拦截器提示。下载接口如果收到 JSON Blob 错误，先解析业务外壳，再统一提示；正常二进制下载保持原契约。

## 验证样例

- HTTP 200 + `{"code":400,"msg":"工单状态不允许此操作"}`：warning 一次。
- HTTP 400 + 同样外壳：与上一例一致。
- HTTP 500 + `{"code":500,"msg":"服务暂不可用"}`：error 一次。
- HTTP 503 + 自定义 msg：保留后端文案；缺少文案才显示维护兜底。
- 已通知错误进入页面 catch 或监控 feedback.errorMessage：不再弹窗。
- skipErrorNotification=true：请求层不弹窗，调用方负责；标记不改变接口或权限。

错误响应没有新增请求字段或数据库字段。用 `node scripts/test-http-error-contract.js` 验证实际请求拦截器及特殊封装，所有依赖为替身，不访问网络。
