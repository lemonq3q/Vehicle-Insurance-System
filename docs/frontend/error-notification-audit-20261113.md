# 顶部通知与状态码检查（2026-11-13）

## 发现并处理的问题

| 位置 | 原因及影响 | 处理 |
| --- | --- | --- |
| 车险 DetailWorkorder 承保保存 | 请求层 4xx 警告后，页面 catch 又按错误提醒；临时静默封装使普通业务自行提示 | 恢复默认请求提示，页面只在异常未通知时兜底 |
| 车险 HeaderComponent 返回门户授权 | 请求层通知后 catch 无条件弹红色错误 | 检查 notificationHandled，已提示不重复 |
| 监控 api/request 与 mixins/feedback | 请求层只抛异常，页面统一按 error 弹窗，导致 400/403/409 等也报警 | 请求层统一按状态分级；根组件订阅全局通知；feedback 忽略已通知异常 |
| 三端请求层 503 | 强制维护文案覆盖后端实际拒绝原因 | 优先显示后端消息；无文案才兜底 |
| 三端请求层 HTTP 500 与业务 4xx 冲突 | 除 503 外优先业务码，可能将真正服务器故障降为 warning/误触登录清理 | HTTP 5xx 统一优先 |
| 车险请求发送失败 | 请求拦截器和响应错误处理均可能通知 | 请求错误只向统一响应失败处理传播 |
| OSS 签名及基础证件 OCR | 上传/识别局部 catch 自行通知，而内部请求默认也通知 | 显式例外由上传组件唯一负责；不扩大普通接口静默范围 |
| 三端文件下载 | responseType=blob 时 JSON 错误外壳未识别，可能误下载错误内容或丢失 msg | 仅对 JSON 或 HTTP 失败 Blob 尝试还原错误外壳 |
| 后端 GlobalExceptionHandler | 缺参、参数类型、JSON 解析、绑定失败可能进入未知异常 500 | 增加已知请求异常 400，上传大小 413、方法 405、媒体类型 415；未知故障仍为 500 |
| 后端业务异常日志 | 所有业务异常均 log.error，4xx 也按系统异常记录 | 4xx 业务拒绝用 warn，5xx 用 error |
| 监控推广页的选择数量/必填提示、车险缺险种及承保本地校验 | 用户可修正问题使用 error 样式 | 改为 warning |
| 项目规则未版本化 | AGENTS.md 被忽略，规则不能随正常提交同步 | 增加规则章节并移除该文件的忽略规则 |

未将 OSS 故障、内部字典缺失、供应商不可用等真实服务问题降成 400。原“工单尚未进入待承保阶段”的状态拒绝本身为 400，重复与红色错误主要来自前端重复处理，并非那条后端响应使用 500。

## 保留的例外

OCR 异步终态、微信/SSO 恢复页、上传完整链路、外部下载、营销局部请求和本地校验保留专门处理。详见 ../api/error-notification-contract.md。成功提示仍由原业务页面决定。

## 文件与验证

- 主要修改：三个前端 api 请求层，监控 App.vue/notification.js/feedback.js，车险 HeaderComponent、DetailWorkorder、file.js、ocr.js；公共后端 GlobalExceptionHandler。
- 接口契约：docs/api/error-notification-contract.md；不改成功响应外壳、业务请求参数或数据库。
- mock：测试直接执行实际拦截器，Axios/通知/存储为替身，保留原各端业务 mock。
- 验证：scripts/test-http-error-contract.js 通过；三个前端 lint 通过；后端 GlobalExceptionHandlerTest、WorkorderPolicyServiceTest 通过。
- 未验证：未连接线上服务或浏览器逐页实测，不声称穷尽运行时所有自定义异常路径。
