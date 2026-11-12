# 工单管理页面历史工单批量导入

## 接口与权限

- `GET /import-templates/workorder`：下载当前版本动态生成的官方xlsx，workorder:select，沿用token请求头，返回文件流。
- `POST /workorder/batch-import`：在工单管理页面新增历史工单，workorder:update，multipart/form-data，file必填，单个xlsx文件<=20MB、最多10000条非空数据。
- 不接收enterpriseId、文件/附件、用户ID或续保完成周期。企业与实际录入人来自已认证上下文。

模板包含使用说明、工单、字段说明。工单Sheet第二行是填写说明，第三行仅保留一组字段分组标题，第四行为固定表头，第五行的101列均有虚构示例。正式导入前须删除或替换示例行，并删除实际业务不适用的示例值；示例行不会自动跳过。新版101列按页面的基础资料、车辆证件、报价、收款、保单、政策费用和承保反馈顺序排列；导入端仍兼容上一版101列与原93列文件。日期为真实Excel日期或yyyy-MM-dd文本；金额单位为元，最多2位小数，比例填写百分数数值（如10表示10%），非负；枚举按中文选项填写。所有身份号码、银行卡号、手机号按文本保存。公式不导入。

## 绑定与推导规则

1. 提交店铺和承保上游机构按本企业有效主体的名称精确匹配，并区分上下游方向；无匹配或多匹配报行级错误。
2. 处理人姓名、登录邮箱、证件号：已填条件全部AND匹配当前企业有效成员及有效账号，按用户ID升序取首个；三项可以选填，但至少填写一项；全部为空时该行失败。未匹配报行级错误。
3. 未填提交人时默认下游contact，用户填入时保留字符串。收款资料未填时取下游联系人、手机、开户行、卡号；手工填写值优先。
4. 商业保费>0时逐项保留企业险种默认值，默认不投保的附加险仍不投保；现有标准配置仅车损投保、三者100万。商业保费空/0时商业险均不投保。单独的交强和车船税按各自金额>0启用默认项；合并的“交强险、车船税”任一金额>0即启用默认项，均为空/0时关闭。关闭值沿用该险种配置中的不投保或不需要；增值服务保持现有默认项，非车险保费单独保存到金额表。
5. 至少填写商业、交强或非车险中的一种起期；有商业保费必须有商业起期，有交强保费或车船税必须有交强起期，有非车险保费必须有非车险起期。填写终期时必须晚于对应起期。非车险仅记录起期，不纳入自动续保提醒。个人车主填写姓名，组织车主填写组织名称；车主手机号填写时校验。
6. 状态按已填阶段恢复：无报价待报价，报价信息进入待核保，应缴保费进入支付待确认，核保时间进入待承保，任一种保单号或承保时间进入已完成。新工单若未到续保日，可从全部工单页查看。
7. 每行独立事务，失败行不留下聚合数据。重复上传成功行会再次新增，修正后只上传失败行；同一车辆跨年工单允许分别存在。

## 字段契约（顺序即模板列顺序）

下表为新版下载模板的列顺序；旧顺序保存在import/workorder-columns-legacy.json，仅用于识别历史文件。后端资源import/workorder-columns.json、模板和解析代码同步维护。date为日期转中国时区零点的Unix秒，integer为整数，decimal为金额/百分比数值，text为字符串。handler字段仅用于匹配，不持久化到工单。

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
| 10 | 投保人信息 | 车主姓名 | ownerName | text | 否 | 100字以内 |
| 11 | 投保人信息 | 车主电话 | ownerPhone | text | 否 | 20字以内 |
| 12 | 投保人信息 | 车主证件号 | ownerIdNum | text | 否 | 50字以内 |
| 13 | 投保人信息 | 组织名称 | organizationName | text | 否 | 100字以内 |
| 14 | 投保人信息 | 统一社会信用代码 | socialCreditCode | text | 否 | 100字以内 |
| 15 | 投保人信息 | 工单备注 | remark | text | 否 | 500字以内 |
| 16 | 行驶证 | 行驶证车牌号 | vehicleLicense.licensePlate | text | 否 | 45字以内 |
| 17 | 行驶证 | 行驶证车辆类型 | vehicleLicense.vehicleType | text | 否 | 95字以内 |
| 18 | 行驶证 | 行驶证所有人 | vehicleLicense.ownerName | text | 否 | 95字以内 |
| 19 | 行驶证 | 行驶证使用性质 | vehicleLicense.usageNature | text | 否 | 95字以内 |
| 20 | 行驶证 | 行驶证厂牌型号 | vehicleLicense.brandModel | text | 否 | 95字以内 |
| 21 | 行驶证 | 行驶证车架号 | vehicleLicense.vehicleCode | text | 否 | 95字以内 |
| 22 | 行驶证 | 行驶证发动机号 | vehicleLicense.engineCode | text | 否 | 95字以内 |
| 23 | 行驶证 | 行驶证注册日期 | vehicleLicense.registrationDate | date | 否 | yyyy-MM-dd |
| 24 | 行驶证 | 行驶证发证日期 | vehicleLicense.issueDate | date | 否 | yyyy-MM-dd |
| 25 | 行驶证 | 行驶证核定载客 | vehicleLicense.seats | integer | 否 | 非负整数 |
| 26 | 行驶证 | 行驶证核定载质量 | vehicleLicense.approvedLoadCapacity | integer | 否 | 非负整数 |
| 27 | 行驶证 | 行驶证整备质量 | vehicleLicense.curbWeight | integer | 否 | 非负整数 |
| 28 | 行驶证 | 行驶证是否过户 | vehicleLicense.isTransfer | integer | 否 | 非负整数 |
| 29 | 行驶证 | 行驶证过户日期 | vehicleLicense.transferDate | date | 否 | yyyy-MM-dd |
| 30 | 合格证 | 合格证厂牌型号 | vehicleCertificate.brandModel | text | 否 | 95字以内 |
| 31 | 合格证 | 合格证车辆类型 | vehicleCertificate.vehicleType | text | 否 | 95字以内 |
| 32 | 合格证 | 合格证车架号 | vehicleCertificate.vehicleCode | text | 否 | 95字以内 |
| 33 | 合格证 | 合格证发动机号 | vehicleCertificate.engineCode | text | 否 | 95字以内 |
| 34 | 合格证 | 合格证核定载客 | vehicleCertificate.seats | integer | 否 | 非负整数 |
| 35 | 合格证 | 合格证整备质量 | vehicleCertificate.curbWeight | integer | 否 | 非负整数 |
| 36 | 合格证 | 合格证排量 | vehicleCertificate.displacement | integer | 否 | 非负整数 |
| 37 | 合格证 | 合格证核定载质量 | vehicleCertificate.approvedLoadCapacity | integer | 否 | 非负整数 |
| 38 | 购车发票 | 发票发票金额 | vehicleInvoice.invoiceAmount | decimal | 否 | 非负，最多2位小数 |
| 39 | 购车发票 | 发票购方名称 | vehicleInvoice.buyerName | text | 否 | 95字以内 |
| 40 | 购车发票 | 发票购方证件号 | vehicleInvoice.buyerIdNum | text | 否 | 95字以内 |
| 41 | 购车发票 | 发票车辆类型 | vehicleInvoice.vehicleType | text | 否 | 95字以内 |
| 42 | 购车发票 | 发票厂牌型号 | vehicleInvoice.brandModel | text | 否 | 95字以内 |
| 43 | 购车发票 | 发票车架号 | vehicleInvoice.vehicleCode | text | 否 | 95字以内 |
| 44 | 购车发票 | 发票发动机号 | vehicleInvoice.engineCode | text | 否 | 95字以内 |
| 45 | 购车发票 | 发票核定载客 | vehicleInvoice.seats | integer | 否 | 非负整数 |
| 46 | 购车发票 | 发票核定载质量 | vehicleInvoice.approvedLoadCapacity | integer | 否 | 非负整数 |
| 47 | 报价结果 | 报价单号 | quotationNo | text | 否 | 100字以内 |
| 48 | 报价结果 | 报价备注 | quotationRemark | text | 否 | 500字以内 |
| 49 | 报价结果 | 报价失败原因 | quotationFailedRemark | text | 否 | 500字以内 |
| 50 | 报价结果 | 报价时间 | quotationTime | date | 否 | yyyy-MM-dd |
| 51 | 收款信息 | 应缴保费 | requiredPayAmount | decimal | 否 | 非负，最多2位小数 |
| 52 | 收款信息 | 收款人姓名 | payName | text | 否 | 100字以内 |
| 53 | 收款信息 | 收款人手机 | payIdNum | text | 否 | 20字以内 |
| 54 | 收款信息 | 收款开户行 | payBank | text | 否 | 100字以内 |
| 55 | 收款信息 | 收款银行卡号 | payBankCardNum | text | 否 | 100字以内 |
| 56 | 收款信息 | 支付备注 | payRemark | text | 否 | 500字以内 |
| 57 | 收款信息 | 支付失败原因 | payFailedRemark | text | 否 | 500字以内 |
| 58 | 商业保单 | 商业起期 | commercialInsuranceStartTime | date | 否 | yyyy-MM-dd |
| 59 | 商业保单 | 商业保险公司 | commercialInsurerName | text | 否 | 200字以内 |
| 60 | 商业保单 | 商业终期 | commercialInsuranceEndTime | date | 否 | yyyy-MM-dd |
| 61 | 商业保单 | 商业保单号 | commercialPolicyNo | text | 否 | 100字以内 |
| 62 | 商业保单 | 商业保费 | commercialAmount | decimal | 否 | 非负，最多2位小数 |
| 63 | 交强保单 | 交强起期 | compulsoryInsuranceStartTime | date | 否 | yyyy-MM-dd |
| 64 | 交强保单 | 交强保险公司 | compulsoryInsurerName | text | 否 | 200字以内 |
| 65 | 交强保单 | 交强终期 | compulsoryInsuranceEndTime | date | 否 | yyyy-MM-dd |
| 66 | 交强保单 | 交强保单号 | compulsoryPolicyNo | text | 否 | 100字以内 |
| 67 | 交强保单 | 交强保费 | compulsoryAmount | decimal | 否 | 非负，最多2位小数 |
| 68 | 交强保单 | 车船税 | vehicleAndTaxAmount | decimal | 否 | 非负，最多2位小数 |
| 69 | 非车险保单 | 非车险起期 | nonMotorInsuranceStartTime | date | 否 | yyyy-MM-dd |
| 70 | 非车险保单 | 非车险保险公司 | nonMotorInsurerName | text | 否 | 200字以内 |
| 71 | 非车险保单 | 非车险终期 | nonMotorInsuranceEndTime | date | 否 | yyyy-MM-dd |
| 72 | 非车险保单 | 非车险保单号 | nonMotorPolicyNo | text | 否 | 100字以内 |
| 73 | 非车险保单 | 非车保费 | nonMotorAmount | decimal | 否 | 非负，最多2位小数 |
| 74 | 非车险保单 | 非车险名称 | nonMotorInsuranceName | text | 否 | 100字以内 |
| 75 | 非车险保单 | 非车保障金额 | nonMotorCoverageAmount | decimal | 否 | 非负，最多2位小数 |
| 76 | 上游政策费用 | 上游计费方式 | upstreamComputeType | enum | 否 | 含税 / 去税 |
| 77 | 上游政策费用 | 上游商业比例 | upstreamCommercialPercentage | decimal | 否 | 非负，最多2位小数 |
| 78 | 上游政策费用 | 上游交强比例 | upstreamCompulsoryPercentage | decimal | 否 | 非负，最多2位小数 |
| 79 | 上游政策费用 | 上游车船税比例 | upstreamVehicleAndVesselTaxPercentage | decimal | 否 | 非负，最多2位小数 |
| 80 | 上游政策费用 | 上游非车比例 | upstreamNonMotorPercentage | decimal | 否 | 非负，最多2位小数 |
| 81 | 上游政策费用 | 上游商业费用 | upstreamCommercialAmount | decimal | 否 | 非负，最多2位小数 |
| 82 | 上游政策费用 | 上游交强费用 | upstreamCompulsoryAmount | decimal | 否 | 非负，最多2位小数 |
| 83 | 上游政策费用 | 上游车船税费用 | upstreamVehicleAndVesselTaxAmount | decimal | 否 | 非负，最多2位小数 |
| 84 | 上游政策费用 | 上游非车费用 | upstreamNonMotorAmount | decimal | 否 | 非负，最多2位小数 |
| 85 | 下游政策费用 | 下游计费方式 | downstreamComputeType | enum | 否 | 含税 / 去税 |
| 86 | 下游政策费用 | 下游商业比例 | downstreamCommercialPercentage | decimal | 否 | 非负，最多2位小数 |
| 87 | 下游政策费用 | 下游交强比例 | downstreamCompulsoryPercentage | decimal | 否 | 非负，最多2位小数 |
| 88 | 下游政策费用 | 下游车船税比例 | downstreamVehicleAndVesselTaxPercentage | decimal | 否 | 非负，最多2位小数 |
| 89 | 下游政策费用 | 下游非车比例 | downstreamNonMotorPercentage | decimal | 否 | 非负，最多2位小数 |
| 90 | 下游政策费用 | 下游商业费用 | downstreamCommercialAmount | decimal | 否 | 非负，最多2位小数 |
| 91 | 下游政策费用 | 下游交强费用 | downstreamCompulsoryAmount | decimal | 否 | 非负，最多2位小数 |
| 92 | 下游政策费用 | 下游车船税费用 | downstreamVehicleAndVesselTaxAmount | decimal | 否 | 非负，最多2位小数 |
| 93 | 下游政策费用 | 下游非车费用 | downstreamNonMotorAmount | decimal | 否 | 非负，最多2位小数 |
| 94 | 承保反馈与物流 | 核保备注 | underwritingRemark | text | 否 | 500字以内 |
| 95 | 承保反馈与物流 | 核保失败原因 | underwritingFailedRemark | text | 否 | 500字以内 |
| 96 | 承保反馈与物流 | 核保时间 | underwritingTime | date | 否 | yyyy-MM-dd |
| 97 | 承保反馈与物流 | 物流单号 | trackingNum | text | 否 | 100字以内 |
| 98 | 承保反馈与物流 | 物流公司 | logisticsCompany | text | 否 | 100字以内 |
| 99 | 承保反馈与物流 | 承保备注 | acceptInsuranceRemark | text | 否 | 500字以内 |
| 100 | 承保反馈与物流 | 承保失败原因 | acceptInsuranceFailedRemark | text | 否 | 500字以内 |
| 101 | 承保反馈与物流 | 承保时间 | finishTime | date | 否 | yyyy-MM-dd |

本版三类起期、保单号码、保险公司、终期及支付承保反馈直接写主表，保费和上下游政策写 quote。模板从相同 JSON 契约生成，不再直接下发旧静态93列文件；已有93列文件仍支持上传。历史导入允许无附件和缺失新保单字段，不冒充 OCR 已识别资料。
