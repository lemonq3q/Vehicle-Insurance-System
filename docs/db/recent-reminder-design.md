# 近期提醒数据库与文案模板设计

## 1. 设计依据与边界

- 企业、套餐、订阅、钱包分别沿用 `tenant_enterprise`、`saas_plan`、`saas_subscription`、`saas_wallet`。
- 套餐工单额度取订阅快照 `saas_subscription.workorder_limit`，不能直接读取可能已经变化的 `saas_plan.workorder_limit`。
- 工单使用量按当前订阅周期 `[start_at, end_at)` 内 `biz_workorder.deleted = 0` 的记录统计，具体是否仅统计已完成工单应在实现触发器前由业务确认。
- 超额单价和周期沿用 `saas.billing.workorder-overage` 配置；当前默认单价为 0.20 元、周期为 365 天。
- 欠费停用阈值沿用 `saas.billing.balance-access.suspend-threshold`；当前默认值为 -100.00 元，余额严格小于阈值时暂停套餐。
- 资料保留期沿用 `saas.data-retention.retention-days`；当前默认值为套餐结束后 90 天。
- 本设计仅新增两张提醒事实表。企业侧“已读”按企业整体处理，不细分成员；如果以后要求每个成员独立已读，需要再增加接收人关系表，不能在同一提醒行上可靠表达。
- 本文中的 SQL 是待审阅设计稿，不代表已在任何数据库执行。

## 2. 统一编码

### 2.1 提醒类型 `reminder_type`

| 编码 | 含义 |
| --- | --- |
| `AUTO_RENEW_BALANCE_INSUFFICIENT` | 已开启自动续费，但预计余额不足 |
| `SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW` | 套餐即将到期且未开启自动续费 |
| `WORKORDER_QUOTA_NEAR_LIMIT` | 工单使用量接近套餐额度 |
| `WORKORDER_QUOTA_EXCEEDED` | 工单使用量超过额度并开始产生额外费用 |
| `WALLET_BALANCE_NEGATIVE` | 钱包余额变为负数 |
| `SUBSCRIPTION_SUSPENDED_ARREARS` | 欠费超过阈值导致套餐暂停 |
| `ENTERPRISE_DATA_DELETION_APPROACHING` | 即将到达企业资料清理期限 |

### 2.2 提醒级别 `severity`

| 编码 | 使用场景 |
| --- | --- |
| `NOTICE` | 普通到期提示 |
| `WARNING` | 需要尽快处理，否则可能产生费用或影响服务 |
| `CRITICAL` | 已产生欠费、已暂停服务或资料即将清理 |

## 3. 企业门户提醒表

表名：`saas_enterprise_reminder`

```sql
CREATE TABLE `saas_enterprise_reminder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint NOT NULL COMMENT '接收提醒的企业ID，关联tenant_enterprise.id',
  `reminder_type` varchar(64) NOT NULL COMMENT '提醒类型编码',
  `severity` varchar(16) NOT NULL COMMENT 'NOTICE WARNING CRITICAL',
  `title` varchar(200) NOT NULL COMMENT '生成时渲染完成的标题快照',
  `content` varchar(2000) NOT NULL COMMENT '生成时渲染完成的正文快照',
  `business_data_json` json DEFAULT NULL COMMENT '模板变量及触发时业务数据快照，不存敏感认证信息',
  `event_key` varchar(160) NOT NULL COMMENT '幂等事件键，同一业务阶段只生成一次',
  `occurred_at` datetime NOT NULL COMMENT '业务条件首次成立时间',
  `expires_at` datetime DEFAULT NULL COMMENT '提醒失去展示意义的时间；为空表示不自动失效',
  `is_read` tinyint NOT NULL DEFAULT 0 COMMENT '企业维度是否已读：0未读 1已读',
  `read_at` datetime DEFAULT NULL COMMENT '首次标记已读时间',
  `read_by` bigint DEFAULT NULL COMMENT '标记已读的tenant_user.id',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enterprise_reminder_event` (`enterprise_id`, `event_key`),
  KEY `idx_enterprise_reminder_list` (`enterprise_id`, `is_read`, `occurred_at`),
  KEY `idx_enterprise_reminder_type_time` (`enterprise_id`, `reminder_type`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='企业门户近期提醒';
```

设计说明：

- `title`、`content` 保存最终文案，确保套餐名、金额和阈值后来变化时历史提醒仍保持原貌。
- `business_data_json` 用于详情展示、排查和未来换模板重放，建议只放本提醒涉及的字段。
- `event_key` 不应使用纯时间戳。建议格式为 `{type}:{businessIdentity}:{stage}`，例如 `WORKORDER_QUOTA_NEAR_LIMIT:subscription-125:80`。
- 不使用 `deleted`：提醒是业务记录，不应软删除。列表默认按 `occurred_at DESC, id DESC`；过期提醒由 `expires_at` 从近期列表隐藏，但历史数据保留。

## 4. 客服监控提醒表

表名：`monitor_enterprise_reminder`

```sql
CREATE TABLE `monitor_enterprise_reminder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint NOT NULL COMMENT '发生提醒的企业ID，关联tenant_enterprise.id',
  `enterprise_name_snapshot` varchar(150) NOT NULL COMMENT '提醒生成时的企业名称快照',
  `reminder_type` varchar(64) NOT NULL COMMENT '提醒类型编码',
  `severity` varchar(16) NOT NULL COMMENT 'NOTICE WARNING CRITICAL',
  `title` varchar(200) NOT NULL COMMENT '生成时渲染完成的客服标题快照',
  `content` varchar(2000) NOT NULL COMMENT '生成时渲染完成的客服正文快照',
  `business_data_json` json DEFAULT NULL COMMENT '模板变量及触发时业务数据快照',
  `event_key` varchar(160) NOT NULL COMMENT '与企业提醒一致的幂等事件键',
  `occurred_at` datetime NOT NULL COMMENT '业务条件首次成立时间',
  `process_status` tinyint NOT NULL DEFAULT 0 COMMENT '处理状态：0待处理 1已处理',
  `processed_at` datetime DEFAULT NULL COMMENT '客服确认处理时间',
  `processed_by` bigint DEFAULT NULL COMMENT '处理人monitor_user.id',
  `process_remark` varchar(500) DEFAULT NULL COMMENT '处理说明，可记录联系结果或处置方式',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_monitor_reminder_event` (`enterprise_id`, `event_key`),
  KEY `idx_monitor_reminder_queue` (`process_status`, `severity`, `occurred_at`),
  KEY `idx_monitor_reminder_enterprise` (`enterprise_id`, `process_status`, `occurred_at`),
  KEY `idx_monitor_reminder_processor` (`processed_by`, `processed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='客服监控企业提醒';
```

处理约束：

- 点击“已处理”时必须同时写入 `process_status=1`、`processed_at=NOW()`、当前 `processed_by`；建议要求客服填写可选的 `process_remark`。
- 已处理即归档语义，不移动或删除记录。默认待办列表只查 `process_status=0`，历史页查 `process_status=1`。
- 企业侧已读与客服侧已处理完全独立，任一状态变化不得联动修改另一张表。
- 两张表应由同一个提醒生成事务同时插入，并共享 `event_key`。唯一键配合幂等插入，防止调度重试制造重复提醒。

## 5. 提醒模板

日期时间统一显示为 `yyyy-MM-dd HH:mm`，金额统一显示为人民币并保留两位小数，数量使用十进制整数。`remainingDays` 建议按自然日向上取整，避免“还有 0 天”但实际尚未到期。

### 5.1 自动续费余额不足

- 触发建议：`auto_renew_enabled=1`，距离 `next_renew_at` 进入预警窗口，且 `availableBalance = balanceAmount - frozenAmount < renewalAmount`。
- 级别：`WARNING`。
- 企业标题：`自动续费余额不足，请及时充值`
- 企业内容：`您的“{planName}”套餐将于 {renewAt} 自动续费，预计续费金额为 ¥{renewalAmount}。当前可用余额为 ¥{availableBalance}，尚缺 ¥{shortfallAmount}，余额不足将导致自动续费失败。请于续费时间前完成充值。`
- 客服标题：`【自动续费风险】{enterpriseName} 余额不足`
- 客服内容：`企业“{enterpriseName}”（{enterpriseCode}）的“{planName}”套餐计划于 {renewAt} 自动续费。预计续费金额 ¥{renewalAmount}，当前可用余额 ¥{availableBalance}，缺口 ¥{shortfallAmount}。请关注充值情况，必要时联系企业。`
- 关键快照：`subscriptionId`、`planId`、`planName`、`renewAt`、`renewalAmount`、`balanceAmount`、`frozenAmount`、`availableBalance`、`shortfallAmount`。
- 事件键：`AUTO_RENEW_BALANCE_INSUFFICIENT:subscription-{subscriptionId}:renew-{renewAt}`。

### 5.2 套餐即将到期且未开启自动续费

- 触发建议：`status=1`、`auto_renew_enabled=0`，距离 `end_at` 进入预警窗口。
- 级别：`NOTICE`；进入更短的升级窗口后可提升为 `WARNING` 并使用不同 `stage`。
- 企业标题：`套餐即将到期，请安排续费`
- 企业内容：`您的“{planName}”套餐将于 {endAt} 到期，剩余 {remainingDays} 天。当前未开启自动续费，到期后相关服务将受到影响。请及时续费或开启自动续费。`
- 客服标题：`【待续费】{enterpriseName} 套餐即将到期`
- 客服内容：`企业“{enterpriseName}”（{enterpriseCode}）的“{planName}”套餐将于 {endAt} 到期，剩余 {remainingDays} 天，目前未开启自动续费。请视客户服务策略进行续费跟进。`
- 关键快照：`subscriptionId`、`planId`、`planName`、`endAt`、`remainingDays`、`autoRenewEnabled`。
- 事件键：`SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW:subscription-{subscriptionId}:{stage}`，例如 `stage=7D`。

### 5.3 工单数量接近套餐额度

- 触发建议：当前周期用量首次达到预警比例，例如 `usedCount / workorderLimit >= 80%` 且尚未超额；比例必须配置化。
- 级别：`WARNING`。
- 企业标题：`工单额度即将用尽`
- 企业内容：`您当前套餐本周期包含 {workorderLimit} 单工单额度，已使用 {usedCount} 单（{usagePercent}%），剩余 {remainingCount} 单。超出额度后，每单将按 ¥{overageUnitPrice}/{overageCycleDays} 天计费，请提前评估用量或调整套餐。`
- 客服标题：`【额度预警】{enterpriseName} 工单用量接近上限`
- 客服内容：`企业“{enterpriseName}”（{enterpriseCode}）当前订阅周期为 {startAt} 至 {endAt}，工单额度 {workorderLimit} 单，已使用 {usedCount} 单（{usagePercent}%），剩余 {remainingCount} 单。超额单价为 ¥{overageUnitPrice}/{overageCycleDays} 天。`
- 关键快照：`subscriptionId`、`startAt`、`endAt`、`workorderLimit`、`usedCount`、`remainingCount`、`usagePercent`、`warningPercent`、`overageUnitPrice`、`overageCycleDays`。
- 事件键：`WORKORDER_QUOTA_NEAR_LIMIT:subscription-{subscriptionId}:{warningPercent}`。

### 5.4 工单数量已超过套餐额度

- 触发建议：当前周期 `usedCount` 首次大于 `workorderLimit`；后续超额数量变化不重复创建同阶段提醒。
- 级别：`WARNING`。
- 企业标题：`工单额度已超出，额外费用已开始计算`
- 企业内容：`您当前套餐本周期包含 {workorderLimit} 单工单额度，现已使用 {usedCount} 单，超出 {overageCount} 单。超额工单将按每单 ¥{overageUnitPrice}/{overageCycleDays} 天计费，当前预计超额费用为 ¥{estimatedOverageAmount}。`
- 客服标题：`【已超额】{enterpriseName} 已产生工单额外费用`
- 客服内容：`企业“{enterpriseName}”（{enterpriseCode}）本周期工单额度 {workorderLimit} 单，已使用 {usedCount} 单，超出 {overageCount} 单；按 ¥{overageUnitPrice}/{overageCycleDays} 天计算，当前预计超额费用 ¥{estimatedOverageAmount}。请关注其余额及后续用量。`
- 关键快照：`subscriptionId`、`workorderLimit`、`usedCount`、`overageCount`、`overageUnitPrice`、`overageCycleDays`、`estimatedOverageAmount`。
- 事件键：`WORKORDER_QUOTA_EXCEEDED:subscription-{subscriptionId}:FIRST_OVERAGE`。

### 5.5 余额变为负数

- 触发建议：钱包变更事务中余额由 `balanceBefore >= 0` 跨越为 `balanceAfter < 0` 时立即生成，而不是每天重复扫描生成。
- 级别：`CRITICAL`。
- 企业标题：`账户余额已为负数，请尽快充值`
- 企业内容：`因{transactionReason}，您的账户余额已由 ¥{balanceBefore} 变为 ¥{balanceAfter}，当前欠费 ¥{arrearsAmount}。当余额低于 ¥{suspendThreshold} 时，套餐服务将暂停。请尽快充值，以免影响正常使用。`
- 客服标题：`【欠费提醒】{enterpriseName} 账户余额已为负数`
- 客服内容：`企业“{enterpriseName}”（{enterpriseCode}）因{transactionReason}，余额由 ¥{balanceBefore} 变为 ¥{balanceAfter}，当前欠费 ¥{arrearsAmount}；套餐暂停阈值为 ¥{suspendThreshold}。请关注欠费变化并按需联系企业。`
- 关键快照：`walletId`、`walletTransactionId`、`transactionReason`、`balanceBefore`、`balanceAfter`、`arrearsAmount`、`suspendThreshold`。
- 事件键：`WALLET_BALANCE_NEGATIVE:transaction-{walletTransactionId}`。

### 5.6 欠费超过阈值导致套餐暂停

- 触发建议：订阅从生效状态变为 `status=3` 且 `suspend_reason=ARREARS` 时立即生成。
- 级别：`CRITICAL`。
- 企业标题：`套餐服务已因欠费暂停`
- 企业内容：`您的账户余额为 ¥{balanceAmount}，已低于服务暂停阈值 ¥{suspendThreshold}，“{planName}”套餐已于 {suspendedAt} 暂停。请充值至高于 ¥{restoreThreshold}；到账并满足恢复条件后，系统将自动恢复服务。`
- 客服标题：`【服务已暂停】{enterpriseName} 欠费超过阈值`
- 客服内容：`企业“{enterpriseName}”（{enterpriseCode}）余额为 ¥{balanceAmount}，已低于暂停阈值 ¥{suspendThreshold}，“{planName}”套餐于 {suspendedAt} 因欠费暂停。余额需恢复至高于 ¥{restoreThreshold} 才会自动恢复，请优先跟进。`
- 关键快照：`subscriptionId`、`planName`、`balanceAmount`、`suspendThreshold`、`restoreThreshold`、`suspendedAt`。
- 事件键：`SUBSCRIPTION_SUSPENDED_ARREARS:subscription-{subscriptionId}:suspended-{suspendedAt}`。

### 5.7 即将到达资料清理期限

- 触发建议：订阅已到期且 `deleteAt = end_at + retentionDays` 进入预警窗口。若企业重新开通套餐，应使旧提醒失效，不得继续展示。
- 级别：`CRITICAL`。
- 企业标题：`企业资料即将进入清理流程`
- 企业内容：`您的套餐已于 {endAt} 结束。根据资料保留规则，相关车险业务资料预计于 {deleteAt} 起进入清理流程，剩余 {remainingDays} 天；清理后将无法通过系统恢复。请在期限前重新开通套餐，并及时备份所需资料。`
- 客服标题：`【资料清理预警】{enterpriseName} 即将到达保留期限`
- 客服内容：`企业“{enterpriseName}”（{enterpriseCode}）最近一次套餐于 {endAt} 结束，资料保留期为 {retentionDays} 天，预计自 {deleteAt} 起进入清理流程，剩余 {remainingDays} 天。请按客户服务策略完成最终提醒。`
- 关键快照：`subscriptionId`、`lastPlanName`、`endAt`、`retentionDays`、`deleteAt`、`remainingDays`。
- 事件键：`ENTERPRISE_DATA_DELETION_APPROACHING:subscription-{subscriptionId}:{stage}`，例如 `stage=7D`。

## 6. 推荐触发与状态规则

1. 余额跨零、套餐欠费暂停属于资金/状态变更的直接结果，应在原业务事务提交成功后可靠生成；如采用事务内双写，两张提醒表任一写入失败都应使业务事务回滚，或使用事务事件表保证最终一致性。
2. 到期、额度临界和资料清理预警可由每日维护任务扫描。扫描必须依赖唯一 `event_key` 幂等，允许失败重跑。
3. 建议将预警窗口配置化，例如套餐到期 15/7/1 天、资料清理 15/7/1 天、额度 80%/90%；每个阶段生成一条独立提醒，避免反复覆盖历史提醒。
4. 条件解除后不删除历史记录。近期列表可依据 `expires_at` 隐藏；客服仍通过处理状态归档并保留审计轨迹。
5. 文案渲染失败时不得写入半成品提醒；日志应记录 `enterprise_id`、`reminder_type` 和 `event_key`，但不得记录手机号等无关敏感信息。

## 7. 实施前需要确认的业务参数

- 套餐到期和资料清理分别提前多少天提醒，是否采用多阶段提醒。
- 工单额度预警比例（建议 80% 和 90%），以及工单用量的有效状态口径。
- 客服将提醒标为已处理时，`process_remark` 是否必填。
- 企业提醒是“企业内任一成员读过即已读”，还是要求每个成员各自维护已读状态；后者需要增加第三张接收人/已读关系表。
- 资料清理前重新开通套餐后，旧提醒是仅从近期列表失效，还是同步自动标记客服提醒已处理。
