# HTTP与业务错误兼容契约

## 普通业务接口

保留已有请求路径、鉴权、参数和成功响应结构。普通控制器仍可返回HTTP200及
`{"code":400,"msg":"请求参数无效","data":null}`，三个前端将它与HTTP400同等处理。
错误字段：`code`为数字或数字字符串，`msg`/`message`为可选提示，`data`为可选附加业务数据。
前端失败统一拒绝Promise，异常含`code`、`message`、`data`、`response`，页面由catch结束加载与处理失败。
真实HTTP503优先并固定提示“系统维护中，服务不可用”，不因错误body触发登录跳转；其他失败业务码优先，
没有失败业务码时采用HTTP状态，HTTP500不能被响应体code=200覆盖。
车险/SaaS的4xx走警告、5xx走错误；监控沿用页面feedback展示。401沿用各端会话失效机制，
SaaS公开接口不触发登录跳转，`skipErrorNotification`仍支持抑制全局通知。

## 维护拒绝

除既有维护协议和精确提醒同步路径之外，所有请求在维护期间均返回真实HTTP503，
响应体为`{"code":503,"msg":"系统维护中，服务不可用"}`，不进入控制器业务处理。
必要内部维护协议与精确提醒同步接口仍放行；否则协调器无法完成维护。所有对外业务入口均拒绝。
三个前端沿用上述错误处理，不自动重发可能有副作用的提交请求。

## POST /portal/payment/stripe/webhook

服务端回调接口无需用户JWT；Header `Stripe-Signature`由Stripe提供，body为原始JSON，验签后处理。
成功处理或幂等忽略返回HTTP200空body；缺少/非法签名返回400，临时不可用可返回503，未知故障500。
回调独立在StripeWebhookController，入口不捕获异常；服务主动抛出HttpBusinessException，
全局HttpBusinessExceptionHandler按异常code返回真实HTTP状态，不判断URL。
StripeExceptionHandler仅限定StripeWebhookController与StripePaymentController，优先级低于特殊异常处理器，
高于历史通用处理器；参数绑定400、权限403、共享业务异常保留错误码、未知异常500。
普通GlobalExceptionHandler不再包含Webhook路径判断。维护503发生在验签、事件记录和资金处理之前。
安全拒绝返回实际401/403，JSON写流工具不得覆盖状态为200。普通控制器历史错误外壳未整体迁移。
外部发送方应依据HTTP状态判定投递结果，而非只读取JSON的code；重复回调沿用现有业务幂等处理。

## 错误Mock及验证

`scripts/test-http-error-contract.js`加载三个真实请求模块，并模拟400、401、403、409、500、503的
HTTP200业务失败、真实HTTP失败、无错误body及成功码掩盖HTTP失败等场景；同时验证成功响应和免通知行为。
例如`HTTP200 + {"code":503,"msg":"系统维护中，请稍后再试"}`与同body的HTTP503均产生code=503异常。
后端`StripeWebhookHttpStatusTest`及`ReminderMaintenanceAccessTest`覆盖控制器与过滤器的实际HTTP状态。
本次不新增数据库结构，不改变列表、详情、分页或成功响应字段。
