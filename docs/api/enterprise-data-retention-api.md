# 企业车险业务资料保留与清理接口

## 业务规则

- 每日凌晨四点的分布式维护周期中，SaaS 后端在订阅到期处理完成后执行 `saas-enterprise-data-retention`。
- `saas_subscription` 每个企业保存一条当前订阅状态，`end_at` 代表该企业最近一次开通套餐的结束时间。
- 当 `end_at <= 当前时间 - retentionDays` 且 `plan_id`、`end_at` 均不为空时，SaaS 调用车险内部清理接口。
- 默认 `retentionDays=90`。从未订阅套餐的企业不会进入清理范围。
- 车险在线业务记录迁移到对应 `_archive` 表后从在线表移除，已有归档记录永久保留。
- 在线及归档文件元数据仍然保留在归档表，但对应 OSS 对象会逐个调用阿里云删除接口真实删除。
- `tenant_member`、`tenant_user`、`tenant_enterprise`、`saas_subscription`、`saas_wallet`、SaaS 订单及交易记录不参与清理。

## 内部清理接口

### `POST /internal/maintenance/enterprise-data/purge`

供 SaaS 维护任务调用，清理单个企业的车险业务资料。接口处于维护专用路径，不受维护业务请求拦截，但必须通过系统共享密钥认证，不接受终端用户 JWT 作为调用凭据。

请求 Header：

| 名称 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `Content-Type` | string | 是 | 固定为 `application/json` | `application/json` |
| `X-Maintenance-Secret` | string | 是 | SaaS、车险和协调器共同配置的维护密钥 | `dev-maintenance-secret` |

请求 Body：

| 字段 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `enterpriseId` | long | 是 | 要清理的企业主键，必须大于零 | `10001` |

请求示例：

```json
{
  "enterpriseId": 10001
}
```

成功响应：

```json
{
  "code": 200,
  "msg": "企业车险业务资料已清除",
  "data": {
    "biz_workorder_file": 12,
    "biz_workorder": 3,
    "biz_merchant": 5,
    "sys_file": 12,
    "ossObjects": 12
  }
}
```

`data` 中数据库表对应的数值是本次从在线表迁移到归档表的数量，并非从归档表永久删除的数量。`ossObjects` 是本次依次调用阿里云 OSS 删除接口的去重对象路径数量。相同企业重复调用时在线表迁移数量为零，因此接口具备幂等性；归档文件路径仍可能再次调用 OSS 删除，以确保上一次响应丢失时对象最终不存在。

## 错误情况

| HTTP/业务码 | 场景 | 处理结果 |
| --- | --- | --- |
| `400` | `enterpriseId` 缺失、非数字或不大于零 | 不执行任何清理 |
| `403` | 未提供或提供了错误的维护共享密钥 | 不执行任何清理 |
| `502` | 任一阿里云 OSS 对象删除失败 | 停止处理，数据库在线记录不迁移，下一周期可重试 |
| `500` | 归档表缺失、迁移插入失败或迁移数量不一致 | 数据库事务整体回滚；此前已成功删除的 OSS 对象保持删除，下一周期幂等重试 |

## 归档范围

车险后端迁移以下在线表的企业私有数据，并保留其 `_archive` 表记录：

- 上下游信息：`biz_merchant`、`biz_merchant_area`、`biz_merchant_staff`、`biz_merchant_staff_role`
- 工单及阶段信息：`biz_workorder`、报价、佣金、缴费、核保、物流表
- 车辆和险种信息：车辆行驶证、发票、合格证、工单险种、企业私有险种配置
- OCR 和附件信息：`biz_ocr_record`、`biz_workorder_file`、`sys_file`

## 环境配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ENTERPRISE_DATA_RETENTION_DAYS` | `90` | 套餐结束后的资料保留天数 |
| `INSURANCE_ENTERPRISE_PURGE_URL` | 开发环境为本机 `8080`，生产为车险内部地址 | 车险资料清理接口完整地址 |
| `INSURANCE_ENTERPRISE_PURGE_CONNECT_TIMEOUT_SECONDS` | `5` | SaaS 连接车险后端的超时秒数 |
| `INSURANCE_ENTERPRISE_PURGE_READ_TIMEOUT_SECONDS` | 开发 `1200`，生产 `1800` | 单个企业清理响应等待秒数 |
| `MAINTENANCE_INTERNAL_SECRET` | 生产环境必须显式配置 | 内部接口共享密钥，SaaS、车险和 C 必须一致 |

## 数据库前置变更

执行维护任务前需审核并应用：

`backend/db/migration/V20260817_01_add_ocr_record_archive.sql`

该脚本只创建 `biz_ocr_record_archive` 归档表及查询索引，不会迁移或删除现有数据。
