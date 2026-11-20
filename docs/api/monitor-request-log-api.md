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


## 2026-11-20 筛选契约更新（替换上文旧查询字段）

- startTime / endTime：yyyy-MM-dd HH:mm:ss，亚洲上海时间；默认当天00:00:00至23:59:59。结束秒内全部毫秒均包含，结束值小于开始值返回400。
- httpStatusClass / businessCodeClass：可选100、200、300、400、500；400表示400至499，业务码仅匹配三位数字字符串。替换旧httpStatus/businessCode精确筛选。
- uri：改为字面关键词包含匹配，%和_不作为通配符。
- minDurationMs / maxDurationMs：可选非负整数，双边包含；空值不限制该边界；下限大于上限或负数返回400。
- enterpriseId / userId：企业及真实账号ID；传userId必须同时传enterpriseId。查询只过滤日志自身记录的身份，避免历史成员变更影响历史日志；监控不查租户表。
- 其余分页、日志ID、服务、异常标记和响应结构不变。

前端示例 query：startTime=2026-11-20 10:00:00&endTime=2026-11-20 10:30:59&httpStatusClass=400&uri=workorder&minDurationMs=100&maxDurationMs=500。

### GET /monitor/request-logs/enterprise-options
Query keyword:string，按企业名包含匹配，空串返回[]。响应 {code:200,data:[{id:1,name:"示例企业"}]}，最多20条。

### GET /monitor/request-logs/user-options
Query enterpriseId:long 必填正数，keyword:string 按姓名或邮箱包含匹配，空串返回[]。响应 {code:200,data:[{id:2,realName:"示例用户",email:"user@example.com"}]}，最多20条，id为tenant_user主键。前端未选企业时禁用，切换企业清空用户。

两接口仅ADMIN可访问；监控分别调用SaaS GET /internal/log-directory/enterprises、/users，相同参数。内部header X-Maintenance-Secret使用maintenance.participant.internal-secret，两侧一致。内部凭证无效403，参数错误400，SaaS依赖失败监控返回502，统一请求层提示一次。无静默例外。

monitor.request-log.saas-base-url 默认 http://localhost:8081，可通过MONITOR_REQUEST_LOG_SAAS_BASE_URL覆盖。内部接口由SaaS独占查询tenant_enterprise、tenant_member、tenant_user；不改变任何成员/账号业务规则。


### 时间范围清除语义
前端日期范围支持组件原生清除按钮。初次进入及重置仍选当天；清除后省略startTime/endTime，后端不添加时间条件，查询全部历史范围。单边传值仅约束该边界；空字符串同省略。分页上限与其他筛选保持不变。mock同步支持无时间条件。
