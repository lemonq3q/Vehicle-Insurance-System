# 工单管理页面历史工单批量导入

## 接口与权限

- `GET /import-templates/workorder`：下载当前版本官方xlsx，workorder:select，沿用token请求头，返回文件流。
- `POST /workorder/batch-import`：在工单管理页面新增历史工单，workorder:update，multipart/form-data，file必填，单个xlsx文件<=20MB、最多10000条非空数据。
- 不接收enterpriseId、文件/附件、用户ID或续保完成周期。企业与实际录入人来自已认证上下文。

模板包含使用说明、工单、字段说明。工单Sheet第四行为固定表头，第五行为虚构示例，正式导入前须删除或替换；示例行不会自动跳过。列名、列数和顺序必须一致。日期为真实Excel日期或yyyy-MM-dd文本；金额单位为元，最多2位小数，比例填写百分数数值（如10表示10%），非负；枚举按中文选项填写。所有身份号码、银行卡号、手机号按文本保存。公式不导入。

## 绑定与推导规则

1. 提交店铺和承保上游机构按本企业有效主体的名称精确匹配，并区分上下游方向；无匹配或多匹配报行级错误。
2. 处理人姓名、登录邮箱、证件号：已填条件全部AND匹配当前企业有效成员及有效账号，按用户ID升序取首个；三项可以选填，但至少填写一项；全部为空时该行失败。未匹配报行级错误。
3. 未填提交人时默认下游contact，用户填入时保留字符串。收款资料未填时取下游联系人、手机、开户行、卡号；手工填写值优先。
4. 商业保费>0时逐项保留企业险种默认值，默认不投保的附加险仍不投保；现有标准配置仅车损投保、三者100万。商业保费空/0时商业险均不投保。单独的交强和车船税按各自金额>0启用默认项；合并的“交强险、车船税”任一金额>0即启用默认项，均为空/0时关闭。关闭值沿用该险种配置中的不投保或不需要；增值服务保持现有默认项，非车险报价单独保存。
5. 至少填写一种起期；有商业保费必须有商业起期，有交强保费或车船税必须有交强起期。个人车主填写姓名，组织车主填写组织名称；车主手机号填写时校验。
6. 状态按已填阶段恢复：无报价待报价，报价信息进入待核保，应缴保费进入支付待确认，核保时间进入待承保，保单号或承保时间进入已完成。新工单若未到续保日，可从全部工单页查看。
7. 每行独立事务，失败行不留下聚合数据。重复上传成功行会再次新增，修正后只上传失败行；同一车辆跨年工单允许分别存在。

## 字段契约（顺序即模板列顺序）

下表为唯一版本契约；后端资源import/workorder-columns.json、模板和解析代码同步维护。date为日期转中国时区零点的Unix秒，integer为整数，decimal为金额/百分比数值，text为字符串。handler字段仅用于匹配，不持久化到工单。

| 列 | 分组 | 表头 | 字段路径 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 基础信息 | * 提交店铺 | createMerchantName | text | 是 | 100字以内 |
| 2 | 基础信息 | 提交人 | submitterName | text | 否 | 100字以内 |
| 3 | 基础信息 | * 承保上游机构 | insuranceMerchantName | text | 是 | 100字以内 |
| 4 | 基础信息 | 处理人姓名 | handlerName | text | 否 | 100字以内 |
| 5 | 基础信息 | 处理人登录邮箱 | handlerEmail | text | 否 | 100字以内 |
| 6 | 基础信息 | 处理人证件号 | handlerIdNum | text | 否 | 100字以内 |
| 7 | 基础信息 | * 车辆类别 | type | enum | 是 | 旧车 / 新车 |
| 8 | 基础信息 | * 车主类别 | ownerType | enum | 是 | 个人 / 组织 |
| 9 | 基础信息 | 业务区域编码 | areaCode | text | 否 | 100字以内 |
| 10 | 基础信息 | 商业起期 | commercialInsuranceStartTime | date | 否 | yyyy-MM-dd |
| 11 | 基础信息 | 交强起期 | compulsoryInsuranceStartTime | date | 否 | yyyy-MM-dd |
| 12 | 投保人信息 | 车主姓名 | ownerName | text | 否 | 100字以内 |
| 13 | 投保人信息 | 车主电话 | ownerPhone | text | 否 | 20字以内 |
| 14 | 投保人信息 | 车主证件号 | ownerIdNum | text | 否 | 50字以内 |
| 15 | 投保人信息 | 组织名称 | organizationName | text | 否 | 100字以内 |
| 16 | 投保人信息 | 统一社会信用代码 | socialCreditCode | text | 否 | 100字以内 |
| 17 | 投保人信息 | 工单备注 | remark | text | 否 | 500字以内 |
| 18 | 合格证 | 合格证厂牌型号 | vehicleCertificate.brandModel | text | 否 | 95字以内 |
| 19 | 合格证 | 合格证车辆类型 | vehicleCertificate.vehicleType | text | 否 | 95字以内 |
| 20 | 合格证 | 合格证车架号 | vehicleCertificate.vehicleCode | text | 否 | 95字以内 |
| 21 | 合格证 | 合格证发动机号 | vehicleCertificate.engineCode | text | 否 | 95字以内 |
| 22 | 合格证 | 合格证核定载客 | vehicleCertificate.seats | integer | 否 | 非负整数 |
| 23 | 合格证 | 合格证整备质量 | vehicleCertificate.curbWeight | integer | 否 | 非负整数 |
| 24 | 合格证 | 合格证排量 | vehicleCertificate.displacement | integer | 否 | 非负整数 |
| 25 | 合格证 | 合格证核定载质量 | vehicleCertificate.approvedLoadCapacity | integer | 否 | 非负整数 |
| 26 | 发票 | 发票发票金额 | vehicleInvoice.invoiceAmount | decimal | 否 | 非负，最多2位小数 |
| 27 | 发票 | 发票购方名称 | vehicleInvoice.buyerName | text | 否 | 95字以内 |
| 28 | 发票 | 发票购方证件号 | vehicleInvoice.buyerIdNum | text | 否 | 95字以内 |
| 29 | 发票 | 发票车辆类型 | vehicleInvoice.vehicleType | text | 否 | 95字以内 |
| 30 | 发票 | 发票厂牌型号 | vehicleInvoice.brandModel | text | 否 | 95字以内 |
| 31 | 发票 | 发票车架号 | vehicleInvoice.vehicleCode | text | 否 | 95字以内 |
| 32 | 发票 | 发票发动机号 | vehicleInvoice.engineCode | text | 否 | 95字以内 |
| 33 | 发票 | 发票核定载客 | vehicleInvoice.seats | integer | 否 | 非负整数 |
| 34 | 发票 | 发票核定载质量 | vehicleInvoice.approvedLoadCapacity | integer | 否 | 非负整数 |
| 35 | 行驶证 | 行驶证车牌号 | vehicleLicense.licensePlate | text | 否 | 45字以内 |
| 36 | 行驶证 | 行驶证车辆类型 | vehicleLicense.vehicleType | text | 否 | 95字以内 |
| 37 | 行驶证 | 行驶证所有人 | vehicleLicense.ownerName | text | 否 | 95字以内 |
| 38 | 行驶证 | 行驶证使用性质 | vehicleLicense.usageNature | text | 否 | 95字以内 |
| 39 | 行驶证 | 行驶证厂牌型号 | vehicleLicense.brandModel | text | 否 | 95字以内 |
| 40 | 行驶证 | 行驶证车架号 | vehicleLicense.vehicleCode | text | 否 | 95字以内 |
| 41 | 行驶证 | 行驶证发动机号 | vehicleLicense.engineCode | text | 否 | 95字以内 |
| 42 | 行驶证 | 行驶证注册日期 | vehicleLicense.registrationDate | date | 否 | yyyy-MM-dd |
| 43 | 行驶证 | 行驶证发证日期 | vehicleLicense.issueDate | date | 否 | yyyy-MM-dd |
| 44 | 行驶证 | 行驶证核定载客 | vehicleLicense.seats | integer | 否 | 非负整数 |
| 45 | 行驶证 | 行驶证核定载质量 | vehicleLicense.approvedLoadCapacity | integer | 否 | 非负整数 |
| 46 | 行驶证 | 行驶证整备质量 | vehicleLicense.curbWeight | integer | 否 | 非负整数 |
| 47 | 行驶证 | 行驶证是否过户 | vehicleLicense.isTransfer | integer | 否 | 非负整数 |
| 48 | 行驶证 | 行驶证过户日期 | vehicleLicense.transferDate | date | 否 | yyyy-MM-dd |
| 49 | 报价信息 | 报价单号 | quotationNo | text | 否 | 100字以内 |
| 50 | 报价信息 | 商业保费 | commercialAmount | decimal | 否 | 非负，最多2位小数 |
| 51 | 报价信息 | 交强保费 | compulsoryAmount | decimal | 否 | 非负，最多2位小数 |
| 52 | 报价信息 | 车船税 | vehicleAndTaxAmount | decimal | 否 | 非负，最多2位小数 |
| 53 | 报价信息 | 非车保费 | nonMotorAmount | decimal | 否 | 非负，最多2位小数 |
| 54 | 报价信息 | 非车险名称 | nonMotorInsuranceName | text | 否 | 100字以内 |
| 55 | 报价信息 | 非车保障金额 | nonMotorCoverageAmount | decimal | 否 | 非负，最多2位小数 |
| 56 | 报价信息 | 上游计费方式 | upstreamComputeType | enum | 否 | 含税 / 去税 |
| 57 | 报价信息 | 上游商业比例 | upstreamCommercialPercentage | decimal | 否 | 非负，最多2位小数 |
| 58 | 报价信息 | 上游交强比例 | upstreamCompulsoryPercentage | decimal | 否 | 非负，最多2位小数 |
| 59 | 报价信息 | 上游车船税比例 | upstreamVehicleAndVesselTaxPercentage | decimal | 否 | 非负，最多2位小数 |
| 60 | 报价信息 | 上游非车比例 | upstreamNonMotorPercentage | decimal | 否 | 非负，最多2位小数 |
| 61 | 报价信息 | 上游商业费用 | upstreamCommercialAmount | decimal | 否 | 非负，最多2位小数 |
| 62 | 报价信息 | 上游交强费用 | upstreamCompulsoryAmount | decimal | 否 | 非负，最多2位小数 |
| 63 | 报价信息 | 上游车船税费用 | upstreamVehicleAndVesselTaxAmount | decimal | 否 | 非负，最多2位小数 |
| 64 | 报价信息 | 上游非车费用 | upstreamNonMotorAmount | decimal | 否 | 非负，最多2位小数 |
| 65 | 报价信息 | 下游计费方式 | downstreamComputeType | enum | 否 | 含税 / 去税 |
| 66 | 报价信息 | 下游商业比例 | downstreamCommercialPercentage | decimal | 否 | 非负，最多2位小数 |
| 67 | 报价信息 | 下游交强比例 | downstreamCompulsoryPercentage | decimal | 否 | 非负，最多2位小数 |
| 68 | 报价信息 | 下游车船税比例 | downstreamVehicleAndVesselTaxPercentage | decimal | 否 | 非负，最多2位小数 |
| 69 | 报价信息 | 下游非车比例 | downstreamNonMotorPercentage | decimal | 否 | 非负，最多2位小数 |
| 70 | 报价信息 | 下游商业费用 | downstreamCommercialAmount | decimal | 否 | 非负，最多2位小数 |
| 71 | 报价信息 | 下游交强费用 | downstreamCompulsoryAmount | decimal | 否 | 非负，最多2位小数 |
| 72 | 报价信息 | 下游车船税费用 | downstreamVehicleAndVesselTaxAmount | decimal | 否 | 非负，最多2位小数 |
| 73 | 报价信息 | 下游非车费用 | downstreamNonMotorAmount | decimal | 否 | 非负，最多2位小数 |
| 74 | 报价信息 | 报价备注 | quotationRemark | text | 否 | 500字以内 |
| 75 | 报价信息 | 报价失败原因 | quotationFailedRemark | text | 否 | 500字以内 |
| 76 | 报价信息 | 报价时间 | quotationTime | date | 否 | yyyy-MM-dd |
| 77 | 支付信息 | 应缴保费 | requiredPayAmount | decimal | 否 | 非负，最多2位小数 |
| 78 | 支付信息 | 收款人姓名 | payName | text | 否 | 100字以内 |
| 79 | 支付信息 | 收款人手机 | payIdNum | text | 否 | 100字以内 |
| 80 | 支付信息 | 收款开户行 | payBank | text | 否 | 100字以内 |
| 81 | 支付信息 | 收款银行卡号 | payBankCardNum | text | 否 | 100字以内 |
| 82 | 支付信息 | 支付备注 | payRemark | text | 否 | 500字以内 |
| 83 | 支付信息 | 支付失败原因 | payFailedRemark | text | 否 | 500字以内 |
| 84 | 承保信息 | 核保备注 | underwritingRemark | text | 否 | 500字以内 |
| 85 | 承保信息 | 核保失败原因 | underwritingFailedRemark | text | 否 | 500字以内 |
| 86 | 承保信息 | 核保时间 | underwritingTime | date | 否 | yyyy-MM-dd |
| 87 | 承保信息 | 商业保单号 | commercialPolicyNo | text | 否 | 100字以内 |
| 88 | 承保信息 | 交强保单号 | compulsoryPolicyNo | text | 否 | 100字以内 |
| 89 | 承保信息 | 物流单号 | trackingNum | text | 否 | 100字以内 |
| 90 | 承保信息 | 物流公司 | logisticsCompany | text | 否 | 100字以内 |
| 91 | 承保信息 | 承保备注 | acceptInsuranceRemark | text | 否 | 500字以内 |
| 92 | 承保信息 | 承保失败原因 | acceptInsuranceFailedRemark | text | 否 | 500字以内 |
| 93 | 承保信息 | 承保时间 | finishTime | date | 否 | yyyy-MM-dd |

## 响应与错误

沿用ResponseResult：`{code:number,msg:string|null,data:any}`。成功解析文件后返回code=200；data.totalRows、successRows、failureRows均为整数，failures为失败行数组，包含sheetName、rowNumber（Excel从1开始）、rowData（表头到原始文字的映射）及reason。非分页，无排序参数；失败行按文件顺序返回。

```json
{"code":200,"msg":"导入完成","data":{"totalRows":2,"successRows":1,"failureRows":1,"failures":[{"sheetName":"工单","rowNumber":6,"rowData":{"提交店铺":"甲店","处理人姓名":"赵六"},"reason":"本企业没有同时满足所填姓名、登录邮箱及证件号的有效处理人"}]}}
```

- code400：文件格式、大小、数量或固定表头错误，不写入任何行。
- 行级失败：字段错误、机构/处理人未匹配、险种配置不可用、保存失败。其余有效行继续保存。
- HTTP401/403或项目统一鉴权业务码：未登录、无权限或无企业关联。
- 模板下载资源缺失属于部署错误，按统一异常响应处理。

Mock扩展位置：frontend/src/mock/batchImportMock.js。可填样例：甲店、提交人李四、保险公司名称、处理人姓名赵六/登录邮箱handler@example.com/证件号ABC123、旧车、个人、车主张三、商业起期2026-10-29、商业保费1000.00，其余允许空的字段留空。

单次最多10,000条非空业务数据行，超出上限整体拒绝且不写入；示例行若未删除也计入数据行。文件大小仍为20MiB（20×1024×1024字节）。

工单与上下游共用失败明细分页：前端默认每页20条，可切换50或100条并跳转页码，只渲染当前页。接口仍完整返回`data.failures`，无新增分页参数，翻页不重复导入。新结果或切换页大小回到第一页；重开弹窗恢复默认页大小。101条失败的联调样例见`frontend/src/mock/batchImportMock.js`中的`batchImportPaginatedResultMock`。
