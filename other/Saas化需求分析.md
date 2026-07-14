# 车险系统 SaaS 化需求分析与改造计划

## 1. 文档目标

本文基于当前系统代码逻辑、现有 `insurance` 数据库结构，以及新建的 `insurance_saas` 数据库表结构，整理 SaaS 化后的需求边界和系统改造计划。

当前系统是一个单租户车险业务系统，核心功能包括登录注册、用户管理、上下游商户管理、工单管理、续保提醒、OCR 识别、文件上传、保险配置、软删除归档和基础权限控制。SaaS 化后，需要在原有车险业务能力外增加门户平台、企业租户、套餐订单、后台监控平台、企业用量统计和租户级权限隔离。

## 2. 当前系统现状

### 2.1 当前后端模块

| 模块 | 当前入口 | 当前职责 | 主要问题 |
| --- | --- | --- | --- |
| 登录注册 | `LoginController`、`LoginServiceImpl`、`UserDetailServiceImpl` | 登录、注册、注销、邮箱验证码、忘记密码 | 只面向单一用户表 `user`，没有企业上下文、平台后台账号、企业切换 |
| 用户管理 | `UserController`、`UserServiceImpl`、`UserMapper.xml` | 系统用户、商户用户、用户审批、个人中心、修改密码 | `user` 同时承载账号、企业归属、商户用户；角色通过 `user_role` 绑定且部分逻辑按角色名称硬编码 |
| 角色权限 | `RoleController`、`RoleServiceImpl`、`MenuMapper`、`MyExpressionRoot` | 查询角色、登录时加载权限、`@PreAuthorize` 鉴权 | `menu` 只有权限编码，前端菜单也按 `all`、`user:update` 等硬编码显示 |
| 上游管理 | `UpstreamController`、`UpstreamServiceImpl`、`MerchantMapper.xml` | 机构、保司管理和选项查询 | 与下游共用 `merchant` 表，通过中文 `type` 区分，不带企业隔离字段 |
| 下游管理 | `DownstreamController`、`DownstreamServiceImp`、`MerchantMapper.xml` | 车商店铺、汽修厂、代理人管理 | 与上游共表，商户联系人等角色和用户表耦合 |
| 工单管理 | `WorkorderController`、`WorkorderServiceImpl`、`WorkorderMapper.xml` | 工单列表、详情、新建、接单、基础信息、报价、核保出单、删除、导出 | `workorder` 是宽表，混合基础信息、报价、支付、核保、物流、费用；不带 `enterprise_id` |
| 续保提醒 | `WorkorderController` 的 `/renew`、`/renew/count` | 按保险起期筛选续保工单、记录跟进状态 | 续保数据仍在 `workorder` 宽表中，未来需要按企业隔离 |
| OCR | `OCRController`、`OCRServiceImpl`、`OCRUtil` | 身份证、营业执照、行驶证、合格证、发票识别 | 没有企业维度的 OCR 调用记录和用量计数 |
| 文件 | `FileController`、`FileServiceImpl`、`OSSUtil` | 文件上传、OSS 临时地址、签名 | `system_file` 不带企业维度，文件引用和清理只按 `is_linked` |
| 保险配置 | `InsuranceController`、`InsuranceServiceImpl` | 查询保险险种配置 | 目前是全局配置，没有企业自定义或平台公共配置的明确边界 |
| 数据归档 | `DataArchive`、`DailyTaskScheduler` | 软删除数据转入 `_archive` 表、清理未引用文件 | 归档只处理业务软删除，不处理 SaaS 用量归档 |
| 请求计数 | `RequestCountFilter`、`MaintenanceManager` | 当前活跃请求数统计，用于维护等待 | 不是企业用量统计，不能用于后台监控分析 |

### 2.2 当前前端模块

| 页面/组件 | 当前路径 | 当前职责 | SaaS 化影响 |
| --- | --- | --- | --- |
| 登录页 | `/login`、`LoginPage.vue` | 登录、注册入口 | 需要区分企业门户登录和后台监控平台登录 |
| 首页框架 | `/home`、`HomePage.vue` | 车险系统主框架 | 登录后需要携带当前企业上下文 |
| 菜单 | `MenuComponent.vue` | 工单、续保、上下游、用户、个人中心 | 菜单应改为后端权限驱动，不能只靠 `all` 硬编码 |
| 工单管理 | `AllWorkOrder.vue` | 工单查询、添加、接单/转移、导出 | 所有查询、导出、操作必须按企业隔离 |
| 续保管理 | `RenewWorkorder.vue` | 续保筛选、跟进 | 企业内数据隔离，跟进状态继续保留 |
| 工单详情/编辑 | `DetailWorkorder.vue`、`EditBaseWorkorder.vue` | 工单全流程编辑 | 后端拆表后前端 DTO 可暂时保持，后端做聚合适配 |
| 上游管理 | `UpStream.vue`、`EditUpstream.vue` | 机构/保司管理 | 改为 `biz_merchant.merchant_type=UPSTREAM` |
| 下游管理 | `DownstreamMerchant.vue`、`EditDownstreamMerchant.vue` | 车商店铺等下游商户 | 改为 `biz_merchant.merchant_type=DOWNSTREAM` |
| 商户用户 | `DownstreamUser.vue`、`EditDownstreamUser.vue` | 商户联系人、收款人等 | 需要和企业成员管理拆开，业务联系人不要混在企业用户角色里 |
| 用户管理 | `UserManagement.vue`、`UserApproval.vue` | 用户列表、审批、角色变更 | 改为企业成员管理：拥有者、管理员、出单员 |
| 个人中心 | `PersonalCenter.vue` | 个人资料、修改密码 | 改为读取 `tenant_user` 和当前企业成员角色 |

### 2.3 现有设计的主要风险

1. 没有租户边界，任何业务查询都缺少 `enterprise_id`。
2. 企业用户、系统用户、商户联系人、收款人等混在 `user` 表和 `user_role` 中。
3. 上游和下游商户通过中文 `type` 判断，扩展和迁移成本高。
4. 权限菜单存在数据库表，但页面和代码仍大量硬编码。
5. 工单宽表会让 SaaS 化后的字段权限、流程拆分、导出和性能优化变复杂。
6. OCR 和请求量只有调用逻辑，没有企业用量统计链路。
7. 后台监控平台用户不能与企业用户共用账号表，否则内部客服/开发权限容易和客户权限混淆。

## 3. SaaS 相关需求

### 3.1 门户平台

门户平台面向企业客户，用于注册账号、创建企业、输入邀请码加入企业、切换企业和进入车险系统。

功能需求：

1. 用户可以注册企业用户账号，账号写入 `tenant_user`。
2. 用户登录后如果没有企业成员关系，必须选择创建企业或输入邀请码加入企业。
3. 用户可以创建企业，创建后写入 `tenant_enterprise`，并在 `tenant_member` 中创建 `OWNER` 成员关系。
4. 用户可以输入邀请码加入企业，加入后写入 `tenant_member`，默认 `role_code=ISSUER`。
5. 用户可查看自己加入的企业列表，并选择当前企业进入车险系统。
6. JWT 或登录态必须包含 `user_id`、`enterprise_id`、`role_code`、权限列表、账号类型。

涉及表：

| 表 | 用途 |
| --- | --- |
| `tenant_user` | 企业客户账号 |
| `tenant_enterprise` | 企业租户 |
| `tenant_member` | 用户在企业内的身份 |
| `tenant_invite_code` | 企业邀请码 |
| `auth_role`、`auth_permission`、`auth_role_permission` | 企业侧权限 |

### 3.2 企业成员与角色

企业内角色固定为企业拥有者、管理员、出单员。

功能需求：

1. 企业拥有者不可直接修改为其他角色，只能通过拥有者转让产生变化。
2. 企业拥有者转让给企业内其他有效成员后，原拥有者自动变为管理员。
3. 一个企业同时只能有一个拥有者，以 `tenant_enterprise.owner_user_id` 作为当前拥有者事实来源。
4. 管理员和出单员可由拥有者或管理员变更。
5. 通过邀请码加入企业的用户默认是出单员。
6. 禁用成员时，只影响其在当前企业内的身份，不应禁用其全局账号。

涉及表：

| 表 | 用途 |
| --- | --- |
| `tenant_member` | 成员角色与状态 |
| `tenant_owner_transfer_log` | 拥有者转让记录 |
| `tenant_enterprise` | 当前拥有者字段 |

### 3.3 邀请码

邀请码用于企业成员自助加入企业。

功能需求：

1. 企业拥有者或管理员可生成邀请码。
2. 邀请码具有过期时间，例如一天。
3. 邀请码可设置最大使用次数，不设置则不限次数。
4. 使用邀请码时校验状态、过期时间和使用次数。
5. 成功加入企业后，`tenant_invite_code.used_count + 1`。
6. 不单独保存邀请码使用日志，成员来源通过 `tenant_member.joined_by_invite_id` 追溯。

涉及表：

| 表 | 用途 |
| --- | --- |
| `tenant_invite_code` | 邀请码主体 |
| `tenant_member` | 记录来源邀请码 |

### 3.4 套餐、企业余额、订单与订阅

企业拥有者可以为企业充值余额，也可以使用企业余额购买系统套餐。余额绑定企业，不绑定个人用户；用户只作为充值、购买、续费、变更套餐的操作人记录在订单和流水中。

功能需求：

1. 后台平台维护套餐，包括价格、人数、时长、上架状态和展示说明。
2. 每个企业拥有一个企业钱包，记录当前可用余额。
3. 用户可为企业充值余额，充值时创建 `saas_recharge_order`，支付成功后增加企业钱包余额并写入资金流水。
4. 企业拥有者购买套餐、手动续费、自动续费、变更套餐时都必须创建 `saas_order`。
5. 订单创建时保存套餐快照，避免后续套餐价格变化影响历史订单。
6. 订单支付成功后创建、续期或更新 `saas_subscription`。
7. 订阅生效后限制企业最大成员数和到期时间。
8. 企业拥有者可以开启到期自动续费，系统在到期前或到期时自动创建续费订单并从企业余额扣款。
9. 企业拥有者可以变更套餐，系统按原套餐剩余价值和新套餐剩余期间价值计算差额；差额为正时扣企业余额，差额为负时退回企业余额。
10. 企业余额每一次增加或减少都写入 `saas_wallet_transaction`，用于展示充值、购买套餐、续费、自动续费、套餐变更等流水。
11. 订阅到期且自动续费失败后，企业状态可变为欠费限制，只允许续费、充值和查看基础信息。

涉及表：

| 表 | 用途 |
| --- | --- |
| `saas_plan` | 套餐配置 |
| `saas_wallet` | 企业钱包余额 |
| `saas_recharge_order` | 充值订单 |
| `saas_wallet_transaction` | 余额流水 |
| `saas_order` | 套餐购买、续费、自动续费、套餐变更订单 |
| `saas_subscription` | 企业订阅 |

套餐变更计算规则：

1. 原套餐剩余价值 = 原套餐价格 * 剩余天数 / 原套餐周期天数。
2. 新套餐剩余期间价值 = 新套餐价格 * 剩余天数 / 新套餐周期天数。
3. 差额 = 新套餐剩余期间价值 - 原套餐剩余价值。
4. 差额大于 0 时创建套餐变更订单并扣减企业余额。
5. 差额小于 0 时创建套餐变更订单并退回企业余额。
6. 变更立即生效，订阅到期时间默认不变。
7. 降级后如果当前企业成员数超过新套餐人数上限，则不允许变更。

### 3.5 后台监控平台

后台监控平台面向内部开发者、客服、运营人员，与企业客户账号完全分开。

功能需求：

1. 后台账号使用 `platform_user`，不允许使用 `tenant_user` 登录后台。
2. 后台平台角色通过 `platform_user_role` 绑定。
3. 后台可维护套餐、查看订单、查看企业、查看成员数量、查看订阅到期时间。
4. 后台可查看企业 OCR 用量、接口请求量、日/月统计和归档任务。
5. 客服可协助查看企业信息，但原则上不直接进入客户企业业务数据；如需代操作，应另行设计审计。

涉及表：

| 表 | 用途 |
| --- | --- |
| `platform_user` | 后台平台账号 |
| `platform_user_role` | 平台账号角色 |
| `auth_role`、`auth_permission`、`auth_role_permission` | 后台权限 |
| `tenant_enterprise`、`tenant_member` | 企业监控 |
| `saas_plan`、`saas_wallet`、`saas_recharge_order`、`saas_wallet_transaction`、`saas_order`、`saas_subscription` | 套餐、余额、订单与订阅 |
| `saas_usage_daily`、`saas_usage_monthly`、`saas_usage_archive_job` | 用量监控 |

### 3.6 用量统计与归档

请求量和 OCR 次数用于后台监控、运营分析和异常排查，不作为客户套餐中的可见额度指标。考虑到实时写库成本高，先写 Redis，夜间归档到 MySQL。

功能需求：

1. 请求进入系统后，根据当前登录态获取 `enterprise_id`，按企业、日期、接口维度写 Redis。
2. OCR 调用成功或发起时，根据当前企业、OCR 类型写 Redis。
3. 夜间任务将 Redis 日统计归档到 `saas_usage_daily`。
4. 月统计写入 `saas_usage_monthly`。
5. 每次归档写入 `saas_usage_archive_job`，记录批次、状态和错误信息。
6. 用量统计允许短暂不精确，不影响核心业务事务。

涉及表：

| 表 | 用途 |
| --- | --- |
| `saas_usage_daily` | 日统计 |
| `saas_usage_monthly` | 月统计 |
| `saas_usage_archive_job` | 归档任务 |

## 4. 非 SaaS 相关需求

非 SaaS 需求指车险系统原有业务能力，但需要适配新的租户数据库结构。

### 4.1 商户渠道管理

当前上游、下游都在 `merchant` 表中，通过中文 `type` 区分。新结构使用 `biz_merchant`，通过 `merchant_type` 区分上游和下游。

功能需求：

1. 上游管理对应 `merchant_type=UPSTREAM`，承载机构、保司等。
2. 下游管理对应 `merchant_type=DOWNSTREAM`，承载车商店铺、汽修厂、代理人等。
3. 所有商户必须归属当前企业 `enterprise_id`。
4. 业务区域迁移到 `biz_merchant_area`。
5. 上游/下游选项查询必须按当前企业过滤。

涉及表：

| 表 | 用途 |
| --- | --- |
| `biz_merchant` | 上游/下游商户 |
| `biz_merchant_area` | 商户业务区域 |

### 4.2 企业成员与业务联系人拆分

当前商户联系人、收款人等角色通过 `user` 和 `role` 维护，容易与系统登录角色混淆。新结构中，企业成员只保留拥有者、管理员、出单员；商户联系人建议作为商户业务字段或后续独立业务联系人表。

短期需求：

1. 企业成员管理只维护 `tenant_member.role_code`。
2. 下游商户联系人暂时可保留在 `biz_merchant.contact`、`phone` 字段。
3. 如果必须维护多个联系人和收款人，建议后续新增 `biz_merchant_contact`，不要复用企业登录账号表。

### 4.3 工单管理

当前 `workorder` 宽表拆分为多个表。为了降低前端改造成本，建议后端先做聚合 DTO，保持前端字段结构基本稳定。

功能需求：

1. 工单主信息写入 `biz_workorder`。
2. 报价信息写入 `biz_workorder_quote`。
3. 上下游费用写入 `biz_workorder_commission`，按 `side=UPSTREAM/DOWNSTREAM` 区分。
4. 支付信息写入 `biz_workorder_payment`。
5. 核保和出单信息写入 `biz_workorder_underwriting`。
6. 物流信息写入 `biz_workorder_logistics`。
7. 附件写入 `biz_workorder_file`，文件主体写入 `sys_file`。
8. 工单列表、详情、导出均必须按 `enterprise_id` 过滤。

涉及表：

| 表 | 用途 |
| --- | --- |
| `biz_workorder` | 工单主表 |
| `biz_workorder_quote` | 报价 |
| `biz_workorder_commission` | 上下游费用 |
| `biz_workorder_payment` | 支付 |
| `biz_workorder_underwriting` | 核保与出单 |
| `biz_workorder_logistics` | 物流 |
| `biz_workorder_file` | 工单附件 |

### 4.4 续保提醒

续保提醒仍是工单能力的一部分。

功能需求：

1. 按 `biz_workorder` 中商业险/交强险起期或后续保单信息查询续保范围。
2. `remind_status` 和 `follow_up_res` 继续保留在 `biz_workorder`。
3. 查询时必须按当前企业过滤。
4. 续保列表导出也必须按企业过滤。

### 4.5 车辆证件与 OCR

当前行驶证、发票、合格证和 OCR 识别结果直接服务于工单录入。新结构中证件表增加企业维度，OCR 增加调用记录。

功能需求：

1. 行驶证写入 `biz_vehicle_license`。
2. 车辆发票写入 `biz_vehicle_invoice`。
3. 合格证写入 `biz_vehicle_certificate`。
4. OCR 每次调用可写 `biz_ocr_record`，用于排查识别错误。
5. OCR 用量另行写 Redis，不依赖 `biz_ocr_record` 做实时统计。

### 4.6 保险配置

当前 `insurance` 表是全局险种配置。新结构使用 `biz_insurance_product`，支持平台公共配置和企业自定义配置。

功能需求：

1. `enterprise_id IS NULL` 表示平台公共险种。
2. `enterprise_id = 当前企业` 表示企业自定义险种。
3. 工单险种选择写入 `biz_workorder_insurance`。
4. 查询险种时默认返回公共险种和当前企业自定义险种。

### 4.7 文件管理

当前 `system_file` 改为 `sys_file`。

功能需求：

1. 上传文件写入 `sys_file`。
2. 企业业务文件必须写入 `enterprise_id`。
3. 平台文件可允许 `enterprise_id` 为空。
4. 工单附件通过 `biz_workorder_file` 关联。
5. 未引用文件清理逻辑从 `system_file` 改为 `sys_file`。

### 4.8 数据归档

当前软删除归档由 `DataArchive` 处理。新数据库为所有带 `deleted` 字段的表创建了 `_archive` 表。

功能需求：

1. 原 `is_delete` 统一改为 `deleted`。
2. 归档任务扫描 `deleted=1` 的数据。
3. 归档表不需要触发器。
4. 归档时需要覆盖 SaaS 表和业务表。
5. 用量归档和软删除归档分开处理，避免概念混淆。

## 5. 总体改造策略

建议采用“先兼容、再替换、最后拆宽表”的方式，而不是一次性全量重写。

### 5.1 第一阶段：基础 SaaS 外壳

目标是让系统具备企业租户、企业用户、当前企业上下文。

工作项：

1. 新增 `insurance_saas` 数据源配置，切换或并行访问新库。
2. 新增实体类和 Mapper：`TenantUser`、`TenantEnterprise`、`TenantMember`、`TenantInviteCode`。
3. 改造登录逻辑，登录 `tenant_user`，返回可选企业列表。
4. 新增企业创建接口。
5. 新增邀请码加入企业接口。
6. 登录态增加当前企业上下文。
7. 新增 `TenantContext` 或类似上下文工具，从 JWT/Redis 中读取当前 `enterprise_id`。

验收标准：

1. 用户可注册登录。
2. 用户可创建企业并成为拥有者。
3. 用户可输入邀请码加入企业成为出单员。
4. 登录后能选择企业进入车险系统。

### 5.2 第二阶段：权限和成员管理

目标是替换现有用户角色逻辑，完成企业内三角色。

工作项：

1. 新增 `auth_role`、`auth_permission`、`auth_role_permission` 的初始化数据。
2. 改造 `UserDetailServiceImpl`，按当前企业加载成员角色和权限。
3. 改造 `MyExpressionRoot.hasAuthority`，继续兼容 `@PreAuthorize`。
4. 改造 `UserController` 为企业成员管理接口。
5. 删除或迁移 `UserApproval` 的审批逻辑，改为邀请码加入或成员禁用/启用。
6. 实现拥有者转让接口，写 `tenant_owner_transfer_log`。
7. 前端 `UserManagement.vue` 改为企业成员管理页面。
8. 前端隐藏企业拥有者角色编辑入口，只保留转让操作。

验收标准：

1. 企业拥有者不可被普通角色修改。
2. 转让后新用户成为拥有者，原拥有者变为管理员。
3. 管理员和出单员可按权限变更。
4. 前后端菜单和按钮按权限显示。

### 5.3 第三阶段：后台监控平台

目标是建立内部平台，不与企业账号混用。

工作项：

1. 新增后台登录接口，使用 `platform_user`。
2. 新增 `PlatformUserDetailsService` 或在登录入口按账号类型拆分认证。
3. 新增平台用户管理、平台角色管理。
4. 新增套餐管理页面和接口。
5. 新增企业列表、企业详情、成员数量、订阅状态查看。
6. 新增订单列表、订单详情。
7. 新增用量看板，读取 `saas_usage_daily`、`saas_usage_monthly`。
8. 前端建议新建独立后台路由，例如 `/admin/login`、`/admin/home`。

验收标准：

1. 平台账号不能登录企业门户。
2. 企业账号不能登录后台监控平台。
3. 客服可查看企业、订单、用量。
4. 开发/运维可查看归档任务和接口用量。

### 5.4 第四阶段：企业余额、套餐订单与订阅

目标是实现企业充值余额、余额流水、套餐购买、手动续费、自动续费、套餐变更，系统按订阅控制使用。

工作项：

1. 实现 `SaasPlanController`：套餐查询、后台增删改查。
2. 实现 `SaasWalletController`：查看企业余额、查看余额流水。
3. 实现 `SaasRechargeOrderController`：创建充值订单、查询充值订单、处理支付回调或模拟支付确认。
4. 充值成功后更新 `saas_wallet` 并写 `saas_wallet_transaction` 入账流水。
5. 实现 `SaasOrderController`：创建购买订单、续费订单、自动续费订单、套餐变更订单、查询订单、取消订单。
6. 购买或续费订单支付成功后扣企业余额、写 `saas_wallet_transaction` 出账流水，并创建或续期 `saas_subscription`。
7. 订阅支持 `auto_renew_enabled`、`next_renew_at` 等自动续费字段。
8. 新增自动续费定时任务，到期时创建 `AUTO_RENEW` 订单，余额足够则自动扣款续期，余额不足则保留失败或待支付订单。
9. 新增套餐变更服务，根据剩余天数计算原套餐剩余价值和新套餐剩余期间价值，差额为正扣余额，差额为负退余额。
10. 企业成员新增时检查 `user_limit`。
11. 登录或请求拦截时检查订阅状态和到期时间。
12. 到期且自动续费失败的企业进入欠费限制状态。

验收标准：

1. 企业拥有者可充值企业余额。
2. 每次充值、购买、续费、自动续费、套餐变更都能看到余额流水。
3. 拥有者可创建套餐购买订单。
4. 订单支付后订阅生效。
5. 自动续费开启后，到期可自动创建续费订单并扣余额。
6. 套餐变更可自动计算补扣或退回企业余额。
7. 超过人数限制不能继续邀请或新增成员。
8. 订阅过期后业务接口被限制，充值和续费入口可用。

### 5.5 第五阶段：商户模块迁移

目标是将 `merchant` 和 `merchant_area` 迁移到 `biz_merchant`、`biz_merchant_area`。

后端改造：

1. `Merchant` 实体改为 `BizMerchant`，字段 `type` 拆为 `merchant_type` 和 `org_type`。
2. `UpstreamServiceImpl` 查询增加 `enterprise_id` 和 `merchant_type=UPSTREAM`。
3. `DownstreamServiceImp` 查询增加 `enterprise_id` 和 `merchant_type=DOWNSTREAM`。
4. `MerchantMapper.xml` 所有 SQL 改为新表名和新字段。
5. 商户区域查询改用 `biz_merchant_area`。
6. 删除或替换按中文 `type IN (...)` 判断上下游的逻辑。

前端改造：

1. `UpStream.vue` 页面字段基本可保持。
2. `EditUpstream.vue` 保存时传 `orgType`，后端固定 `merchantType=UPSTREAM`。
3. `DownstreamMerchant.vue` 保存时传 `orgType`，后端固定 `merchantType=DOWNSTREAM`。
4. 下游用户页面不再管理企业成员，后续如需要可改为商户联系人管理。

验收标准：

1. 每个企业只能看到自己的上下游商户。
2. 上游和下游不再依赖中文类型区分业务边界。
3. 工单创建时只能选择当前企业的商户。

### 5.6 第六阶段：工单模块迁移

目标是拆分 `workorder` 宽表，同时保持前端交互稳定。

后端改造：

1. 新增聚合 DTO，对外仍返回接近当前 `WorkorderDTO` 的结构。
2. `WorkorderMapper.xml` 从单表宽查询改为多表聚合查询。
3. 新建工单时写 `biz_workorder`、车辆证件表、附件表和险种表。
4. 基础信息更新只更新 `biz_workorder` 和车辆证件相关表。
5. 报价更新写 `biz_workorder_quote` 和 `biz_workorder_commission`。
6. 支付更新写 `biz_workorder_payment`。
7. 核保/出单更新写 `biz_workorder_underwriting`。
8. 物流更新写 `biz_workorder_logistics`。
9. 删除工单时软删除主表和关联子表。
10. 所有查询强制追加当前 `enterprise_id`。

前端改造：

1. `AllWorkOrder.vue` 查询参数基本保留。
2. `EditBaseWorkorder.vue` 不必一次性大改，后端负责聚合和拆分保存。
3. `DetailWorkorder.vue` 继续展示聚合详情。
4. 导出字段保持现有格式，后端从多表组装。

验收标准：

1. 工单列表、详情、新建、编辑、删除功能正常。
2. 企业之间看不到彼此工单。
3. 报价、支付、核保、物流数据正确进入各子表。
4. 导出结果与旧系统字段含义一致。

### 5.7 第七阶段：续保模块迁移

目标是保持当前续保功能，同时按企业隔离。

后端改造：

1. `/workorder/renew` 改查 `biz_workorder`。
2. 续保筛选字段从新表读取。
3. `remind_status`、`follow_up_res` 更新到 `biz_workorder`。
4. `/workorder/renew/count` 按企业统计。

前端改造：

1. `RenewWorkorder.vue` 基本保持。
2. 跟进弹窗提交接口不变或仅调整字段。

验收标准：

1. 续保列表按企业隔离。
2. 跟进状态更新正确。
3. 统计数量正确。

### 5.8 第八阶段：OCR 和文件模块迁移

目标是将文件和 OCR 纳入企业维度，并接入用量统计。

后端改造：

1. `SystemFile` 改为 `SysFile`，表名改为 `sys_file`。
2. 文件上传写入当前 `enterprise_id`。
3. `WorkorderFile` 改为 `biz_workorder_file`。
4. OCR 调用后写入 `biz_ocr_record`。
5. OCR 调用处写 Redis 用量计数。
6. 未引用文件清理改查 `sys_file`。

前端改造：

1. 文件上传接口可保持路径不变。
2. OCR 接口返回结构尽量保持，减少工单编辑页面变更。

验收标准：

1. 文件只能被当前企业业务引用。
2. OCR 识别结果正常回填。
3. 后台能看到企业 OCR 用量。

### 5.9 第九阶段：保险配置迁移

目标是支持平台公共险种和企业自定义险种。

后端改造：

1. `Insurance` 实体迁移到 `BizInsuranceProduct`。
2. `/insurance/all` 查询公共险种和当前企业自定义险种。
3. 工单险种关联改为 `biz_workorder_insurance`。
4. JSON 字段改用 MySQL `json` 类型，Java 层可继续以字符串或对象处理。

前端改造：

1. 工单编辑页面的险种选择尽量保持。
2. 如后续开放企业自定义险种，再新增配置页面。

验收标准：

1. 原有险种可正常选择。
2. 工单险种保存和详情展示正常。

### 5.10 第十阶段：用量统计和归档

目标是完成 SaaS 后台监控数据闭环。

后端改造：

1. 改造 `RequestCountFilter` 或新增请求统计拦截器。
2. 统计 key 包含日期、企业、指标、接口或模块。
3. OCR 统计独立写入 Redis。
4. 新增夜间归档任务，将 Redis 写入 `saas_usage_daily`、`saas_usage_monthly`。
5. 新增归档任务记录 `saas_usage_archive_job`。
6. 改造 `DataArchive`，支持新表 `_archive` 归档。

后台前端：

1. 企业用量列表。
2. 企业日/月用量详情。
3. 归档任务状态页面。

验收标准：

1. 请求和 OCR 用量能按企业统计。
2. 夜间归档成功后 MySQL 可查询统计结果。
3. 归档失败有错误记录。

## 6. 模块级改造清单

| 模块 | 当前文件/页面 | 新表 | 改造重点 |
| --- | --- | --- | --- |
| 企业登录 | `LoginController`、`LoginServiceImpl` | `tenant_user`、`tenant_member` | 登录返回企业列表和当前企业上下文 |
| 后台登录 | 新增 | `platform_user`、`platform_user_role` | 与企业账号完全分离 |
| 企业门户 | 新增前端页面 | `tenant_enterprise`、`tenant_invite_code` | 创建企业、加入企业、切换企业 |
| 成员管理 | `UserController`、`UserManagement.vue` | `tenant_member` | OWNER/ADMIN/ISSUER 三角色 |
| 拥有者转让 | 新增 | `tenant_owner_transfer_log` | 事务更新企业拥有者和成员角色 |
| 权限 | `RoleController`、`MenuMapper`、`MenuComponent.vue` | `auth_role`、`auth_permission` | 后端权限驱动菜单 |
| 套餐 | 新增后台模块 | `saas_plan` | 后台维护套餐 |
| 企业钱包 | 新增企业侧模块 | `saas_wallet` | 企业余额查看、余额状态 |
| 充值订单 | 新增企业侧模块 | `saas_recharge_order` | 企业余额充值、支付确认 |
| 余额流水 | 新增企业侧模块 | `saas_wallet_transaction` | 充值、购买、续费、套餐变更流水 |
| 套餐订单 | 新增企业侧模块 | `saas_order` | 购买、续费、自动续费、套餐变更 |
| 订阅 | 新增服务 | `saas_subscription` | 到期、人数限制、自动续费配置 |
| 后台监控 | 新增后台模块 | `saas_usage_daily`、`saas_usage_monthly` | 企业用量监控 |
| 上游 | `UpstreamController`、`UpStream.vue` | `biz_merchant` | `merchant_type=UPSTREAM` |
| 下游 | `DownstreamController`、`DownstreamMerchant.vue` | `biz_merchant` | `merchant_type=DOWNSTREAM` |
| 商户区域 | `MerchantController` | `biz_merchant_area` | 企业隔离和区域查询 |
| 工单 | `WorkorderController`、`AllWorkOrder.vue` | `biz_workorder` 等 | 后端聚合 DTO，前端低改造 |
| 续保 | `RenewWorkorder.vue` | `biz_workorder` | 按企业筛选续保 |
| 文件 | `FileController` | `sys_file`、`biz_workorder_file` | 企业文件隔离 |
| OCR | `OCRController`、`OCRUtil` | `biz_ocr_record` | 记录识别结果和 Redis 用量 |
| 保险配置 | `InsuranceController` | `biz_insurance_product` | 公共配置和企业配置 |
| 归档 | `DataArchive` | 各 `_archive` 表 | `deleted=1` 归档 |

## 7. 推荐实施顺序

1. 先实现企业账号、企业、成员、当前企业上下文。
2. 再改造权限和菜单，确保后续所有模块都有统一鉴权。
3. 建立后台平台账号和套餐管理。
4. 实现订单、订阅和企业限制。
5. 完成企业钱包、充值订单、余额流水、套餐购买、自动续费和套餐变更。
6. 迁移商户模块，因为工单依赖商户。
7. 迁移文件和 OCR，因为工单编辑依赖附件和识别。
8. 迁移工单宽表到多表结构。
9. 迁移续保、导出、归档等依赖工单查询的功能。
10. 最后完善用量统计、后台监控和性能索引。

## 8. 关键验收标准

1. 任意企业用户只能看到自己当前企业的数据。
2. 后台平台账号和企业账号完全分离。
3. 企业拥有者唯一，且只能通过转让变更。
4. 邀请码加入企业后默认出单员。
5. 充值、套餐购买、续费、自动续费、套餐变更都能生成订单或流水。
6. 套餐购买后生成订单和订阅。
7. 成员人数和订阅到期可被系统校验，OCR 和请求量仅进入后台监控。
7. 工单、商户、文件、OCR、保险配置均能按 `enterprise_id` 隔离。
8. 前端核心业务流程可继续完成：新建工单、OCR 回填、报价、核保出单、续保跟进、导出。
9. 后台监控平台可查看套餐、订单、企业、成员数量、用量统计和归档任务。
10. 归档表保存软删除数据，归档表不触发更新时间。
