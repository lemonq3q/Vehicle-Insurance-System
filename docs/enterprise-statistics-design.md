# 企业每日统计采集与维护设计

## 1. 设计结论

所有指标统一存入 `monitor_enterprise_daily_usage`，每企业每自然日一行。API、OCR 当日实时写 Redis；自然日结束后归档。盈利、新增客户、处理完成工单由每日维护任务从业务表计算。

周期统一为 `Asia/Shanghai` 自然日 `[00:00, 次日00:00)`。周、月、季度和任意区间都从日表聚合，不再维护月表。后续扩展指标时给宽表增加列，并同步计算 SQL、DTO 和接口文档。

## 2. 表结构

| 字段 | 含义 | 来源 |
| --- | --- | --- |
| `stat_date`,`enterprise_id` | 企业统计自然日，组成唯一键 | 周期解析器 |
| `request_count` | API 请求量 | Redis |
| `ocr_count` | OCR 供应商调用量 | Redis |
| `processed_workorder_count` | 完成处理工单数 | 每日 SQL |
| `new_customer_count` | 新增下游客户数 | 每日 SQL |
| `upstream_income` | 完成工单上游政策收入 | 每日 SQL |
| `downstream_cost` | 完成工单下游政策成本 | 每日 SQL |
| `profit_amount` | 上游收入减下游成本 | 每日 SQL |
| `is_finalized` | 是否为已结束自然日终值 | 维护任务 |
| `calculated_at`,`last_flushed_at`,`finalized_at` | 计算、Redis归档、终算时间 | 维护任务 |

保留现有唯一键 `(stat_date, enterprise_id)` 和查询索引 `(enterprise_id, stat_date)`。

`monitor_system_daily_stat` 可按日跨企业 `SUM`，`monitor_monthly_revenue` 既无企业维度又不等于业务盈利，因此迁移时删除。更早的 `saas_usage_daily/monthly/archive_job` 若存在也一并删除，防止新旧口径并行。迁移脚本位于 `backend/db/migration/V20260821_01_refactor_enterprise_statistics.sql`。

## 3. 指标口径

### API

统计车险和 SaaS 后端中已通过企业认证的业务请求。企业 ID 只从认证上下文取得。默认包括最终返回 4xx/5xx 的已认证请求；排除登录、静态资源、健康检查、监控端和 `/internal/**`。

### OCR

在真正调用 OCR 供应商时计数。供应商失败仍计一次；参数校验失败、缓存命中、调用前拒绝不计。异步调用必须在线程切换前捕获企业 ID。

### 盈利

建议只统计 `biz_workorder.deleted=0`、`biz_workorder_underwriting.deleted=0` 且 `finish_time` 落在当日的工单。单工单盈利为 `UPSTREAM` 四项政策费用合计减 `DOWNSTREAM` 四项政策费用合计，空金额按 0。

当前口径不含税费、退款、坏账、SaaS 套餐收入和运营成本，实施前仍需业务确认。

### 新增客户

建议定义为当日新增的下游业务商户：`biz_merchant.deleted=0`，分类方向为 `DOWNSTREAM`，按 `created_at` 归日。上游保险机构不计。若客户实际指车主，应先建立独立客户主表，不能稳定地用工单手机号或证件号临时去重。

### 处理完成工单

按 `biz_workorder_underwriting.finish_time` 归日，工单与 underwriting 均需未删除，并按 `(enterprise_id, workorder_id)` 去重。它表示当日完成量，不是当日创建量。

## 4. Redis

```text
stats:api:day:2026-08-21  { enterpriseId -> requestCount }
stats:ocr:day:2026-08-21  { enterpriseId -> ocrCount }
```

使用 `HINCRBY key enterpriseId 1`。一个自然日只有两个确定 key，维护时直接 `HGETALL`，不使用 `KEYS/SCAN`。

归档采用绝对值 `INSERT ... ON DUPLICATE KEY UPDATE` 覆盖 `request_count/ocr_count`，保证重试幂等。数据库事务成功后给旧 key 设置 7 天 TTL；失败时保留 Redis。禁止“读取后清零、数据库累加”，以免重试造成重复或丢失。

## 5. 每日维护

凌晨任务应维护刚结束的前一自然日，而不是把当前未完成日标记为终值。例如 2026-08-22 04:05 运行时，目标日是 2026-08-21。

1. 计算 `targetDate = businessDate - 1 day`。
2. 读取目标日 API/OCR Redis Hash。
3. 用集合 SQL 计算目标日客户、完成工单、上游收入、下游成本和盈利。
4. 合并所有出现过的企业；企业只有一个指标时也生成一行，其他列为 0。
5. 在一个事务中批量 upsert 全部字段，设置 `is_finalized=1` 和三个维护时间。
6. 写入现有 `monitor_job_execution`；事务提交后设置 Redis TTL。
7. 失败则整体回滚并由现有维护协调器重试。

跨日补录通过受控的 `recalculate(date, reason)` 重新覆盖计算型字段并记录操作审计。API/OCR 没有原始明细，只能在 Redis 保留期内再次归档。

## 6. 低侵入插入点

- `backend/common`：增加 `UsageMetricRecorder` 接口、Redis 实现和空实现；给 `RedisCache` 增加 Hash 原子增量方法。
- API：增加位于身份解析之后的 `EnterpriseApiUsageFilter`，集中计数和排除路径，不修改各 Controller。现有 `RequestCountFilter` 是活动请求计数，不能复用。
- OCR：把静态 `OCRUtil` 封装成 `OcrProviderClient` Bean，在唯一供应商边界通过装饰器或 AOP 计数。
- `backend/monitor`：增加日统计 Mapper、事务 Service 和 `MaintenanceTask` 配置，接入现有协调器；任务排在业务数据归档、删除之前，不另建独立 `@Scheduled`。

## 7. 查询方式

- 日趋势：按日期范围直接查询。
- 月趋势：按年月分组，对所有计数和金额字段 `SUM`。
- 企业月合计：按企业和 `[monthStart,nextMonthStart)` 汇总。
- 全平台统计：跨企业汇总同一日表。
- 当前日：已结束日读取 MySQL；未结束日 API/OCR 合并 Redis 实时值。计算型指标默认只展示最近已完成日。

建议后端接口提供 `granularity=DAY|MONTH`，由后端统一处理时区、空日期补零和金额口径。前端仍可请求每日明细，但无需固定把数月数据全部传到浏览器再聚合。

企业门户仪表盘不额外缓存：本月与上月聚合对单企业最多读取约 62 条日统计记录，属于低成本索引范围查询；续保提醒使用企业、删除状态、提醒状态和创建时间组合索引。后续只有在慢查询监控证明该接口成为热点时，才在 Service 外层增加短期 Redis 缓存，保持现有 API 响应不变。

## 8. 验证与上线

1. 确认盈利、客户、API 失败请求三个口径。
2. 备份并核对旧表行数、目标列和等价索引。
3. 备份后执行迁移并确认旧统计表已删除。
4. 测试时区边界、Redis 并发、归档重试、空指标企业、负盈利及历史重算。
5. 用原始业务 SQL 与日统计表逐企业、逐日对账。
6. 观察至少一个完整周期后，再单独迁移删除 legacy 表。
