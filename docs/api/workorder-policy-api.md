# 工单报价与承保接口（两表结构，2026-11-07）

依赖迁移：`backend/db/migration/V20261107_01_merge_workorder_business_tables.sql`。
业务数据保存到 workorder 与 quote，文件沿用原工单附件接口。underwriting/payment/commission 不再作为独立表访问。承保附件可通过 `POST /ocr/policy` 识别三类保单；识别只返回待回填字段，仍由本接口保存。详见 [承保 OCR 接口](workorder-policy-ocr-api.md)。

## 公共约定

- 统一响应：`{ "code": 200, "msg": "操作成功", "data": ... }`；沿用 token 请求头和现有异常处理。
- 工单 ID、文件 ID 可用十进制字符串；时间均为 Unix 秒。保单起期和终期在前端仅按本地年月日展示、选择，接口仍提交 Unix 秒；工单管理列表的创建、跟进和承保时间展示到秒，日期与时分秒分两行。
- 金额可提交十进制字符串，最多两位小数，范围 0 至 9999999999.99；不能将空串当成零。
- 企业和操作人来自登录上下文；工单必须属于本企业并有现有工单处理权限。
- 错误沿用统一响应：400 参数/状态不合法，403 无权处理，404 无资源；校验失败不修改任何表或文件关系。

## GET /workorder/{id}

作用：查询工单详情、三类保单、金额政策、收款资料及附件；沿用现有详情访问权限。
path：id，必填，工单主键。无 body/query/file 参数。

保单字段（均可为 null，以兼容历史缺失）：

| 前缀 | 险种 | 保费字段 | 附件类型 |
| --- | --- | --- | --- |
| commercial | 商业险 | commercialAmount | acceptInsuranceCommercial |
| compulsory | 交强险 | compulsoryAmount | acceptInsuranceCompulsory |
| nonMotor | 非车险 | nonMotorAmount | acceptInsuranceOther（0—6 份） |

各前缀对应 `InsurerName:string(200)`、`PolicyNo:string(100)`、`InsuranceStartTime:long`、`InsuranceEndTime:long`。选中险种时仅起期与保费必填；终期、公司名称和保单号可空。填写终期时须晚于起期。三种起期和其他保单字段均在主表。交强险另有可选的 `vehicleAndTaxAmount:decimal`，表示代收车船税；未录入保持 null，明确免税可填 0。

`totalPremiumAmount` 为三种保费之和；`payableAmount` 为合计保费加车船税，只在响应计算，不持久化。全空显示未知；有明确零元显示 0.00。原 `requiredPayAmount` 是支付环节录入金额，保持独立语义。

其他原有工单、证件、状态、上下游费用、收款与反馈字段保留。附件列表 `workorderFileList` 项为 `{fileId,type,fileName,path,...}`；path 为现有临时下载地址。`acceptInsuranceOther` 是非车险保单附件，可有 0—6 份；历史 `acceptInsuranceNonMotor` 在详情中仍可读取，再次承保提交时统一归入 `acceptInsuranceOther`。独立的 `acceptInsuranceMisc` 是其他单证，可有 0—6 份，不做 OCR，也不依赖非车险是否选中。

## PUT /workorder/quotation

作用：保存报价单及报价反馈，沿用 `workorder:update` 权限。
body：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | long/string | 是 | 工单 ID |
| status | int | 是 | 本页面成功推进到 8；已经完成的工单保留原状态，不倒退 |
| quotationRemark | string(500) | 否 | 报价反馈，允许空串清空 |
| workorderFileList | array | 是 | 当前报价附件完整列表，最多一个，类型仅 quotation |

文件上传使用既有接口，body 只提交已上传文件的 ID。附件必须属于本企业且未被其他工单占用。
保单、保费、政策、收款等额外字段不会在此入口保存；不覆盖以前承保资料。成功返回统一响应，无分页。

示例：
```json
{"id":"101","status":8,"quotationRemark":"报价已反馈","workorderFileList":[{"fileId":"899","type":"quotation"}]}
```

## PUT /workorder/acceptInsurance

作用：完整提交承保保单和政策费用，在一个事务内保存主表、quote、附件，再完成工单。沿用 `workorder:update` 权限，已接单的状态 2—10 均可提交，可跳过报价、核保及支付直接承保；不要求报价单或先前报价记录。金额表不存在时自动补建。未接单、权限不足、资料不合法仍拒绝。

body：

- `id`：必填。
- `policyTypes:string[]`：必填，至少一类，只允许 COMMERCIAL、COMPULSORY、NON_MOTOR，不重复。列表是本次明确选择的完整险种范围。
- 对每个选中险种，仅 `InsuranceStartTime` 和对应保费字段必填。`InsuranceEndTime` 可为 null；填写时必须晚于起期。保险公司名称、保单号码、保单附件和交强险代收车船税均可不填；已填写的文本、金额和附件仍须符合格式、长度与数量限制。
- 未选中险种的保单字段、保费和对应政策费用显式清空；重新提交承保代表完整替换，不把缺失字段解释为保留。
- `workorderFileList`：必填数组（可为空），表示当前承保附件的完整列表。商业险 `acceptInsuranceCommercial` 和交强险 `acceptInsuranceCompulsory` 各可选 0—1 份；选择非车险时，`acceptInsuranceOther` 可选 0—6 份，未选择时不得提交。独立的其他单证 `acceptInsuranceMisc` 可选 0—6 份，无论是否选择非车险都可提交，上传后仅建立文件关联，不调用 OCR。历史 `acceptInsuranceNonMotor` 入参兼容并归一化，合并后的非车险附件不能超过六份。文件 ID 不能重复或引用其他企业文件。
- `payName:string(100)`、`payIdNum:string(20)`、`payBank:string(100)`、`payBankCardNum:string(100)`：原收款资料，允许空串。
- `acceptInsuranceRemark:string(500)`：承保反馈，允许空串。
- `nonMotorInsuranceName:string(100)`、`nonMotorCoverageAmount:decimal`：原非车险补充资料，选填，取消非车险时清空。
- `upstreamComputeType/downstreamComputeType:int`：0/1，沿用原计算规则。
- 每个方向分别有 Commercial、Compulsory、VehicleAndVesselTax、NonMotor 四类的 `Percentage` 与 `Amount` 字段，例如 `upstreamCommercialPercentage`、`upstreamCommercialAmount`。每个维度提交一种：金额模式清空比例，比例模式清空旧金额并重算。两者都填时拒绝；都为空表示未配置，不强制新增费用。
- 比例最多两位小数，沿用 DECIMAL(8,2) 范围。计算方式 1：基数×比例÷106；方式 0：基数×比例÷100；金额最终四舍五入到分，保留原公式。
- 客户端的 status、finishTime、合计、应付及 requiredPayAmount 不作为此入口的更新依据；首次完成时间由服务端生成，编辑已完成工单保持原完成时间。

成功返回统一响应。不存在列表/分页/排序参数，不新增导入导出或删除端点。

多文件承保请求示例（其余保单和政策字段可不填，计算方式仍须提交）：
```json
{"id":"101","policyTypes":["NON_MOTOR"],"nonMotorInsurerName":"测试保险公司","nonMotorPolicyNo":"NM-101","nonMotorInsuranceStartTime":1793980800,"nonMotorInsuranceEndTime":1825516800,"nonMotorAmount":"200.00","upstreamComputeType":0,"downstreamComputeType":0,"workorderFileList":[{"fileId":"902","type":"acceptInsuranceOther"},{"fileId":"903","type":"acceptInsuranceOther"},{"fileId":"904","type":"acceptInsuranceMisc"}]}
```
失败响应示例：`{"code":400,"msg":"保单附件数量超过限制","data":null}`（提交超出该险种附件上限）；`{"code":400,"msg":"附件数量超过限制","data":null}`（附件类别超限）。缺少起期或保费、已填终期不晚于起期也返回 400；权限不足为 403，工单不存在为 404。

## PUT /workorder/noCascade

原失败反馈与支付确认入口保留，改为写主表。仅允许对应状态的失败原因或支付确认字段；不允许经此入口绕过保单校验改写保单与政策。报价失败为 3，核保失败为 5，支付失败为 7，支付确认为 8，承保失败为 9；状态与既有流程对应。

## Mock 与联调

`frontend/src/mock/workorderPolicyMock.js` 提供可切换详情/报价/承保响应，API 仍通过 `frontend/src/api/workorder.js` 调用。开发环境设置 `VUE_APP_WORKORDER_POLICY_MOCK=true` 后重启前端；关闭即恢复真实请求。示例 ID：101 三类齐全且非车险有两份单证，102 空白待报价，103 历史已完成且含旧 `acceptInsuranceNonMotor` 附件，104 三类起期与保费齐全但终期、公司、号码、附件和车船税均为空。上传仍复用既有文件服务；不向真实数据库写入。

可直接联调的详情响应核心示例：
```json
{"code":200,"msg":"操作成功","data":{"id":"101","status":8,"commercialInsurerName":"测试保险公司","commercialPolicyNo":"MOCK-POLICY-0","commercialInsuranceStartTime":1793980800,"commercialInsuranceEndTime":1825516800,"commercialAmount":"3000.00","compulsoryAmount":"950.00","nonMotorAmount":"200.00","vehicleAndTaxAmount":"360.00","totalPremiumAmount":"4150.00","payableAmount":"4510.00","workorderFileList":[{"fileId":"900","type":"acceptInsuranceCommercial","fileName":"商业保单.pdf","path":""},{"fileId":"902","type":"acceptInsuranceOther","fileName":"非车险保单.pdf","path":""},{"fileId":"903","type":"acceptInsuranceOther","fileName":"非车险附加单证.pdf","path":""},{"fileId":"904","type":"acceptInsuranceMisc","fileName":"其他单证.pdf","path":""}]}}
```
完整三类字段及文件见 mock 中的 101；102/103 覆盖空数据及历史资料不足状态。


## 关联流程适配

- 续保：仅商业险、交强险提醒和独立跟进；非车险起期可录入、查看、导入导出。
- 基础资料：创建时三种起期留空；待承保及之后的起期通过完整保单表单录入，基础抽屉不能覆盖已确认保单、收款和政策。
- 导入：当前101列模板动态生成，兼容旧93列；新增保单字段、三种起期和非车险保单完成状态，历史无附件导入保留原能力。详见 workorder-batch-import-api.md。
- 导出：三类公司、保单号、终期及非车险起期均包含；支付历史金额仍独立保存。计算方式缺失兼容历史值。
- 统计、归档和企业保留期清理：只访问新主表及金额表；数据库迁移备份不纳入日常自动清理。
- 套餐计数/周期超额计费：使用未变的工单ID、创建时间及删除标记，计费口径保持原样。
