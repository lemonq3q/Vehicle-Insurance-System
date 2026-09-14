# 监控仪表盘 API

所有接口均需监控后台 JWT，统一响应外壳为 `{ code, msg, data }`。接口按页面独立筛选区域拆分；每个接口在后端固定执行一条聚合 SQL，首次加载由前端并行调用，不存在逐企业查询。

## GET `/monitor/dashboard/summary`

用途：读取五张顶部卡片本月值、上月值和环比。一次 SQL 分别聚合企业、企业日统计与已支付充值三个数据源。

```json
{
  "code": 200,
  "data": {
    "enterprise": { "current": 26, "previous": 23, "comparison": { "direction": "UP", "rate": 13.0 } },
    "workorder": { "current": 2860, "previous": 2520, "comparison": { "direction": "UP", "rate": 13.5 } },
    "recharge": { "current": 428600.00, "previous": 396000.00, "comparison": { "direction": "UP", "rate": 8.2 } },
    "request": { "current": 826420, "previous": 751300, "comparison": { "direction": "UP", "rate": 10.0 } },
    "ocr": { "current": 98640, "previous": 91220, "comparison": { "direction": "UP", "rate": 8.1 } },
    "updatedAt": "2026-08-24T17:40:00"
  }
}
```

`direction` 为 `UP / DOWN / FLAT / NEW`。上月为零且本月非零时 `rate=null`。企业表没有删除时间，因此上月企业数表示“当前仍存在企业中，上月月末已经创建的数量”，不包含后来软删除的历史企业。

## GET `/monitor/dashboard/recharge-trend`

用途：固定返回包含本月在内近 12 个月已支付充值订单金额，不接受其他图表范围参数。

```json
{ "code": 200, "data": { "interval": "MONTH", "points": [{ "label": "2025-09", "value": 246000.00 }] } }
```

## GET `/monitor/dashboard/usage-trend`

Query：

| 字段 | 类型 | 必填 | 可选值 |
| --- | --- | --- | --- |
| metric | string | 是 | `request`、`ocr` |
| range | string | 否 | `7d`、`15d`、`30d`、`3m`、`6m`、`1y`，默认 `30d` |

粒度：7/15/30 天按日，3 个月按周，6 个月和 1 年按月。无数据周期由后端补零。

```json
{ "code": 200, "data": { "range": "3m", "interval": "WEEK", "points": [{ "label": "2026-06-01", "value": 185200 }] } }
```

非法指标或范围返回 `400`。

## GET `/monitor/dashboard/enterprise-ranking`

Query：`range` 同趋势接口；`top` 仅支持 `5 / 10 / 20`，默认 5。按指定区间系统调用总量倒序。

```json
{
  "code": 200,
  "data": {
    "range": "30d",
    "top": 5,
    "items": [{ "enterpriseId": 1, "enterpriseName": "杭州示例企业", "requestCount": 98620 }]
  }
}
```

## SQL 与索引约束

- 卡片：1 条 SQL，三个单行子查询分别扫描 `tenant_enterprise`、`monitor_enterprise_daily_usage`、`saas_recharge_order`；本月工单直接复用 `processed_workorder_count` 已处理工单终值。
- 每张趋势：1 条 SQL；排行：1 条 SQL；12 月充值：1 条 SQL。
- 时间条件始终使用原始 `stat_date/paid_at >= ? AND < ?`，日期函数只用于 SELECT/GROUP BY，不包裹 WHERE 列。
- 用量命中 `uk_monitor_enterprise_usage_day(stat_date, enterprise_id)`；充值命中 `idx_recharge_paid_rollup(deleted,status,paid_at,enterprise_id)`；迁移仅新增企业比较所需的 `(deleted,created_at)` 聚合索引。
