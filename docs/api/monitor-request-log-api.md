# 请求日志接口契约

权限：监控 ADMIN。沿用现有 token 鉴权、统一请求封装与失败顶部通知，无静默例外。
统一响应：`{ "code": 200, "data": ... }`，错误使用现有异常处理；参数错误业务400、权限403、详情不存在404、依赖/数据库异常500。客户端同时遵守 HTTP 状态与业务 code。

前端统一 baseURL 已包含监控前缀，API 封装使用 `/request-logs`；网关映射至后端 `/monitor/request-logs`，不能重复添加 `/monitor`。详情同理。

## GET /monitor/request-logs

Query 字段均可选，除日期、分页外为空表示不限：

|字段|类型|默认 / 示例|含义|
|---|---|---|---|
|startDate|string|当天，2026-11-20|开始日期，含当天零点|
|endDate|string|startDate|结束日期，含全天|
|pageNo|integer|1|页号，至少1|
|pageSize|integer|20|每页1至100条|
|sourceSystem|string|InsuranceSystem|后端应用名称，精确匹配|
|hasException|integer|0或1|是否捕获边界异常|
|httpStatus|integer|500|HTTP状态码|
|businessCode|string|500|业务返回码|
|uri|string|/workorder/list|不含query的URI，精确匹配|
|enterpriseId|integer|1|认证主体企业|
|userId|integer|2|认证主体用户|
|logId|string|UUID|单条日志ID|

排序 `started_at DESC,id DESC`；SQL参数化，默认当天，列表不加载详情大字段。

响应 mock：
```json
{"code":200,"data":{"list":[{"id":1,"log_id":"00000000-0000-0000-0000-000000000001","source_system":"InsuranceSystem","started_at":"2026-11-20 12:00:00","duration_ms":125,"method":"GET","uri":"/example","http_status":500,"business_code":"500","content_type":"application/json","has_exception":1,"enterprise_id":1,"user_id":2}],"total":1,"pageNo":1,"pageSize":20}}
```

## GET /monitor/request-logs/{logId}

Path `logId`：必填字符串，列表返回的UUID。请求体无。

```json
{"code":200,"data":{"payload":"{\"logId\":\"00000000-0000-0000-0000-000000000001\",\"requestBody\":null,\"responseBody\":\"{\\\"code\\\":500}\",\"businessInfo\":\"业务自由文本\",\"exceptionInfo\":\"异常堆栈\",\"hasException\":true}"}}
```

payload 为原始JSON字符串，包含请求、响应、耗时、异常与业务扩展。请求和响应仅采集最多64KiB文本（业务扩展最多65536字符），二进制不采集，截断有标记。无脱敏；查询仅管理员允许。HTTP错误和业务错误均不自动修改原响应。

前端 mock：`monitor-frontend/src/mock/requestLogs.js`，`VUE_APP_REQUEST_LOG_MOCK=true` 切换，仅用于本模块，默认真实API。
