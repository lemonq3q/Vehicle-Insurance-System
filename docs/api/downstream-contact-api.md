# 下游联系人与工单提交人接口变更

## 工单列表身份与政策费用展示补充（2026-11-03）

现有 `GET /workorder`、`GET /workorder/renew` 以及 `GET /workorder/{id}` 的路径、鉴权、查询/分页参数、统一响应和错误码不变；列表仍为 `{code,msg,data:{total,table}}`，详情为 `{code,msg,data:Workorder}`。
处理人字段来自同一所属用户，不新增请求或数据库字段。除已有 `handleUserName`、`handleUserPhone`、`handleUser` 外，兼容新增以下只读返回字段：

| 字段 | 类型 | 可空 | 含义 |
| --- | --- | --- | --- |
| handleUserEmail | string | 是 | 处理账号绑定邮箱 |
| handleUserWechatNickname | string | 是 | 处理账号保存的微信昵称，不返回OpenID/UnionID |
| handleUserUsername | string | 是 | 处理账号登录名，所有其他身份缺失时用于回退 |

列表处理人按非空白的姓名、邮箱、微信昵称、手机号、账号依次选择，只显示一项；不再在姓名下面附加内部微信账号。旧服务未返回新增字段时兼容已有身份字段。

费用只在前端展示层处理，`upstreamSumAmount`、`downstreamSumAmount` 原接口值和数据库金额不修改：有有效 `quotationTime`，或状态为4至10时视为完成过报价，缺失合计显示0.00；否则缺失合计仍为空。已有零、精确金额字符串、非零及负金额在展示层统一按HALF_UP保留两位小数，不通过Number重新解析精确字符串；接口原始金额仍保留。报价失败且没有成功报价依据时不补零。

前端展示字段 `handleUserDisplay`、`upstreamPolicyFeeDisplay`、`downstreamPolicyFeeDisplay` 只由页面生成，不是请求或服务端响应字段。API联调样例位于 `frontend/src/mock/workorderListDisplayMock.js`：

```json
{"code":200,"msg":null,"data":{"total":1,"table":[{"id":"102","status":4,"quotationTime":1793577600,"handleUserName":"秦艺玲","handleUserEmail":null,"handleUserWechatNickname":null,"handleUserUsername":"issuer_example","handleUserPhone":null,"handleUser":"秦艺玲","upstreamSumAmount":null,"downstreamSumAmount":null}]}}
```

此样例在页面显示处理人“秦艺玲”、上下游费用均为0.00；未新增错误分支，继续沿用接口原有401/403及业务失败响应。

统一响应 `{code:number,msg:string|null,data:any}`，鉴权沿用 `token` 请求头和当前企业上下文；所有 ID 以十进制字符串传输。本文为此次版本的新契约，替代独立商户人员及嵌套 user 数据结构。

## 下游资料

| 方法与完整路径 | 场景及权限 | 请求位置 |
| --- | --- | --- |
| GET /downstream | 分页列表，merchant:select | query: pageNum=1、pageSize=20、blurParam、location、channel、businessArea |
| GET /downstream/{id} | 下游详情，merchant:select | path: id，必填 |
| GET /downstream/option | 工单选店，merchant:select | query: blurParam，非空，按名称或编码模糊匹配 |
| POST /downstream | 新增下游，merchant:update | body: 下表字段，id/code 由服务端生成 |
| PUT /downstream | 编辑下游，merchant:update | body: 下表字段及必填 id |
| DELETE /downstream?id={id} | 软删除，merchant:update | query: id，必填 |
| GET /downstream/excel | 导出同一筛选范围，merchant:select | query 同列表；返回 xlsx 文件，含联系人和手机号 |

| body/业务数据字段 | 类型 | 必填 | 语义及示例 |
| --- | --- | --- | --- |
| id | string | 编辑必填 | 下游 ID，17 |
| name | string(100) | 是 | 商家名称，去首尾空白；同企业有效下游不能重名 |
| type | string | 是 | 车商店铺 / 汽修厂 / 代理人 |
| location | string | 是 | 地区编码，440100 |
| contact | string(100) | 是 | 联系人姓名，张三，直接字段 |
| phone | string(20) | 是 | 联系电话，13800000000，11 位大陆手机号 |
| address | string(100) | 否 | 详细地址 |
| bank | string(100) | 是 | 开户银行 |
| bankCardNum | string(100) | 是 | 银行卡号，按文本传输 |
| businessArea | string[] | 否 | 经营区域编码 |
| defaultAreaCode | string | 否 | 默认区域编码 |

列表返回 `data={total:number,table:Downstream[]}`；无新增排序参数，沿用现有列表顺序。详情 `data=Downstream`，选项 `data=Downstream[]`，包含 contact、phone。新增编辑删除成功沿用 msg 业务提示字符串。废弃 `user`、`storeManager`，不再创建人员或登录账号。

## 工单提交人及默认收款资料

- `POST /workorder`、`PUT /workorder/baseInfo` body 新增 `submitterName:string(100)`（可选），取代前端把人员 ID 放入 createBy 的方式。
- `GET /workorder`、`GET /workorder/{id}`、`GET /workorder/renew` 返回同名字符串；列表及导出中的 createUser/createUserName 表示提交人快照；createUserPhone 不再通过系统账号推导。
- `createBy` 继续是系统录入人的 ID，由后端新建时写入当前登录用户；基础编辑不得修改。submitterName 未提供时，新建默认下游 contact；明确提交空字符串允许保留为空。
- 选店后前端从 `GET /downstream/{id}` 带入 contact，允许人工修改。编辑初始化不覆盖历史姓名。
- 新建时收款姓名默认 contact、手机号默认 phone，银行及卡号默认下游资料；沿用当前支付字段 payName/payIdNum（后者为历史 API 的手机号字段名）。已保存工单保持原快照，不跟随下游联系人变化。

## 错误与样例

- code=400：联系人/手机号/名称不合法、同企业同方向重名、引用了无效或非下游店铺。
- code=404：下游或工单不存在于当前授权范围。
- HTTP 401/403：未登录或没有相应权限。数据库并发重名由唯一约束兜底并转换成业务错误。

```json
{"code":200,"data":{"id":"17","name":"示例车商","type":"车商店铺","contact":"张三","phone":"13800000000","location":"440100","businessArea":[]}}
```

```json
{"createMerchantId":"17","submitterName":"李四","ownerType":0,"type":0,"ownerName":"王五","ownerPhone":"13900000000"}
```

完整联调对象见 `frontend/src/mock/downstreamContactMock.js`。不再支持 `/merchant-staff`、商户人员角色和旧商户用户选项；系统用户、企业成员及登录接口保持独立。

## OCR 交互

OCR 上传及识别 API 不变，不新增提交字段。识别响应到达时，当前场景中的空字段（null、undefined 或空白字符串）自动填入；0、false 和其他已有内容均保留，旁边仅显示识别内容及应用按钮，不带文件来源前缀。自动填入的字段不再显示候选；逐项应用后移除该字段全部候选。各识别区标题同行提供带内边距、悬停主色底白字的全部应用按钮，全局全部应用只处理当前车主/车辆类型的有效非空候选；待确认的正副页冲突时优先正页，应用后可人工编辑，重新识别可产生新候选。识别期间手工填入的内容按已有内容处理；隐藏场景的迟到响应不自动填入。日期候选转换后沿用原工单 Unix 秒提交方式。

OCR 自动填入与人工确认的联调样例位于 `frontend/src/mock/ocrRecognitionMock.js`：已有车型和零值保持不变，空车架号自动补齐。接口契约无需后端或数据库变更。

候选文本最大宽度跟随对应输入区，单行超出显示省略号，通过悬停或键盘聚焦提示查看全文；候选出现不改变身份证、营业执照原有两列布局。逐项应用与全部应用共用按钮样式，悬停为主色底白字并保持文字居中；全部应用采用更大点击区域，放在识别区标题同行。`idCardLongRecognitionMock` 可联调长文字展示，窄屏按既定断点切换为单列，与是否存在候选无关。

## 接单、派单及重新分配处理人

`GET /user/option/system`（权限 `user:select`）接收 query `blurParam:string`；空白返回空列表，非空按姓名、用户名或登录邮箱模糊查找本企业启用的系统成员，按用户 ID 升序最多 50 个。返回 `data:User[]`，选择器使用 `id`、`realName`、`username`、`email`；不再随处理机构联动商户人员列表。

`PUT /workorder/accept`（权限 `workorder:update`）接收 JSON：

| 字段 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- |
| id | string | 是 | 当前企业的工单 ID |
| handleBy | string | 是 | 当前企业启用的系统用户 ID |
| handleMerchantId | string | 否 | 当前企业有效处理机构；未提交保留原值 |
| status | integer | 否 | 沿用工单流程状态；未提交保留原状态 |

成功为 `{code:200,msg:"更新成功"}`；失效、停用或跨企业处理人/机构返回业务 code=400；工单不存在或不在当前企业返回 404。保留旧工单原处理人显示，仅在用户明确重新分配时更新。实际录入人不随处理人变化。

```json
{"code":200,"data":[{"id":"8","realName":"张三","username":"zhangsan","email":"zhangsan@example.com","idNum":null,"status":1}]}
```

联调示例见 `frontend/src/mock/downstreamContactMock.js` 中的 `handlerOptionsMock`。
