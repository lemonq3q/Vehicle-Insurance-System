# 工单查询关联索引优化与验证

## 实施范围

已在本地 `insurance_saas` 完成索引优化。仅新增17个非唯一联合索引，均为
`idx_20261103_workorder_link(enterprise_id, workorder_id, deleted)`。
未修改PageHelper全局配置、自动COUNT、Service/Mapper查询、前端分页、权限、接口或返回格式；无API/mock契约变更。
未修改旧库insurance，也未连接或修改云端数据库。

| 表类型 | 在线表 | 归档表 |
| --- | --- | --- |
| 报价、核保、物流 | 各1个 | 各1个 |
| 行驶证、合格证、发票 | 各1个 | 各1个 |
| 工单附件、工单险种明细 | 各1个 | 各1个 |
| 佣金 | 已有等效索引，保留 | 新增1个 |
| 支付 | 已有等效索引，保留 | 已有等效索引，保留 |

未删除原有按完成时间、统计、计费等用途的索引，也未新增唯一约束。
主表已有enterprise_id/deleted/created_at相关索引，没有重复添加。

## 结果与耗时

优化前在同一MySQL实例、完整恢复本地数据的演练库记录基线；优化后在本地正式库运行同形状查询复核。数据相同，但缓存和统计状态可能不同，单次计时用于确认量级，不能作为生产SLA。时间均为EXPLAIN ANALYZE数据库内部时间，不含HTTP/前端及命令行启动。

| 场景 | 优化前COUNT | 优化后COUNT | 优化后取当前页 |
| --- | ---: | ---: | ---: |
| 无条件管理员 | 5122 ms | 136 ms | 0.688 ms |
| 普通处理人限制 | 1497 ms | 36.4 ms | 0.819 ms |
| 状态筛选 | 4684 ms | 110 ms | 0.455 ms |
| 创建时间区间 | 1599 ms | 22.2 ms | 0.288 ms |
| 完成时间区间 | 32.7 ms | 64 ms | 0.832 ms |
| 关键词 | 4795 ms | 84.8 ms | 13.3 ms |
| 处理人+状态+时间 | 468 ms | 16.8 ms | 0.417 ms |
| 第51页 | 4846 ms | 110 ms | 43.5 ms |

无条件COUNT改善约38倍。核保关联从每次扫描3675个索引条目、重复3675次，变为按企业/工单/删除标记直接查找，实际每次返回1条。报价、物流及车辆表也使用了新关联索引。
完成时间区间COUNT本来已较快，本次单次复核由约33ms波动到64ms，未声称所有场景都严格更快；原完成时间索引仍在，后续可持续观察该场景。

## 正确性与回退验证

- 完整备份保存在 `outputs/index-optimization/20261103-002852/insurance_saas_before_migration.sql`。
- 索引变更前后72张表的完整行四段SHA256聚合指纹一致，没有业务数据变化。
- 8组查询总数一致；将完整匹配结果按唯一ID排序后，所有字段的结果SHA256一致。
- 当前页数量、ID顺序和分页边界一致，没有观察到同一创建时间导致的顺序变化。
- 所有原索引完整保留，仅增加17个非唯一索引。
- 演练库重复执行迁移17项全部跳过，无重复索引。
- 演练库执行回退后精确恢复原索引布局，再次迁移可恢复17项新增索引。
- 演练库已删除；SQL、执行计划、计时、备份和核验记录保留。

未启动浏览器端到端测试；应用业务代码未变，验证覆盖重建的当前Mapper/PageHelper SQL及数据库数据。
新增索引会占用一定空间，并增加对应明细写入时维护索引的成本。DDL采用INPLACE/LOCK=NONE，不支持时失败，不静默降级；开始/结束仍需短暂元数据锁，云端应在低峰执行。

## 云端增量部署

迁移脚本：`backend/db/migration/V20261103_01_add_workorder_relation_indexes.sql`。
先备份云端数据库，将脚本上传服务器，再针对正确数据库执行：

```bash
mysql -u root -p insurance_saas < V20261103_01_add_workorder_relation_indexes.sql
```

该脚本不包含业务DELETE/UPDATE，不覆盖现存云端数据。会检查InnoDB、目标字段和已有索引前缀；有等效索引则跳过。执行后应再核对云端实际计划及耗时。

回退脚本：`backend/db/migration/R20261103_01_rollback_workorder_relation_indexes.sql`。
本地17项全部新增，回退脚本已实测；云端若有部分索引被跳过，只回退其执行日志中实际新增的索引，不能盲目删除原有等效索引。

## 最新完整SQL

`base_conf/database/insurance_saas_optimized_full_20261103_003411.sql`

约19.5MB，包含72张表、56个触发器及17个新增索引；导出正常结束。适合初始化或明确需要完整恢复的环境。若云端已有新增业务，应使用上述增量脚本，不用本地快照覆盖云端业务数据。

## 代码与证据

- SQL：V20261103_01新增索引、R20261103_01回退。
- 执行/验证：`backend/db/migration/optimize_workorder_relation_indexes.py`。
- 全部证据：`outputs/index-optimization/20261103-002852/`。
- `production_verification.json`：72表数据与查询一致性。
- `production_benchmarks.json`：正式库各场景计时与结果指纹。
- `production_*_explain.txt`：正式库执行计划。
- `rollback_verification.json`：演练回退及重新应用结果。
- `export_verification.json`：完整SQL大小、索引数量及SHA256。
