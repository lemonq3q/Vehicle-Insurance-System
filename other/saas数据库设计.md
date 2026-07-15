# 车险系统 SaaS 化数据库设计

## 1. 现有数据库问题

本文基于当前 MySQL 数据库 `insurance` 的真实表结构和项目代码进行设计。当前主要表包括 `user`、`role`、`menu`、`user_role`、`role_menu`、`merchant`、`merchant_area`、`workorder`、`insurance`、`system_file`、车辆证件表和对应归档表。

当前设计中比较影响 SaaS 化的问题如下：

1. `merchant.type` 同时承担上下游方向和机构类别两个含义，代码通过 `m.type IN ('车商店铺', '汽修厂', '代理人')` 等中文名称判断上下游。新增或改名商户类型时必须同步修改 SQL，且无法通过外键或字典约束类型。SaaS 化后应将“上下游方向”和“商户类别”集中到商户分类表管理，业务 SQL 不再维护中文名称列表。
2. `user` 表同时保存真实登录用户和车商店铺人员等业务抽象对象，并通过 `role/user_role` 中的 `admin`、`出单员`、`联系人`、`店员`、`收款人` 区分。联系人、店员、收款人不参与系统登录和操作权限，不应继续占用系统用户表及权限角色表。
3. `merchant` 表同时表达上游机构、下游商户等业务实体，且 `user.merchant_id` 直接绑定商户，导致“企业租户”“业务商户”“商户人员”三个概念混在一起。SaaS 化后应新增 `tenant_enterprise` 作为租户企业，`biz_merchant` 表示业务渠道主体，`biz_merchant_staff` 表示商户人员。
4. 角色权限虽然有 `role/menu/role_menu` 表，但代码和前端仍大量按角色名称硬编码，例如 `admin`、`出单员`、`收款人` 等，并且 `role_menu` 当前只有出单员的权限数据，权限体系不完整。系统权限角色和商户人员业务角色必须分开建模。
5. `menu` 表只有 `perms`，缺少权限名称、权限类型、前端路由、父子层级、归属系统等字段，不利于同时维护门户平台、车险系统和后台系统的权限。
6. 几乎所有业务表只有主键索引，没有租户、工单编号、用户、商户、时间等常用查询索引；SaaS 化后如果不补充 `enterprise_id` 和复合索引，会很容易出现跨租户查询风险和性能问题。
7. 当前表普遍使用 `bigint` 毫秒时间戳，建议新表统一改为 `datetime`，便于 MySQL 查询、分区和归档；旧表可以迁移时逐步改造。
8. `workorder` 表字段过宽，混合了车主信息、报价、支付、核保、物流、上下游费用等多个阶段数据；短期可增加租户隔离字段，长期建议拆成工单主表、报价表、支付表、核保表、物流表和费用明细表。
9. OCR 和请求量目前没有企业维度的持久化统计表；`RequestCountFilter` 只统计当前活跃请求数，不能满足后台系统按企业、日期、接口维度监控用量。
10. 归档表采用同结构 `_archive`，但缺少归档批次、归档时间、归档原因，不利于追溯。新增 SaaS 用量归档表时应补齐这些字段。

本次对真实数据库的只读核对结果进一步证明了上述混用：`insurance.merchant` 中同时存在 `机构`、`车商店铺`、`汽修厂` 和空类型；`insurance.role` 中同时存在系统权限角色 `admin`、`出单员` 和商户人员角色 `联系人`、`店员`、`收款人`。有效商户人员数据还存在同一商户多联系人、多个收款人的历史情况，迁移时必须先处理冲突，不能直接依赖旧角色名称完成无校验迁移。

## 2. 总体设计原则

1. 登录账号与业务人员分离：`tenant_user` 只保存真实登录账号；联系人、店员、收款人等不登录系统的商户人员保存到 `biz_merchant_staff`，二者之间不建立默认继承关系。
2. 企业账号与企业成员分离：`tenant_user` 只表示企业客户侧自然人账号，`tenant_member` 表示账号在某个企业内的身份和状态。
3. 后台监控平台账号独立管理：开发者、客服、运营人员使用 `platform_user`，不与企业客户账号共表，避免内部权限和客户权限混淆。
4. 租户企业与业务商户分离：`tenant_enterprise` 是 SaaS 租户；`biz_merchant` 是车险业务中的渠道主体；`biz_merchant_staff` 是依附于业务商户的人员档案。
5. 商户方向与商户类别分离：`biz_merchant_category.direction` 表示 `UPSTREAM/DOWNSTREAM`，`code/name` 表示机构、车商店铺、汽修厂、代理人等类别，禁止再通过中文名称列表判断上下游。
6. 权限角色与业务角色分离：`auth_role/auth_permission` 只处理系统操作权限；`biz_merchant_staff_role` 只描述联系人、店员、收款人等业务身份，不关联菜单和权限。
7. 企业内角色固定为三类：企业拥有者、管理员、出单员。企业拥有者不可直接修改角色，只能通过转让产生；转让后原拥有者自动变为管理员。
8. 一个企业只能有一个有效拥有者。建议通过业务事务保证，同时增加唯一约束辅助控制。
9. 所有车险业务数据必须带 `enterprise_id`，查询必须默认按当前企业过滤。
10. 套餐、订单、订阅和用量统计归属 SaaS 平台层；工单、商户、保险配置归属车险业务层。
11. 请求量、OCR 次数等高频统计先写 Redis，夜间批量归档到 MySQL 汇总表；重要订单、订阅和权限变更必须实时写 MySQL。

## 3. SaaS 功能表设计

### 3.1 门户账号与企业租户

#### tenant_user 企业用户账号表

替代现有 `user` 表中企业客户登录账号的职责。该表只保存企业客户、企业拥有者、企业管理员、出单员等普通企业用户，不保存开发者、客服、平台运营人员。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 用户 ID |
| username | varchar(100) | 登录账号，可继续兼容手机号 |
| phone | varchar(20) | 手机号 |
| email | varchar(100) | 邮箱 |
| password | varchar(100) | 加密密码 |
| real_name | varchar(100) | 姓名 |
| id_num | varchar(100) | 证件号，按需要加密或脱敏 |
| avatar_file_id | bigint | 头像文件 |
| status | tinyint | 1 启用，0 禁用 |
| last_login_time | datetime | 最后登录时间 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议约束与索引：

| 名称 | 字段 | 说明 |
| --- | --- | --- |
| uk_tenant_user_username | username | 登录账号唯一 |
| uk_tenant_user_phone | phone | 手机号唯一，允许为空时需业务控制 |
| idx_tenant_user_email | email | 邮箱查询 |

#### tenant_enterprise 企业租户表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 企业 ID |
| name | varchar(150) | 企业名称 |
| code | varchar(50) | 企业编码 |
| owner_user_id | bigint | 当前企业拥有者用户 ID |
| contact_name | varchar(100) | 联系人 |
| contact_phone | varchar(20) | 联系电话 |
| status | tinyint | 1 正常，2 欠费限制，3 停用 |
| source | tinyint | 1 用户自建，2 后台创建 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议约束与索引：

| 名称 | 字段 | 说明 |
| --- | --- | --- |
| uk_enterprise_code | code | 企业编码唯一 |
| idx_enterprise_owner | owner_user_id | 查询拥有者企业 |
| idx_enterprise_status | status | 后台筛选 |

#### tenant_member 企业成员表

一个账号加入一个企业后，在此表形成企业内身份。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 成员 ID |
| enterprise_id | bigint | 企业 ID |
| user_id | bigint | 用户 ID |
| role_code | varchar(32) | OWNER, ADMIN, ISSUER |
| status | tinyint | 1 正常，0 禁用，2 待审核，3 已退出 |
| joined_by_invite_id | bigint | 来源邀请码 |
| joined_at | datetime | 加入时间 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议约束与索引：

| 名称 | 字段 | 说明 |
| --- | --- | --- |
| uk_member_enterprise_user | enterprise_id,user_id,deleted | 同一用户在同一企业只有一个有效成员关系 |
| idx_member_user | user_id,status | 用户进入门户后查询可切换企业 |
| idx_member_enterprise_role | enterprise_id,role_code,status | 企业成员和角色查询 |

拥有者唯一性建议：

MySQL 对软删除和条件唯一不够直接，建议二选一：

1. 以 `tenant_enterprise.owner_user_id` 作为当前拥有者唯一事实来源，`tenant_member.role_code = OWNER` 作为冗余展示字段，由转让事务同步。
2. 增加生成列 `owner_enterprise_key`，仅当 `role_code='OWNER' AND deleted=0 AND status=1` 时等于 `enterprise_id`，否则为 null，并对该生成列建唯一索引。

#### tenant_owner_transfer_log 企业拥有者转让记录

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 记录 ID |
| enterprise_id | bigint | 企业 ID |
| from_user_id | bigint | 原拥有者 |
| to_user_id | bigint | 新拥有者 |
| status | tinyint | 1 成功，2 撤销，3 失败 |
| transferred_at | datetime | 转让时间 |
| created_by | bigint | 操作人 |
| remark | varchar(500) | 备注 |

转让业务必须放在一个事务中：

1. 校验 `from_user_id = tenant_enterprise.owner_user_id`。
2. 校验 `to_user_id` 是同企业有效成员。
3. 更新 `tenant_enterprise.owner_user_id = to_user_id`。
4. 原 OWNER 成员改为 ADMIN，新成员改为 OWNER。
5. 写入 `tenant_owner_transfer_log`。

### 3.2 邀请码

#### tenant_invite_code 企业邀请码表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 邀请码 ID |
| enterprise_id | bigint | 企业 ID |
| code | varchar(64) | 邀请码，建议保存随机码或 hash |
| default_role_code | varchar(32) | 默认 ISSUER |
| max_use_count | int | 最大使用次数，null 表示不限 |
| used_count | int | 已使用次数 |
| expires_at | datetime | 过期时间，例如创建后一天 |
| status | tinyint | 1 可用，2 已失效，3 已撤销 |
| created_by | bigint | 创建人，必须是 OWNER 或 ADMIN |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 软删除 |

建议约束与索引：

| 名称 | 字段 | 说明 |
| --- | --- | --- |
| uk_invite_code | code | 邀请码唯一 |
| idx_invite_enterprise | enterprise_id,status,expires_at | 企业邀请码列表 |

邀请码加入规则：

1. 校验邀请码存在、未删除、状态可用、未过期、未超过次数。
2. 若用户已是该企业有效成员，直接返回已加入。
3. 新建 `tenant_member`，`role_code` 固定为 `ISSUER`。
4. `tenant_member.joined_by_invite_id` 记录来源邀请码。
5. `tenant_invite_code.used_count + 1`。

### 3.3 套餐、企业余额、订单与订阅

#### saas_plan 套餐表

后台系统维护。套餐只配置客户可理解的商业指标，例如人数、时长和价格；OCR 次数、请求次数等细粒度技术指标只进入后台监控，不作为套餐展示项。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 套餐 ID |
| code | varchar(50) | 套餐编码 |
| name | varchar(100) | 套餐名称 |
| description | varchar(500) | 描述 |
| billing_period | varchar(20) | MONTH, YEAR, DAY |
| duration_days | int | 有效天数 |
| user_limit | int | 最大成员数 |
| price | decimal(12,2) | 售价 |
| original_price | decimal(12,2) | 原价 |
| status | tinyint | 1 上架，0 下架 |
| sort_no | int | 排序 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议约束：

| 名称 | 字段 | 说明 |
| --- | --- | --- |
| uk_saas_plan_code | code | 套餐编码唯一 |
| idx_saas_plan_status | status,sort_no | 前台套餐列表 |

#### saas_wallet 企业钱包表

企业余额绑定企业，不绑定个人用户。原因是套餐、订阅、成员人数和业务使用权都属于企业级资源；企业拥有者转让后，余额也应继续属于该企业。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 钱包 ID |
| enterprise_id | bigint | 企业 ID |
| balance_amount | decimal(12,2) | 可用余额 |
| frozen_amount | decimal(12,2) | 冻结金额，预留 |
| currency | varchar(10) | 币种，默认 CNY |
| status | tinyint | 1 正常，0 冻结 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

#### saas_recharge_order 充值订单表

用户向企业钱包充值时创建充值订单。充值订单表示“外部支付换余额”，套餐订单表示“企业余额购买服务”，两者不要混用。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 充值订单 ID |
| recharge_no | varchar(64) | 充值订单号 |
| enterprise_id | bigint | 企业 ID |
| user_id | bigint | 充值操作人 |
| amount | decimal(12,2) | 充值金额 |
| pay_channel | varchar(32) | 支付渠道 |
| pay_trade_no | varchar(100) | 第三方交易号 |
| status | tinyint | 1 待支付，2 已支付，3 已取消，4 支付失败 |
| paid_at | datetime | 支付时间 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 软删除 |

充值成功后必须在同一事务中：

1. 更新 `saas_recharge_order.status = 2`。
2. 增加 `saas_wallet.balance_amount`。
3. 写入一条 `saas_wallet_transaction` 入账流水。

#### saas_wallet_transaction 企业钱包流水表

流水表记录企业余额每一次变化，只追加，不修改，不软删除。充值、购买套餐、手动续费、自动续费、套餐变更补差价、套餐变更退差价都必须写流水。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 流水 ID |
| enterprise_id | bigint | 企业 ID |
| wallet_id | bigint | 钱包 ID |
| user_id | bigint | 操作用户；自动续费可为空 |
| transaction_no | varchar(64) | 流水号 |
| direction | varchar(10) | IN 增加，OUT 减少 |
| transaction_type | varchar(32) | RECHARGE, BUY_PLAN, RENEW_PLAN, AUTO_RENEW, CHANGE_PLAN, REFUND, ADJUST |
| amount | decimal(12,2) | 本次变动金额，始终为正数 |
| balance_before | decimal(12,2) | 变动前余额 |
| balance_after | decimal(12,2) | 变动后余额 |
| related_order_id | bigint | 关联套餐订单 |
| related_recharge_order_id | bigint | 关联充值订单 |
| related_subscription_id | bigint | 关联订阅 |
| remark | varchar(500) | 说明 |
| created_at | datetime | 创建时间 |

#### saas_order 套餐订单表

企业购买订阅、续费订阅、自动续费、变更套餐都必须创建套餐订单。订单是业务动作凭证，余额流水是资金变化凭证，两者通过 `wallet_transaction_id` 关联。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 订单 ID |
| order_no | varchar(64) | 订单号 |
| order_type | varchar(32) | BUY, RENEW, AUTO_RENEW, CHANGE_PLAN |
| enterprise_id | bigint | 企业 ID |
| buyer_user_id | bigint | 购买人，必须是当前拥有者 |
| plan_id | bigint | 套餐 ID |
| plan_snapshot_json | json | 下单时套餐快照 |
| buy_user_limit | int | 购买人数 |
| buy_duration_days | int | 购买时长 |
| pay_type | varchar(32) | BALANCE, ONLINE |
| amount | decimal(12,2) | 应付金额，保留兼容字段 |
| price_amount | decimal(12,2) | 原始套餐金额 |
| discount_amount | decimal(12,2) | 折扣金额 |
| credit_amount | decimal(12,2) | 原套餐剩余价值抵扣 |
| payable_amount | decimal(12,2) | 最终应付金额 |
| refund_amount | decimal(12,2) | 应退回企业余额金额 |
| paid_amount | decimal(12,2) | 实付金额 |
| pay_channel | varchar(32) | 支付渠道 |
| pay_trade_no | varchar(100) | 第三方交易号 |
| wallet_transaction_id | bigint | 关联扣款或退款流水 |
| original_subscription_id | bigint | 变更套餐时关联原订阅 |
| old_plan_id | bigint | 变更前套餐 |
| new_plan_id | bigint | 变更后套餐 |
| auto_renew | tinyint | 是否自动续费订单 |
| status | tinyint | 1 待支付，2 已支付，3 已取消，4 已退款，5 已关闭 |
| paid_at | datetime | 支付时间 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 软删除 |

建议约束与索引：

| 名称 | 字段 | 说明 |
| --- | --- | --- |
| uk_saas_order_no | order_no | 订单号唯一 |
| idx_saas_order_enterprise | enterprise_id,status,created_at | 企业订单列表 |
| idx_saas_order_buyer | buyer_user_id,created_at | 用户订单查询 |

#### saas_subscription 企业订阅表

订单支付成功后创建、续期或变更订阅。订阅只保留当前套餐、人数、有效期和自动续费配置；订阅变化历史通过每次创建的 `saas_order` 和 `saas_wallet_transaction` 追溯，不再单独建表记录。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 订阅 ID |
| enterprise_id | bigint | 企业 ID |
| plan_id | bigint | 当前套餐 |
| order_id | bigint | 来源订单 |
| status | tinyint | 1 生效中，2 已过期，3 已暂停，4 已取消 |
| user_limit | int | 当前人数上限 |
| start_at | datetime | 生效时间 |
| end_at | datetime | 到期时间 |
| auto_renew_enabled | tinyint | 是否开启自动续费 |
| auto_renew_plan_id | bigint | 自动续费用套餐，默认当前套餐 |
| next_renew_at | datetime | 下次自动续费时间 |
| last_renew_order_id | bigint | 最近一次续费订单 |
| cancel_auto_renew_at | datetime | 取消自动续费时间 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

建议索引：

| 名称 | 字段 | 说明 |
| --- | --- | --- |
| idx_sub_enterprise_status | enterprise_id,status,end_at | 鉴权和到期校验 |

套餐变更计算规则：

1. 变更立即生效，订阅 `end_at` 默认不变。
2. 原套餐剩余价值 = 原套餐价格 * 剩余天数 / 原套餐周期天数。
3. 新套餐剩余期间价值 = 新套餐价格 * 剩余天数 / 新套餐周期天数。
4. 差额 = 新套餐剩余期间价值 - 原套餐剩余价值。
5. 差额大于 0 时，从企业钱包扣款并写 `CHANGE_PLAN` 出账流水。
6. 差额小于 0 时，退回企业钱包并写 `CHANGE_PLAN` 入账流水。
7. 降级后若当前成员数超过新套餐人数上限，则不允许变更，需先减少成员或选择更高套餐。

自动续费规则：

1. 企业拥有者可开启或关闭自动续费。
2. 到达 `next_renew_at` 时，系统创建 `order_type=AUTO_RENEW` 的套餐订单。
3. 企业钱包余额充足则自动扣款、写流水、延长订阅。
4. 企业钱包余额不足则订单保持待支付或失败状态，订阅到期后企业进入欠费限制。

### 3.4 后台系统与平台权限

#### platform_user 后台平台用户表

后台监控平台专用账号表，用于开发者、客服、运营人员登录后台系统。该表不与 `tenant_user` 共用，后台人员默认不属于任何企业，也不参与企业拥有者、管理员、出单员这套企业内角色。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 平台用户 ID |
| username | varchar(100) | 后台登录账号 |
| phone | varchar(20) | 手机号 |
| email | varchar(100) | 邮箱 |
| password | varchar(100) | 加密密码 |
| real_name | varchar(100) | 姓名 |
| department | varchar(100) | 部门，例如客服、技术、运营 |
| status | tinyint | 1 启用，0 禁用 |
| last_login_time | datetime | 最后登录时间 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议约束与索引：

| 名称 | 字段 | 说明 |
| --- | --- | --- |
| uk_platform_user_username | username | 后台登录账号唯一 |
| uk_platform_user_phone | phone | 手机号唯一，允许为空时需业务控制 |
| idx_platform_user_email | email | 邮箱查询 |

#### platform_user_role 后台用户角色关联表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 关联 ID |
| platform_user_id | bigint | 平台用户 ID |
| role_id | bigint | 平台角色 ID，对应 `auth_role.role_scope = PLATFORM` |
| created_at | datetime | 创建时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议唯一键：`platform_user_id, role_id, deleted`。

平台用户常见角色建议：

| 角色 | role_code | 说明 |
| --- | --- | --- |
| 超级管理员 | PLATFORM_SUPER_ADMIN | 管理后台账号、角色、套餐、企业和系统配置 |
| 客服 | PLATFORM_CUSTOMER_SERVICE | 查看企业、订单、用量，协助处理客户问题 |
| 运维/开发 | PLATFORM_DEVELOPER | 查看监控、归档任务、接口用量、异常排查数据 |

#### auth_role 角色表

建议替代或升级现有 `role`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 角色 ID |
| code | varchar(50) | ROLE_PLATFORM_ADMIN, ROLE_OWNER, ROLE_ADMIN, ROLE_ISSUER |
| name | varchar(100) | 角色名称 |
| role_scope | varchar(32) | PLATFORM, TENANT |
| builtin | tinyint | 是否系统内置 |
| status | tinyint | 1 启用，0 停用 |
| remark | varchar(500) | 备注 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 软删除 |

#### auth_permission 权限表

建议替代或升级现有 `menu`。权限不仅是菜单，也包括按钮和接口权限。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 权限 ID |
| parent_id | bigint | 父权限 |
| system_code | varchar(32) | PORTAL, INSURANCE, ADMIN |
| type | varchar(20) | MENU, BUTTON, API |
| code | varchar(100) | 权限编码，例如 workorder:update |
| name | varchar(100) | 权限名称 |
| route_path | varchar(200) | 前端路由 |
| component | varchar(200) | 前端组件 |
| sort_no | int | 排序 |
| status | tinyint | 1 启用，0 停用 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 软删除 |

#### auth_role_permission 角色权限关联表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 关联 ID |
| role_id | bigint | 角色 ID |
| permission_id | bigint | 权限 ID |
| created_at | datetime | 创建时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议保留三类企业内置角色：

| 角色 | role_code | 说明 |
| --- | --- | --- |
| 企业拥有者 | OWNER | 企业唯一拥有者，管理企业、套餐、邀请、成员、业务数据 |
| 管理员 | ADMIN | 管理企业成员和业务数据，但不能转让拥有者，不能购买或变更套餐时可按业务决定 |
| 出单员 | ISSUER | 默认邀请码加入角色，处理工单 |

后台平台角色不应与企业角色混用：平台用户只从 `platform_user` 登录，通过 `platform_user_role` 绑定 `auth_role.role_scope=PLATFORM` 的角色；企业用户只从 `tenant_user` 登录，通过 `tenant_member.role_code` 获得企业内角色。

联系人、店员、收款人不是 `auth_role`，也不是 `tenant_member.role_code`。这些角色只写入 `biz_merchant_staff_role`，不生成登录账号、不进入 JWT、不关联 `auth_permission`，不能作为 `@PreAuthorize` 或前端菜单判断依据。

### 3.5 用量统计、Redis 缓存与夜间归档

高频计数建议写 Redis，夜间汇总入库。

Redis key 设计：

| key | 类型 | 说明 |
| --- | --- | --- |
| usage:day:{yyyyMMdd}:enterprise:{enterpriseId}:request | hash | field 为接口或模块，value 为请求次数 |
| usage:day:{yyyyMMdd}:enterprise:{enterpriseId}:ocr | hash | field 为 OCR 类型，value 为调用次数 |
| usage:month:{yyyyMM}:enterprise:{enterpriseId}:request | hash | 月请求计数，可由日统计累加 |
| usage:month:{yyyyMM}:enterprise:{enterpriseId}:ocr | hash | 月 OCR 计数 |

#### saas_usage_daily 每日用量汇总表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 汇总 ID |
| stat_date | date | 统计日期 |
| enterprise_id | bigint | 企业 ID |
| metric_code | varchar(50) | REQUEST, OCR |
| dimension | varchar(100) | 接口路径、OCR 类型或模块 |
| count_value | bigint | 次数 |
| archived_at | datetime | 归档时间 |
| archive_batch_no | varchar(64) | 归档批次 |

建议唯一键：

| 名称 | 字段 | 说明 |
| --- | --- | --- |
| uk_usage_daily | stat_date,enterprise_id,metric_code,dimension | 每天每企业每指标唯一 |

#### saas_usage_monthly 每月用量汇总表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 汇总 ID |
| stat_month | char(7) | 例如 2026-07 |
| enterprise_id | bigint | 企业 ID |
| metric_code | varchar(50) | REQUEST, OCR |
| dimension | varchar(100) | 接口路径、OCR 类型或模块 |
| count_value | bigint | 次数 |
| archived_at | datetime | 归档时间 |

建议唯一键：

| 名称 | 字段 | 说明 |
| --- | --- | --- |
| uk_usage_monthly | stat_month,enterprise_id,metric_code,dimension | 每月每企业每指标唯一 |

#### saas_usage_archive_job 用量归档任务表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 任务 ID |
| batch_no | varchar(64) | 批次号 |
| stat_date | date | 归档日期 |
| status | tinyint | 1 执行中，2 成功，3 失败 |
| started_at | datetime | 开始时间 |
| finished_at | datetime | 结束时间 |
| error_message | varchar(1000) | 错误信息 |

归档建议：

1. 每晚固定时间扫描前一天 Redis key。
2. 使用 `INSERT ... ON DUPLICATE KEY UPDATE count_value = VALUES(count_value)` 写入日表。
3. 同步累加或重算月表。
4. 归档成功后保留 Redis key 2 到 7 天再删除，便于重跑。
5. 用量数据只作为后台监控和运营分析，不作为套餐中展示给客户的额度指标。

## 4. 非 SaaS 功能表设计

### 4.1 车险业务商户、分类与人员

本节将旧库中混在 `merchant.type` 和 `user/role/user_role` 内的概念拆为四层：商户分类、业务商户、商户人员、人员业务角色。车商店铺人员是主要使用场景，但表结构也允许机构、汽修厂等其他商户维护联系人或收款人。

#### biz_merchant_category 商户分类表

集中维护商户类别以及该类别所属的上下游方向。类别名称仅用于展示，程序和 SQL 使用稳定的 `code` 或关联 ID，不使用中文名称列表判断上下游。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 分类 ID |
| code | varchar(50) | 稳定分类编码 |
| name | varchar(100) | 展示名称 |
| direction | varchar(20) | UPSTREAM, DOWNSTREAM |
| status | tinyint | 1 启用，0 禁用 |
| sort_no | int | 排序 |
| remark | varchar(500) | 说明 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人，指向真实系统用户 |
| deleted | tinyint | 软删除 |

建议约束与初始数据：

| code | name | direction | 旧 `merchant.type` 映射 |
| --- | --- | --- | --- |
| INSURANCE_ORG | 保险机构 | UPSTREAM | 机构 |
| DEALER_STORE | 车商店铺 | DOWNSTREAM | 车商店铺 |
| AUTO_REPAIR | 汽修厂 | DOWNSTREAM | 汽修厂 |
| AGENT | 代理人 | DOWNSTREAM | 代理人 |

- 唯一键：`code, deleted`；索引：`direction, status, deleted`。
- 旧类型为空或无法映射的数据先进入迁移异常清单，不自动猜测分类。
- 上下游查询统一关联分类表并使用 `direction`，例如查询所有下游商户只判断 `direction='DOWNSTREAM'`，不再维护 `IN ('车商店铺', ...)`。

#### biz_merchant 业务商户表

由现有 `merchant` 改造。商户保存机构主体信息以及商户自己的开户行、银行卡号，分类通过 `category_id` 关联；联系人和人员手机号不再以 `contact/phone` 作为唯一事实来源。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 商户 ID |
| enterprise_id | bigint | 所属企业租户 |
| code | varchar(100) | 商户编码 |
| name | varchar(100) | 商户名称 |
| category_id | bigint | 商户分类 ID，关联 `biz_merchant_category` |
| location | varchar(20) | 所在地区 |
| address | varchar(100) | 地址 |
| bank | varchar(100) | 商户开户行 |
| bank_card_num | varchar(100) | 商户银行卡号，建议加密存储、脱敏展示 |
| channel | varchar(100) | 渠道 |
| service_phone | varchar(20) | 商户公共服务电话，可为空，不代表联系人手机号 |
| default_area_code | varchar(20) | 默认区域 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议索引：

| 名称 | 字段 | 说明 |
| --- | --- | --- |
| uk_biz_merchant_enterprise_code | enterprise_id,code | 企业内商户编码永久唯一，软删除后也不复用 |
| idx_biz_merchant_category | enterprise_id,category_id,deleted | 按分类查询商户 |
| idx_biz_merchant_name | enterprise_id,name | 模糊查询可后续换全文索引 |

#### biz_merchant_area 商户业务区域表

由现有 `merchant_area` 改造。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | ID |
| enterprise_id | bigint | 企业 ID |
| merchant_id | bigint | 商户 ID |
| area_code | varchar(20) | 区域编码 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议唯一键：`enterprise_id, merchant_id, area_code, deleted`。

#### biz_merchant_staff 商户人员表

保存车商店铺等业务商户的人员档案。该表中的人员是业务联系人，不是系统登录用户，不保存密码，不参与企业成员数计算，也不拥有系统操作权限。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 商户人员 ID |
| enterprise_id | bigint | 企业 ID |
| merchant_id | bigint | 所属业务商户 ID |
| name | varchar(100) | 姓名 |
| phone | varchar(20) | 手机号，仅用于业务联系 |
| email | varchar(100) | 邮箱，可为空 |
| id_num | varchar(100) | 证件号，按需加密或脱敏 |
| status | tinyint | 1 在用，0 停用 |
| remark | varchar(500) | 备注 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人，指向 `tenant_user` |
| deleted | tinyint | 软删除 |

建议索引：

| 名称 | 字段 | 说明 |
| --- | --- | --- |
| idx_merchant_staff_list | enterprise_id,merchant_id,status,deleted | 商户人员列表 |
| idx_merchant_staff_phone | enterprise_id,phone,deleted | 企业内按手机号检索 |

`phone` 不作为全平台登录账号，因此不要求跨企业全局唯一。同一商户是否允许重复手机号由业务校验；迁移旧数据时不得因为手机号与 `tenant_user` 相同就自动合并两类对象。

#### biz_merchant_staff_role 商户人员业务角色表

一个人员可以同时承担多个业务角色。该表只表达业务职责，不关联 `auth_role`、`auth_permission` 或菜单。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 关联 ID |
| enterprise_id | bigint | 企业 ID |
| merchant_id | bigint | 商户 ID，与人员所属商户一致 |
| staff_id | bigint | 商户人员 ID |
| role_code | varchar(32) | CONTACT, CLERK, PAYEE |
| is_default | tinyint | 是否默认；仅 PAYEE 有意义 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人，指向 `tenant_user` |
| deleted | tinyint | 软删除 |

角色语义：

| 角色 | role_code | 约束与用途 |
| --- | --- | --- |
| 联系人 | CONTACT | 每个商户最多一个有效联系人 |
| 店员 | CLERK | 普通业务人员，可存在多个 |
| 收款人 | PAYEE | 可存在多个，但每个商户最多一个默认收款人 |

建议约束：

1. 唯一键 `enterprise_id, staff_id, role_code, deleted`，避免同一人员重复绑定同一角色。
2. 增加生成列 `contact_merchant_key`：仅当 `role_code='CONTACT' AND deleted=0` 时取 `merchant_id`，否则为 null；对该列建唯一索引，保证每个商户最多一个有效联系人。
3. 增加生成列 `default_payee_merchant_key`：仅当 `role_code='PAYEE' AND is_default=1 AND deleted=0` 时取 `merchant_id`，否则为 null；对该列建唯一索引，保证每个商户最多一个默认收款人。
4. 创建或更换联系人、默认收款人时必须使用事务；先解除旧角色或旧默认标记，再设置新记录。
5. 出单时只读取创建方商户的有效默认收款人。未配置默认收款人时应明确提示补充资料，不再按“收款人、联系人、任意人员”的顺序静默回退。

按当前 `insurance_saas` 的业务归档约定，应同步建立 `biz_merchant_staff_archive` 和 `biz_merchant_staff_role_archive`；归档表保存业务字段以及归档批次、归档时间、归档原因。`biz_merchant_category` 属于低频配置字典，优先保留变更日志，不参与普通业务数据夜间归档。

### 4.2 车险工单

#### biz_workorder 工单主表

短期可以在现有 `workorder` 表增加 `enterprise_id`，并按下表逐步重命名。长期建议拆宽表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 工单 ID |
| enterprise_id | bigint | 企业 ID |
| code | varchar(100) NOT NULL | 工单编号 |
| type | tinyint | 0 旧车，1 新车 |
| owner_type | tinyint | 0 个人，1 组织 |
| owner_name | varchar(100) | 车主姓名 |
| owner_phone | varchar(20) | 车主电话 |
| owner_id_num | varchar(50) | 证件号 |
| organization_name | varchar(100) | 组织名称 |
| social_credit_code | varchar(100) | 统一社会信用代码 |
| create_merchant_id | bigint | 创建方下游商户 |
| source_staff_id | bigint | 业务来源人员，关联 `biz_merchant_staff`，可为空 |
| handle_merchant_id | bigint | 处理方商户 |
| insurance_merchant_id | bigint | 承保上游机构 |
| area_code | varchar(20) | 业务区域 |
| status | tinyint | 工单状态 |
| remind_status | tinyint | 续保跟进状态 |
| follow_up_res | varchar(1000) | 跟进结果 |
| remark | varchar(500) | 备注 |
| created_by | bigint | 实际创建工单的系统用户，关联 `tenant_user` |
| handle_by | bigint | 实际处理工单的系统用户，关联 `tenant_user` |
| updated_by | bigint | 实际更新工单的系统用户，关联 `tenant_user` |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 软删除 |

建议索引：

| 名称 | 字段 | 说明 |
| --- | --- | --- |
| uk_biz_workorder_enterprise_code | enterprise_id,code | 企业内工单号永久唯一，软删除后也不复用 |
| idx_workorder_status | enterprise_id,status,created_at | 工单列表 |
| idx_workorder_create_merchant | enterprise_id,create_merchant_id,created_at | 下游商户工单 |
| idx_workorder_source_staff | enterprise_id,source_staff_id,created_at | 商户人员来源工单 |
| idx_workorder_handle_by | enterprise_id,handle_by,status | 出单员待办 |
| idx_workorder_owner | enterprise_id,owner_phone,owner_name | 车主查询 |

#### biz_workorder_quote 工单报价表

承接现有 `workorder` 中报价、费率、金额字段。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | ID |
| enterprise_id | bigint | 企业 ID |
| workorder_id | bigint | 工单 ID |
| quotation_no | varchar(100) | 报价单号 |
| commercial_amount | decimal(12,2) | 商业险金额 |
| compulsory_amount | decimal(12,2) | 交强险金额 |
| vehicle_and_tax_amount | decimal(12,2) | 车船税 |
| non_motor_amount | decimal(12,2) | 非车险金额 |
| non_motor_insurance_name | varchar(100) | 非车险名称 |
| non_motor_coverage_amount | decimal(12,2) | 非车保障金额 |
| quotation_remark | varchar(500) | 报价备注 |
| quotation_failed_remark | varchar(500) | 报价失败原因 |
| quotation_time | datetime | 报价时间 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 软删除 |

#### biz_workorder_commission 工单上下游费用表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | ID |
| enterprise_id | bigint | 企业 ID |
| workorder_id | bigint | 工单 ID |
| side | varchar(20) | UPSTREAM, DOWNSTREAM |
| compute_type | tinyint | 0 税前，1 税后 |
| commercial_percentage | decimal(8,2) | 商业险比例 |
| compulsory_percentage | decimal(8,2) | 交强险比例 |
| vehicle_tax_percentage | decimal(8,2) | 车船税比例 |
| non_motor_percentage | decimal(8,2) | 非车险比例 |
| commercial_amount | decimal(12,2) | 商业险费用 |
| compulsory_amount | decimal(12,2) | 交强险费用 |
| vehicle_tax_amount | decimal(12,2) | 车船税费用 |
| non_motor_amount | decimal(12,2) | 非车险费用 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 软删除 |

建议唯一键：`enterprise_id, workorder_id, side, deleted`。

#### biz_workorder_payment 工单支付表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | ID |
| enterprise_id | bigint | 企业 ID |
| workorder_id | bigint | 工单 ID |
| required_pay_amount | decimal(12,2) | 应付金额 |
| payee_staff_id | bigint | 自动选中的默认收款人，关联 `biz_merchant_staff` |
| payee_name | varchar(100) | 收款人姓名快照 |
| payee_phone | varchar(20) | 收款人手机号快照 |
| payee_id_num | varchar(100) | 收款人证件号快照 |
| merchant_bank | varchar(100) | 创建方商户开户行快照 |
| merchant_bank_card_num | varchar(100) | 创建方商户银行卡号快照 |
| pay_remark | varchar(500) | 支付备注 |
| pay_failed_remark | varchar(500) | 支付失败原因 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 软删除 |

创建支付记录时，根据 `biz_workorder.create_merchant_id` 查询创建方商户及其唯一的默认 `PAYEE`：收款人姓名、手机号和证件号取自 `biz_merchant_staff`，开户行和银行卡号取自 `biz_merchant`，然后分别写入支付表快照字段。后续修改商户人员或商户银行卡信息不得覆盖历史工单支付快照；敏感字段应加密保存并按权限脱敏展示。

#### biz_workorder_underwriting 工单核保与出单表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | ID |
| enterprise_id | bigint | 企业 ID |
| workorder_id | bigint | 工单 ID |
| underwriting_remark | varchar(500) | 核保备注 |
| underwriting_failed_remark | varchar(500) | 核保失败原因 |
| underwriting_time | datetime | 核保时间 |
| commercial_policy_no | varchar(100) | 商业险保单号 |
| compulsory_policy_no | varchar(100) | 交强险保单号 |
| accept_insurance_remark | varchar(500) | 承保备注 |
| accept_insurance_failed_remark | varchar(500) | 承保失败原因 |
| finish_time | datetime | 完成时间 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 软删除 |

#### biz_workorder_logistics 工单物流表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | ID |
| enterprise_id | bigint | 企业 ID |
| workorder_id | bigint | 工单 ID |
| tracking_num | varchar(100) | 快递单号 |
| logistics_company | varchar(100) | 快递公司 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 软删除 |

### 4.3 车辆证件与 OCR 结果

以下表可从现有 `vehicle_license`、`vehicle_invoice`、`vehicle_certificate` 改造，核心是增加 `enterprise_id` 和必要索引。

#### biz_vehicle_license 行驶证表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | ID |
| enterprise_id | bigint | 企业 ID |
| workorder_id | bigint | 工单 ID |
| license_plate | varchar(50) | 车牌号 |
| vehicle_type | varchar(100) | 车辆类型 |
| owner_name | varchar(100) | 所有人 |
| usage_nature | varchar(100) | 使用性质 |
| brand_model | varchar(100) | 品牌型号 |
| vehicle_code | varchar(100) | 车架号 |
| engine_code | varchar(100) | 发动机号 |
| registration_date | datetime | 注册日期 |
| issue_date | datetime | 发证日期 |
| seats | int | 座位数 |
| approved_load_capacity | int | 核定载质量 |
| curb_weight | int | 整备质量 |
| is_transfer | varchar(20) | 是否过户 |
| transfer_date | datetime | 过户日期 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议索引：`enterprise_id, workorder_id`，`enterprise_id, license_plate`，`enterprise_id, vehicle_code`。

#### biz_vehicle_invoice 车辆发票表

保留现有字段并增加 `enterprise_id`、时间字段改为 `datetime`。建议索引：`enterprise_id, workorder_id`，`enterprise_id, vehicle_code`。

#### biz_vehicle_certificate 合格证表

保留现有字段并增加 `enterprise_id`、时间字段改为 `datetime`。建议索引：`enterprise_id, workorder_id`，`enterprise_id, vehicle_code`。

#### biz_ocr_record OCR 识别记录表

用于保存 OCR 调用结果和排查问题，调用次数仍以 Redis 计数为主。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | ID |
| enterprise_id | bigint | 企业 ID |
| user_id | bigint | 调用用户 |
| file_id | bigint | 文件 ID |
| ocr_type | varchar(50) | IdCard, BusinessLicense, CarInvoice 等 |
| provider | varchar(50) | ALIYUN |
| request_id | varchar(100) | 供应商请求 ID |
| success | tinyint | 是否成功 |
| result_json | json | 识别结果 |
| error_message | varchar(1000) | 错误信息 |
| created_at | datetime | 创建时间 |

建议索引：`enterprise_id,created_at`，`enterprise_id,ocr_type,created_at`。

### 4.4 保险配置

#### biz_insurance_product 保险产品/险种配置表

由现有 `insurance` 表改造。是否企业隔离取决于业务，如果所有企业共享险种配置，可将 `enterprise_id` 置空；如果允许企业自定义，必须带 `enterprise_id`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | ID |
| enterprise_id | bigint null | 企业 ID，null 表示平台公共 |
| name | varchar(100) | 名称 |
| type | tinyint | 原险种类型 |
| options_json | json | 可选项 |
| default_option_json | json | 默认选项 |
| deductible_options_json | json | 免赔选项 |
| default_deductible_option_json | json | 默认免赔选项 |
| remark | varchar(500) | 备注 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议索引：`enterprise_id,type,deleted`。

#### biz_workorder_insurance 工单险种关联表

由现有 `workorder_insurance` 改造，增加 `enterprise_id`，JSON 字段改为 MySQL `json` 类型。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | ID |
| enterprise_id | bigint | 企业 ID |
| workorder_id | bigint | 工单 ID |
| insurance_id | bigint | 险种 ID |
| option_json | json | 投保险种选项 |
| deductible_option_json | json | 免赔选项 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议索引：`enterprise_id,workorder_id`。

### 4.5 文件与附件

#### sys_file 文件表

由现有 `system_file` 改造。文件可以服务于门户、后台和车险业务，因此放在通用层。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 文件 ID |
| enterprise_id | bigint null | 企业 ID，平台文件可为空 |
| path | varchar(500) | OSS 路径 |
| file_name | varchar(100) | 原始文件名 |
| content_type | varchar(100) | MIME 类型 |
| file_size | bigint | 文件大小 |
| is_linked | tinyint | 是否已被业务引用 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议索引：`enterprise_id,created_at`，`is_linked,updated_at,deleted`。

#### biz_workorder_file 工单附件表

由现有 `workorder_file` 改造。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | ID |
| enterprise_id | bigint | 企业 ID |
| workorder_id | bigint | 工单 ID |
| file_id | bigint | 文件 ID |
| file_type | varchar(100) | 附件类型 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| updated_by | bigint | 更新人 |
| deleted | tinyint | 软删除 |

建议索引：`enterprise_id,workorder_id,file_type`。

## 5. 后台系统功能分组

后台系统主要面向平台运营人员，不进入企业租户后台。

### 5.1 套餐管理

使用表：

| 表 | 用途 |
| --- | --- |
| saas_plan | 增删改查套餐、价格、人数、时长、上架状态 |
| saas_order | 查看套餐订单 |
| saas_recharge_order | 查看充值订单 |
| saas_wallet | 查看企业钱包余额 |
| saas_wallet_transaction | 查看企业余额流水 |
| saas_subscription | 查看企业当前订阅 |

### 5.2 企业监控

使用表：

| 表 | 用途 |
| --- | --- |
| tenant_enterprise | 企业列表、状态、拥有者 |
| tenant_member | 企业人数、成员角色 |
| saas_usage_daily | 每日 OCR 和请求用量 |
| saas_usage_monthly | 每月 OCR 和请求用量 |
| saas_usage_archive_job | 归档任务状态 |

### 5.3 权限与平台管理员

使用表：

| 表 | 用途 |
| --- | --- |
| platform_user | 后台监控平台账号，供开发者、客服、运营人员使用 |
| platform_user_role | 后台平台用户与平台角色关联 |
| auth_role | 平台角色和企业角色定义，通过 `role_scope` 区分 |
| auth_permission | 门户、车险系统、后台权限 |
| auth_role_permission | 角色权限关联 |

## 6. 迁移建议

建议分阶段迁移，避免一次性重构风险过大。

### 6.1 第一阶段：补 SaaS 外壳

1. 新增 `tenant_user`、`platform_user`、`platform_user_role`、`tenant_enterprise`、`tenant_member`、`tenant_invite_code`、`saas_plan`、`saas_wallet`、`saas_recharge_order`、`saas_wallet_transaction`、`saas_order`、`saas_subscription`。
2. 先按旧角色和业务用途拆分 `insurance.user`：`admin`、`出单员` 等真实登录账号迁移到 `tenant_user`；后台开发者、客服、运营账号单独初始化到 `platform_user`；`联系人`、`店员`、`收款人` 不迁入任何登录用户表，留待第三阶段迁入商户人员表。
3. 创建一个默认企业，仅将真实登录用户挂到 `tenant_member`。商户人员不进入 `tenant_member`，也不占用套餐成员数。
4. 现有 `merchant` 暂不拆，只给 `workorder`、`merchant`、`system_file` 等核心表增加 `enterprise_id`。
5. 登录后 JWT 中增加 `user_id`、当前 `enterprise_id`、`role_code`、权限列表。

### 6.2 第二阶段：角色权限去硬编码

1. 将 `role/menu/role_menu` 升级到 `auth_role/auth_permission/auth_role_permission`。
2. 后端 `@PreAuthorize` 继续使用权限编码，但权限来源从完整角色权限表读取。
3. 前端菜单从权限表或接口动态生成，避免按角色名称过滤。
4. 企业拥有者角色变更只允许走转让接口，管理员和出单员由拥有者或管理员变更。
5. `联系人`、`店员`、`收款人` 不迁入 `auth_role`；删除通过这些名称判断系统权限的逻辑，商户人员职责只查询 `biz_merchant_staff_role.role_code`。

### 6.3 第三阶段：车险业务拆表

1. 先初始化 `biz_merchant_category`，按“机构→INSURANCE_ORG、车商店铺→DEALER_STORE、汽修厂→AUTO_REPAIR、代理人→AGENT”映射旧 `merchant.type`；空类型和未知类型进入人工核对清单。
2. 将 `merchant` 迁移为 `biz_merchant`，通过 `category_id` 关联分类，不再保存可自由填写的上下游判断字符串。
3. 将旧 `user` 中角色为 `联系人`、`店员`、`收款人` 的有效记录迁入 `biz_merchant_staff`，并分别写入 `CONTACT`、`CLERK`、`PAYEE` 角色关系。旧密码、审批状态和登录角色不迁移。
4. 迁移前按商户检查联系人和默认收款人冲突。真实旧库中已存在同一商户多联系人和多个收款人的情况：联系人必须人工确认唯一有效记录；多个收款人可以保留，但必须人工或按明确业务规则指定唯一默认收款人，禁止按查询顺序自动选择。
5. 将旧 `merchant.contact/phone` 与联系人角色数据交叉核对；旧 `merchant.bank/bank_card_num` 直接迁入 `biz_merchant.bank/bank_card_num`，不得迁入商户人员表。旧 `user.username` 仅按手机号迁入 `biz_merchant_staff.phone`，证件号必须取 `user.id_num`，不能延续现有代码将 `username` 写入 `pay_id_num` 的错误映射。不一致时输出迁移异常清单，不静默覆盖。
6. 将 `workorder` 宽表逐步拆出报价、费用、支付、核保、物流表；`created_by/handle_by/updated_by` 只引用真实系统用户，`source_staff_id/payee_staff_id` 引用商户人员。
7. 创建工单支付数据时分别保存默认收款人的人员信息快照和创建方商户的开户行、银行卡号快照，不让后续人员或商户资料变更影响历史工单。
8. OCR 结果落 `biz_ocr_record`，用量进入 Redis 计数。
9. 为所有企业业务表增加 `enterprise_id` 复合索引。

`insurance_saas` 实库已于 2026-07-14 按本节设计完成结构升级，执行脚本为 `other/insurance_saas_merchant_staff_delta.sql`。当前已移除 `biz_merchant.merchant_type/org_type/contact/phone`，保留 `bank/bank_card_num` 作为商户属性，并新增 `biz_merchant_category`、`biz_merchant_staff`、`biz_merchant_staff_role`、工单来源人员字段和拆分后的支付快照字段。后续环境应通过迁移脚本升级，不直接覆盖式重建已有业务表。

### 6.4 第四阶段：用量归档和后台运营

1. 请求过滤器或拦截器按当前企业写 Redis 请求量。
2. OCR 调用封装处写 Redis OCR 计数。
3. 每晚任务归档到 `saas_usage_daily` 和 `saas_usage_monthly`。
4. 后台页面按企业展示人数、套餐、到期时间、OCR 次数、请求次数。

## 7. 推荐核心建表清单

SaaS 功能：

| 板块 | 表 |
| --- | --- |
| 门户账号 | tenant_user |
| 企业租户 | tenant_enterprise, tenant_member, tenant_owner_transfer_log |
| 邀请码 | tenant_invite_code |
| 套餐订单与余额 | saas_plan, saas_wallet, saas_recharge_order, saas_wallet_transaction, saas_order, saas_subscription |
| 后台平台账号 | platform_user, platform_user_role |
| 后台权限 | auth_role, auth_permission, auth_role_permission |
| 用量统计 | saas_usage_daily, saas_usage_monthly, saas_usage_archive_job |

非 SaaS 功能：

| 板块 | 表 |
| --- | --- |
| 商户渠道 | biz_merchant_category, biz_merchant, biz_merchant_area |
| 商户人员 | biz_merchant_staff, biz_merchant_staff_role |
| 工单 | biz_workorder, biz_workorder_quote, biz_workorder_commission, biz_workorder_payment, biz_workorder_underwriting, biz_workorder_logistics |
| 车辆证件 | biz_vehicle_license, biz_vehicle_invoice, biz_vehicle_certificate |
| OCR | biz_ocr_record |
| 保险配置 | biz_insurance_product, biz_workorder_insurance |
| 文件附件 | sys_file, biz_workorder_file |
