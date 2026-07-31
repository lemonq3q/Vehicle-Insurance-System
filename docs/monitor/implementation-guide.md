# 运营监控平台实施指导文档

> 状态：数据库基线已落地（2026-07-22）  
> 本文是后续“静态 UI → Mock 前端与 API 文档 → 后端 → 联调”的实时实施台账。每完成一个阶段，都应更新本文的状态、决策记录、接口链接和验证结果。

## 1. 已确认的项目现状

### 1.1 工程结构

- `frontend/`：车险出单前端，Vue 3 + Element Plus。
- `systemportal/`：SaaS 门户前端，Vue 3 + Layui。
- `backend/common/`：两套后端共享的安全、Redis、统一响应、维护模式等能力。
- `backend/insruance/`：车险出单后端，端口 8080，连接 `insurance_saas`。
- `backend/saas/`：SaaS 门户后端，端口 8081，连接 `insurance_saas`。
- 新监控后端应新增 `backend/monitor/` Maven 模块；新监控前端建议新增 `monitor-frontend/`，沿用 `systemportal` 的 Vue 3、Vue Router、Vuex、Axios 和 Layui 组织方式。

### 1.2 真实数据库核对结果

本次通过 `mysql_ops` 只读核对 `insurance` 与 `insurance_saas`。当前运行代码已统一使用 `insurance_saas`；旧 `insurance` 仅有旧版车险表，不作为新平台的数据源。

当前 `insurance_saas` 数据量：企业 2、企业用户 5、套餐 3、充值订单 7、订阅订单 4、工单 21、平台用户 0、OCR 明细 0、日/月用量汇总均为 0。

可直接复用的业务表：

| 领域 | 真实表 | 监控平台用途 |
| --- | --- | --- |
| 企业与成员 | `tenant_enterprise`、`tenant_user`、`tenant_member` | 企业列表、员工列表、总数统计 |
| 套餐 | `saas_plan`、`saas_subscription`、`saas_order` | 套餐维护、企业当前套餐、订阅订单 |
| 资金 | `saas_wallet`、`saas_recharge_order`、`saas_wallet_transaction` | 余额、充值订单、流水、月流水 |
| 车险业务 | `biz_workorder` | 仅用于聚合企业出单量，不向监控平台提供工单明细 |
| OCR | `biz_ocr_record` | OCR 明细（当前业务代码尚未写入） |
| 监控草表 | `platform_user`、`platform_user_role`、`saas_usage_*` | 两套现有系统未引用且均为空，迁移时删除重建为独立 `monitor_*` 表族 |

### 1.3 已发现的缺口

1. `RequestCountFilter` 只统计维护模式下的活动请求，不记录企业请求累计量。
2. `OCRServiceImpl` 未记录 Redis 用量，也未写 `biz_ocr_record`。
3. 原日/月用量草表采用 EAV 结构且只有主键；本需求只有三个固定指标，继续复用会增加 upsert、聚合和图表查询复杂度。
4. 没有“每日系统汇总”“每月充值流水”“后台敏感操作审计”和“任务执行记录”表。
5. 原平台账号表没有被现有系统引用，且与租户权限模型边界不清晰。
6. 现有凌晨 4 点 SaaS 维护任务没有监控统计步骤，也没有多实例互斥。

数据库适配脚本见 `backend/db/migration/V20260722_01_prepare_monitor_schema.sql`。脚本已于 2026-07-22 执行：删除未使用且为空的 `platform_user*`、`saas_usage_*` 草表，重建为职责清晰的 `monitor_*` 表；现有两套系统使用的业务表未删除或重建。

### 1.4 数据库重建设计边界

通过两套后端的 Mapper XML、MyBatis 注解 SQL、实体 `@TableName` 和维护代码交叉检查后，边界如下：

- 必须保留：`tenant_*`、`auth_role`、`auth_permission`、`auth_role_permission`、`saas_plan`、`saas_subscription`、`saas_order`、`saas_wallet`、`saas_recharge_order`、`saas_wallet_transaction`、`biz_*`、`sys_file` 及其现有归档表。
- 可以重建：`platform_user`、`platform_user_role`、`saas_usage_daily`、`saas_usage_monthly`、`saas_usage_archive_job` 及对应未使用归档草表。
- 新监控表：`monitor_user`、`monitor_role`、`monitor_user_role`、`monitor_enterprise_daily_usage`、`monitor_system_daily_stat`、`monitor_monthly_revenue`、`monitor_job_execution`、`monitor_operation_log`。

监控账号不再复用 `auth_role`。`auth_role/auth_permission` 继续只服务现有租户和车险权限，避免监控平台角色改动影响两套线上系统。

## 2. 关键业务口径

### 2.1 统计口径

- 出单量：企业在统计日内创建且 `deleted=0` 的 `biz_workorder` 数；实时计数在工单创建事务成功后递增。
- 系统访问次数：通过鉴权且已识别 `enterpriseId` 的业务后端请求数。默认排除健康检查、静态资源、登录、监控平台自身接口和内部 SSO 接口。
- OCR 使用次数：一次实际调用 OCR 供应商计一次，无论成功失败；校验失败且未调用供应商不计。
- 月流水：仅 `saas_recharge_order.status=2`，按 `paid_at` 归属月份，求 `amount`；不统计 `saas_order` 套餐订阅，也不按钱包流水推导。
- 总企业数：`tenant_enterprise.deleted=0`；状态正常/限制/停用都属于现存企业。
- 总用户数：`tenant_user.deleted=0`；不包含 `platform_user` 和非登录型商户人员。

### 2.2 统计日边界

按需求，Redis 在每日 04:00 轮换，因此统计日定义为北京时间 `[当日 04:00, 次日 04:00)`，图表日期标记为区间起始日。这样不会丢失 00:00–04:00 的计数。若产品需要自然日 `[00:00,24:00)`，必须在开发前变更 Redis key 轮换策略。

### 2.3 Redis key 协议

所有写入使用原子 `HINCRBY`；所有 key 显式使用 `Asia/Shanghai` 统计日。

```text
monitor:usage:{yyyyMMdd}:enterprise:{enterpriseId}
  workorder -> long
  request   -> long
  ocr       -> long

monitor:snapshot:enterprise-count -> long
monitor:snapshot:user-count       -> long
monitor:usage:active-day          -> yyyyMMdd
monitor:usage:flush-lock          -> distributed lock
monitor:maintenance:lock          -> distributed lock
```

不要在 04:00 直接扫描并删除所有 key。正确顺序是冻结旧统计日、完成最终覆盖写库、创建新统计日记录，再删除旧 key；失败时保留旧 key 以便补偿。

## 3. 三套后端的职责边界

| 模块 | 写入职责 | 查询/管理职责 |
| --- | --- | --- |
| `backend/insruance` | 请求量、OCR 次数、出单量递增；OCR 明细 | 不提供跨企业监控接口 |
| `backend/saas` | 门户请求量递增；充值支付成功等业务数据仍按原事务写库 | 保持现有企业自助接口 |
| `backend/monitor` | 15–20 分钟随机落库、04:00 汇总与快照；后台操作审计 | 所有跨企业查询和管理接口 |

`common` 中只放共享的计数客户端、统计日计算和企业上下文解析，不放监控业务 Controller。两个业务后端通过共享组件写 Redis，监控后端读取 Redis 并落库。

## 4. 后端定时流程

### 4.1 15–20 分钟随机覆盖写库

使用 Spring `TaskScheduler` 在每次完成后随机安排下一次执行（15–20 分钟），不要使用固定 cron。流程：

1. 获取 `monitor:usage:flush-lock` 分布式锁。
2. 读取 active-day 下全部企业计数快照。
3. 逐企业按 `(stat_date, enterprise_id)` upsert，把 Redis 当前绝对值覆盖到 `monitor_enterprise_daily_usage` 的 `workorder_count/request_count/ocr_count`。
4. 单批事务成功后记录批次；失败不清 Redis，下次覆盖即可自愈。

覆盖而非累加可保证重复执行幂等。读 Redis 到写 MySQL 期间发生的新增不会丢失，会在下一批覆盖。

### 4.2 每日 04:00 维护顺序

1. 获取全局 `monitor:maintenance:lock`，进入维护模式并等待活动请求结束。
2. 冻结旧 active-day，执行旧日最终 flush。
3. 从 `monitor_enterprise_daily_usage` 汇总旧日全系统请求量、OCR 量、出单量，写 `monitor_system_daily_stat`。
4. 按当月已支付充值订单计算月累计，覆盖 `monitor_monthly_revenue`，并写入每日快照的 `month_recharge_amount`。
5. 统计企业数、用户数写 Redis；应用启动时执行同一快照逻辑一次。
6. 为全部现存企业创建新日 `WORKORDER/REQUEST/OCR + TOTAL` 的 0 值数据库记录，切换 active-day。
7. 删除旧日 Redis 用量 key。
8. 再执行已有订阅到期、邀请码清理和软删除归档，最后退出维护模式。

任一步失败必须记录任务状态并保留旧 Redis key。生产多实例必须使用分布式锁，不能仅依赖 `@Scheduled`。

## 5. 前端信息架构与页面拆分

建议路由基址 `/monitor`，四个一级栏目如下。

### 5.1 仪表盘

- `/dashboard`：总企业数、总用户数、当月充值流水、今日出单/调用/OCR；近 7/30 天调用与 OCR 趋势；企业用量 Top 10。

### 5.2 企业管理

- `/enterprises`：企业筛选、状态、成员数、余额、当前套餐、到期时间。
- `/enterprises/:id/overview`：企业基本信息、余额调整、设置/取消套餐、三类用量趋势。
- `/enterprises/:id/members`：该企业全部登录用户/成员及状态，只读（平台非账号管理对象）。
- `/enterprises/:id/finance`：充值订单、订阅订单、钱包流水三个页签。
- `/enterprises/compare`：选择 2–8 家企业，对比出单、请求、OCR 趋势。

### 5.3 套餐管理

- `/plans`：套餐列表、上下架、排序。
- `/plans/:id/edit`：名称、说明、计费周期、时长、成员上限、价格等编辑。已产生订单的套餐历史由订单快照保证，不回写历史订单。

### 5.4 用户管理

- `/platform-users`：监控平台账号列表、创建、停用/启用、软删除。
- `/platform-users/:id`：资料与角色修改。

只有 ADMIN 可进入用户管理并调用账号写接口；CUSTOMER_SERVICE 等非管理员角色可以使用其他三个栏目。后端必须鉴权，不能只隐藏菜单。

## 6. 敏感管理动作的事务规则

- 调整余额：锁定钱包，写 `saas_wallet_transaction(type=ADJUST)`，再更新余额，并写 `monitor_operation_log`；禁止只改余额字段。
- 设置/取消套餐：更新唯一 `saas_subscription` 当前状态，必要时同步成员席位；后台赠送/调整不伪造已支付订阅订单，但必须写操作审计。具体是否生成 0 元 ADMIN_ADJUST 订单在 API 设计阶段确认。
- 修改套餐：校验价格非负、人数和时长为正；订单继续读取自身 `plan_snapshot_json`。
- 删除平台账号：软删除；禁止删除当前登录账号和最后一个启用 ADMIN。
- 所有管理写操作记录操作人、对象、动作、变更前后 JSON、原因、IP、requestId 和时间。

## 7. 分阶段实施与验收门槛

### 阶段 1：静态 UI（已完成）

- 已创建独立 `monitor-frontend/`，使用 Vue 3、Vue Router、Layui 与 ECharts。
- 已完成仪表盘、企业列表/概览/员工/财务/用量对比、套餐列表/编辑、平台用户列表/编辑页面。
- 页面数据仅用于视觉验收，不作为阶段 2 的业务 Mock 或 API 契约。
- 已在 1280×720 与 375×812 视口完成浏览器视觉检查；页面无全局横向溢出，宽表在自身容器内滚动，浏览器控制台无错误。
- `npm run lint` 通过；`npm run build` 成功，存在 Layui 图标字体和 ECharts 分包体积提示，不影响阶段 1 验收。

### 阶段 2：Mock 前端 + API 文档（已完成）

- 已依据数据库结构建立前端模型及可切换 Axios Mock 适配层。
- 已完成全部页面的异步加载、查询、分页、导出、表单校验、确认弹窗、错误反馈及敏感管理交互。
- 已在 `docs/api/monitor-api.md` 固化方法、路径、权限、请求、响应、错误码、事务要求和 Mock 示例。
- 联调时只需设置 `VUE_APP_USE_MOCK=false` 并配置后端基址。

### 阶段 3：后端（待开始）

- 在 `backend/pom.xml` 注册 `monitor` 模块，包名建议 `com.example.insurancesystem.monitor`。
- 先完成认证授权、只读查询，再完成敏感事务接口，最后接入计数与定时任务。
- 车险与 SaaS 后端只做必要的计数埋点和共享配置调整。
- 验收：单元/集成测试覆盖权限、幂等 flush、04:00 轮换、余额事务、套餐状态和最后管理员保护。

### 阶段 4：联调（待开始）

- 关闭 Mock，按 API 文档逐页联调。
- 校验 Redis 数值、数据库覆盖值、图表聚合值三方一致。
- 人工模拟 03:59–04:01 边界、任务重试、双实例抢锁和 Redis 暂时不可用。
- 验收：三套后端 build/test、监控前端 lint/build 通过，并记录未覆盖风险。

## 8. 当前决策与待确认项

已确定：复用 `insurance_saas`；只复用现有企业、资金、套餐和车险业务表；监控账号与统计表已独立重建为 `monitor_*`；监控统计日以 04:00 为边界。

进入阶段 1 前需产品确认但不阻塞数据库准备：

1. “取消套餐”是立即失效，还是当前周期结束后失效。
2. 后台设置套餐是否生成 0 元 `ADMIN_ADJUST` 订阅订单。
3. 请求量是否包含 SaaS 门户请求；本文默认车险后端与 SaaS 后端都计入。
4. 监控平台只允许读取企业出单量汇总，不提供工单、车主、车牌、险种、处理人员等业务隐私明细的查询接口或页面。

## 9. 实施日志

| 日期 | 阶段 | 结果 |
| --- | --- | --- |
| 2026-07-22 | 设计基线 | 完成仓库、真实数据库、现有定时维护与关键业务代码的只读核对；产出待审阅迁移脚本。 |
| 2026-07-22 | 数据库重建 | 执行 `V20260722_01_prepare_monitor_schema.sql`；创建 8 张 `monitor_*` 表并写入 ADMIN、CUSTOMER_SERVICE 两个角色；验证现有企业、用户、套餐、订单、工单数据量未变化。 |
| 2026-07-22 | 阶段 1 静态 UI | 创建 `monitor-frontend/` 及 11 个业务路由页面；使用 Layui 组件和 ECharts 完成静态运营后台；lint、生产构建与桌面/移动端视觉检查通过。 |
| 2026-07-25 | 阶段 1 UI 优化 | 统一按钮为 SaaS 门户的横向紧凑风格并修复表格操作按钮对齐；企业筛选项改为右侧等间距布局；企业对比增加用量占比饼图；删除无通知表支撑的通知入口及无可维护额度字段支撑的 OCR 配额文案。 |
| 2026-07-25 | 隐私边界修正 | 删除企业历史出单页面、路由和导航入口；明确监控平台只能查看出单量汇总，不得访问企业工单及车主、车辆等业务隐私明细。 |
| 2026-07-25 | 阶段 2 Mock 前端与 API 契约 | 建立 Axios API 与可切换 Mock 层；完成全部业务页面交互、分页、表单校验、导出和敏感操作确认；产出 `docs/api/monitor-api.md`。 |
