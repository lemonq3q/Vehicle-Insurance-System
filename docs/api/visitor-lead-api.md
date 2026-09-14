# SaaS 官网游客信息 API

## 统一响应

```json
{ "code": 200, "msg": "提交成功", "data": {} }
```

## POST /portal/visitor-leads

官网“联系我们”匿名提交接口，无需 JWT。前端不会附加门户认证头，后端 JWT 过滤器也明确跳过该 POST，因此未登录游客以及浏览器中残留过期登录凭据的访问者均可提交。

服务端必须校验浏览器 `Origin`，缺失时回退校验 `Referer` 的站点来源；允许来源由 `VISITOR_LEAD_ALLOWED_ORIGINS` 配置。通过来源校验后，以“规范化来源站点 + 客户端 IP”的 SHA-256 摘要作为限流键，默认 60 秒只允许一次请求取得提交资格。限流发生在业务字段校验和数据库写入之前，因此字段校验失败的请求也会占用当前窗口；窗口内重复请求返回 429。Redis 用于多实例共享限流状态，Redis 不可用时降级为当前应用实例的内存限流。部署在可信反向代理后需显式设置 `VISITOR_LEAD_TRUST_PROXY_HEADERS=true`，此时客户端 IP 优先取 `X-Forwarded-For` 第一项、其次取 `X-Real-IP`；默认不信任代理头并使用直连地址，防止客户端伪造 IP 绕过限制。

请求头：

| 名称 | 必填 | 说明 |
| --- | --- | --- |
| `Content-Type` | 是 | `application/json` |
| `Origin` | 浏览器请求是 | 必须命中服务端白名单；同源环境缺失时可用 Referer 验证 |

请求体：

| 字段 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `name` | string | 是 | 姓名，最多 100 字符 | `陈先生` |
| `contact` | string | 是 | 手机号、微信号或邮箱，最多 100 字符 | `13800138000` |
| `roleCode` | string | 否 | `OPC_AGENT`、`CAR_DEALER`、`INSURANCE_AGENCY`、`OTHER` | `CAR_DEALER` |
| `expectedMonthlyOrders` | integer | 否 | 填写时为 0～100000000 | `320` |
| `intentCodes` | string[] | 否 | 可多选；`TRIAL`、`DEMO`、`CUSTOM_COOPERATION` | `["TRIAL","DEMO"]` |
| `remark` | string | 否 | 备注，最多 1000 字符 | `希望了解多门店协作` |

成功响应：

```json
{ "code": 200, "msg": "提交成功", "data": { "leadNo": "VL20260828A7K3M9Q2X5" } }
```

常见错误：

| code | 信息 | 条件 |
| --- | --- | --- |
| 400 | 字段校验信息 | 缺少必填字段、枚举无效或长度超限 |
| 403 | 请求来源不受信任 | Origin/Referer 缺失或未命中白名单 |
| 429 | 提交过于频繁，请稍后再试 | 同一来源仍处于限流窗口 |
| 500 | 游客编号生成失败，请稍后重试 | 连续三次编号唯一键冲突 |

Mock 示例与真实响应结构一致，位于 `systemportal/src/mock/portalMock.js`。

## GET /monitor/visitor-leads

监控系统游客信息分页查询，必须携带有效监控平台 JWT。只支持游客编号查询；不提供姓名、联系方式、角色等额外条件。

Query 参数：

| 字段 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `leadNo` | string | 否 | 完整编号，精确匹配，最多 32 字符 | `VL20260828A7K3M9Q2X5` |
| `pageNo` | integer | 否 | 页码，默认 1 | `1` |
| `pageSize` | integer | 否 | 每页 1～100，默认 10 | `10` |

返回数据：

```json
{
  "code": 200,
  "data": {
    "list": [{
      "id": 1,
      "leadNo": "VL20260828A7K3M9Q2X5",
      "name": "陈先生",
      "contact": "13800138000",
      "roleCode": "CAR_DEALER",
      "expectedMonthlyOrders": 320,
      "intentCodes": ["TRIAL", "DEMO"],
      "remark": "希望了解多门店协作",
      "createdAt": "2026-08-28 10:35:20"
    }],
    "pageNo": 1,
    "pageSize": 10,
    "total": 1
  }
}
```

排序规则：`created_at DESC, id DESC`。Mock 数据位于 `monitor-frontend/src/mock/database.js`，路由位于 `monitor-frontend/src/mock/adapter.js`。
