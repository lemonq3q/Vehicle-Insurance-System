# 系统优化实施与上线审阅

更新日期：2026-10-30。前后端代码、mock、API 文档和三份 Excel 模板已完成；2026-10-30已在本地 localhost:3306/insurance_saas 执行增量迁移并核验，服务器数据库尚未操作。

## 已采纳的关联处理

| 事项 | 已确认的处理方式 |
| --- | --- |
| 主体表 | 保留上下游共用的 biz_merchant，删除独立商户人员及角色体系 |
| 下游资料 | 联系人、手机号合并到基础信息；移除人员管理入口和接口 |
| 工单提交人 | 改为可直接录入的姓名快照，新单默认使用下游联系人 |
| 历史资料 | 保留旧提交人、收款资料与实际录入人审计；不随联系人更新覆盖 |
| 接单、派单 | 处理人统一选择本企业系统用户，保留已有处理人记录；保存时再校验企业成员和启用状态 |
| 配套业务 | 同步查询、导出、归档、企业清理和租户表配置 |

## 功能落地与主要文件

| 需求 | 已实现行为 | 主要文件（仓库内路径） |
| --- | --- | --- |
| 下游联系人 | 基础表单直接维护 contact/phone，选店默认填入 submitterName；录入人由服务端维护；新单收款资料形成快照 | frontend/src/components/EditDownstreamMerchant.vue、EditBaseWorkorder.vue；backend/common 的 Merchant、DownstreamDTO、Workorder；DownstreamServiceImp、WorkorderServiceImpl 及对应 Mapper |
| 名称唯一 | 手工新增编辑和导入校验同企业同方向有效名称；数据库唯一键防并发，软删除允许复用 | MerchantNameValidationService；MerchantBatchImportServiceImpl；合并增量脚本 |
| 工单导入 | 工单管理页使用现有导入弹窗；93列按基础、客户和证件、报价、支付、承保排序；不导入附件；名称关联机构、非空身份条件 AND 匹配本企业用户，重复取最小ID | AllWorkOrder.vue；WorkorderBatchImportService、WorkorderImportSchema、WorkorderImportRowWriter；resources/import/workorder-columns.json |
| 投保推导 | 商业保费大于0使用合法默认投保项，否则不投保；交强、车船税分别按自身金额判断；强制险与增值服务详情分类已纠正 | WorkorderBatchImportService；DetailWorkorder.vue |
| OCR | 识别结果只形成原字段旁候选；可逐项或一键应用；空结果不覆盖，上传及切换清除失效候选 | EditBaseWorkorder.vue、OcrCandidate.vue、utils/ocrCandidates.js |
| 双险续保 | 商业、交强分别按原起期计算年度周期，工单去重提醒与双tag；分项完成或提前全部完成；每年重启；关闭未来提醒需警告确认 | RenewWorkorder.vue；RenewalCalendar、RenewalFollowUpRequest、WorkorderServiceImpl、WorkorderMapper.xml |
| 归档清理 | 移除旧人员表依赖，归档跳过生成列，保留完整历史工单快照 | DataArchive、EnterpriseBusinessDataPurgeService、MybatisPlusConfig |

名称唯一范围为当前企业 + 上游/下游方向 + 去首尾空白的有效名称，不限制其他企业；跨方向可同名。工单导入每行独立事务，失败行整体回滚并返回行号，后续行继续；重传时只提交失败行，当前不提供重复导入幂等更新。

日历示例：2026-10-29 起期首轮提醒为 2027-09-29；2026-03-31 为 2027-02-28；2027-03-31 为 2028-02-29；2024-02-29 为 2025-01-29。每轮从原起期平移 12*n-1 个月，避免中间截断或累计偏移。

## Mock 与 API 文档

页面默认走现有真实请求封装；离线联调样例独立放置，可由请求适配器返回：

- frontend/src/mock/downstreamContactMock.js：下游详情、列表、空列表、保存、名称冲突、提交人快照、企业处理人选项。
- frontend/src/mock/batchImportMock.js：上下游及工单导入成功/部分失败结果。
- frontend/src/mock/workorderRenewalMock.js：商业/交强双提醒与处理结果。

接口契约：

- docs/api/downstream-contact-api.md：下游字段、提交人与录入人、收款快照、企业处理人选择与接单、OCR交互。
- docs/api/merchant-batch-import-api.md：新版上下游模板、校验及导入、模板下载。
- docs/api/workorder-batch-import-api.md：93列字段及匹配、金额、事务规则和样例。
- backend/docs/api/workorder-renewal-api.md：双险周期、列表/数量、分项处理及关闭提醒。
- backend/docs/api/merchant-staff-api.md：旧接口退出说明。

## Excel 模板

outputs/20261029-system-optimization/ 中交付：上游批量导入模板（含示例版）.xlsx（7列）、下游批量导入模板（含示例版）.xlsx（9列）、工单批量导入模板（含示例版）.xlsx（93列）。均有使用说明、业务数据、字段说明，第四行为固定表头，第五行为一行虚构示例，导入前须删除或替换，程序不会自动跳过示例。工单示例采用个人旧车，不适用的组织、合格证、发票及失败原因留空。

相同文件已放入 backend/insruance/src/main/resources/import/，通过 GET /import-templates/{businessType} 鉴权下载，替代旧 OSS 模板地址。数据录入工作表使用Excel默认样式，保留默认网格和General格式，仅按列标题调整列宽以完整显示文字；使用说明及字段说明仍保持可读版式。模板已检查列数、三页预览、无公式错误及服务端发布副本一致性。

## 数据库变更与本地执行记录

服务器只需完整执行 **V20261030_01_insurance_optimization.sql** 一份增量文件。原两份写入脚本已合并并移除，不能再分别执行。V20261029_00_insurance_optimization_precheck.sql 保留为可选的提前只读检查，不是额外必执行的写入步骤。

合并文件按以下顺序完成：关键前置检查 → 增加字段及名称唯一约束 → 回填当前和归档快照 → 核验提交人、收款人快照 → 删除旧人员引用及现表/归档表 → 恢复更新时间触发器。所有结构和数据调整在同一次迁移过程调用内顺序执行，任一步报错立即退出，不继续删除。旧收款快照只补NULL。无需另设删除确认变量。

请先备份并停止旧版服务写入，选中目标数据库，用支持 MySQL DELIMITER 的客户端在同一连接执行文件，再部署匹配的新版本。脚本不能重复执行，已执行或部分执行会阻断；MySQL DDL不能整体回滚。真实服务器结构与定制触发器仍须先核对。
2026-10-30数据库MCP连接已恢复，已核对并更新本地 insurance_saas。用户明确授权删除本地无价值的阻断数据：清理 enterprise_id、category_id 同时为空的44,870条车商及42,410条区域关联后，执行合并增量。清理是本地专用步骤，没有加入服务器通用增量脚本，未操作其他数据库或服务器。

执行后核验：当前机构8条、归档机构10,072条；当前工单1条、归档工单22条、对应支付记录1条和22条，工单及支付数量与执行前一致。当前与归档表均已增加提交人和双险续保字段；唯一索引 uk_biz_merchant_active_name 已建立；旧人员/角色四表及source_staff_id、payee_staff_id引用列已删除；临时迁移过程已清除，三张业务表更新时间触发器已恢复。当前库原有已处理续保数为0，无旧状态映射冲突。

数据库工具在多语句结果格式化时返回错误，因此没有盲目重跑；已通过独立只读查询逐项确认实际事务提交及全部迁移终态。执行用SQL副本和清理脚本位于 outputs/local-db-update-20261030/，仅用于本地审计；服务器仍使用 backend/db/migration/V20261030_01_insurance_optimization.sql 原文件。

历史单一 remind_status 无法推断此前处理了商业还是交强。迁移草案保留旧字段，新双状态初始未处理；这会让部分历史已处理工单重新进入提醒。执行前需用户确认此策略，或提供逐工单分项映射后调整回填。未经确认不执行这一状态迁移。

MySQL DDL 不支持依靠事务整体回滚；必须备份。合并增量不是可重复执行脚本，失败后须先核对已执行步骤和触发器保护状态，不能直接重跑。发现无联系人、多联系人、重名或无法恢复姓名，先人工核对，不自动合并或挑选任意人员。

## 验证结果与剩余验证

- 前端：npm run lint -- --no-fix 通过；npm run build 通过；node --test frontend/tests/ocrCandidates.test.js 通过2项。
- 后端：mvn -o compile -DskipTests -q 全模块通过；选定的6组不连接数据库的回归测试通过15项，覆盖日历和闰年、实际 Mapper SQL 解析、正式下游模板、身份 AND 匹配及投保推导、单行失败后继续、处理人企业/启用状态校验。
- git diff --check 通过。前端构建仍提示浏览器兼容数据陈旧和包体积建议，无编译失败。
- 已完成本地真实数据库迁移、结构与数量核验；实际Mapper续保计数SQL在本地MySQL执行通过。未完成登录后的浏览器全流程、真实导入事务及归档任务联调，服务器历史数据仍需单独预检。
- 已实际运行 mvn -o -pl insruance -am package -DskipTests -q 并检查生成JAR，BOOT-INF/classes/import/中的三个xlsx与最新含示例模板一致；通过classpath流下载，部署JAR无需另上传OSS。

原有 outputs/architecture/ 保持不变。本次没有提交、推送或部署。


## 服务器迁移补充（2026-10-30）

服务器首次运行因缺失企业/分类的历史数据被前置检查阻断，未创建新字段，触发器已恢复。用户截图确认服务器企业和分类均为空的车商42,145条、经营区域39,832条，工单与人员引用为0，名称共3,474种。用户随后明确确认这些服务器数据无需保留。

现已将不备份清理步骤合并到 V20261030_01_insurance_optimization.sql，同一文件先清理、后继续结构迁移，不需另跑清理文件。严格核对截图数量，检查当前与归档的工单、人员、角色及区域引用；数量不符或有引用立即回滚清理。清理仅选 enterprise_id 和 category_id 同时为空的主体及其当前区域关联，其他异常仍由原前置检查阻断。

当前可用数据库工具仍连接本地localhost，未连接或执行服务器更新。服务器须在停止业务写入后重新运行此更新后的文件；需要看到 insurance_optimization_completed 结果，不能只凭执行语句总数判断成功。清理提交后续DDL仍可能因其他历史异常失败，MySQL DDL不能整体回滚。

合并后的过程定义已在本地MySQL创建成功，完成语法编译检查后即删除验证过程；未调用该验证过程，未执行服务器清理。

服务器20:50日志显示执行连接未选择数据库，除两个会话变量赋值外其余语句均因1046失败，无业务变更。增量文件现已将 USE insurance_saas; 放在首条可执行语句，避免依赖客户端默认数据库；尚未代替用户重跑服务器迁移。
