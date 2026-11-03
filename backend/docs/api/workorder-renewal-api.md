# 工单双险种续保 API

统一响应 `{code:number,msg:string|null,data:any}`；鉴权沿用 token 请求头、当前企业范围及现有 workorder 权限。日期按 Asia/Shanghai 日历计算，无起期不按创建日猜测。

## GET /workorder/renew

权限 workorder:select。query 沿用 blurParam、createMerchantId、handleUserId、areaCode、insuranceId、pageNum（默认1）、pageSize（默认20）；remindStatus 可选：0未处理、1已续保、2已流失，不传表示全部。非管理员仅查询本人录入工单。

响应 data={total:number,table:Workorder[]}。每工单一行，排序为创建时间升序及 ID 升序；列表保留工单原聚合字段，新增：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| commercialReminder | boolean | 商业险已进入本周期且未处理 |
| compulsoryReminder | boolean | 交强险已进入本周期且未处理 |
| commercialActiveCycle | number | 商业当前已开始周期，首轮前为0 |
| compulsoryActiveCycle | number | 交强当前已开始周期，首轮前为0 |
| commercialRenewalStatus | number | 当前周期商业状态，0未处理、1已续保、2已流失 |
| compulsoryRenewalStatus | number | 当前周期交强状态，定义同上 |
| commercialRenewalCycle | number | 商业最近处理周期，持久化值 |
| compulsoryRenewalCycle | number | 交强最近处理周期，持久化值 |

第 n 周期提醒日为原始起期直接平移 12*n-1 个日历月，n>=1，只在最终目标月截断不存在的日期。到提醒日起持续待办，进入下一年度提醒日后按新周期判断。列表按状态筛选，任一险种满足即返回；提醒数量只统计未处理且未关闭的工单，一张工单双提醒仍计1。

## GET /workorder/renew/count

权限 workorder:select，无 body。返回 `{code:200,data:{selfCount:1,allCount:3}}`，非管理员省略 allCount；计数和列表未处理筛选使用同一规则。

## PUT /workorder/renew/{id}/follow-up

权限 workorder:update；path id 为必填工单 ID。服务端校验当前企业，普通用户只能处理自己录入工单。

| body 字段 | 类型 | 必填 | 示例/约束 |
| --- | --- | --- | --- |
| scope | string | 是 | COMMERCIAL / COMPULSORY / ALL |
| status | number | 是 | 1 已续保、2 已流失 |
| followUpRes | string(1000) | 否 | 电话确认续保 |

单项处理需该险种已进入提醒周期；ALL 可提前处理另一种尚未提醒的本轮续保（以最近尚未过去的周年确定未来周期），不会永久关闭后续年度。服务端计算完成周期，不接受客户端指定周期。只更新选择范围内状态，防止并发处理另一险种时互相覆盖。

成功 `{code:200,msg:"已保存续保跟进"}`；400：非法范围、结果、备注过长或所选险种没有起期/未到提醒；404：工单不存在或无权操作；HTTP401/403：鉴权失败。

## PUT /workorder/renew/{id}/disable-reminder

权限 workorder:update，path id 必填，无 body。前端必须警告确认此操作将关闭工单双险种未来所有年度提醒。成功 `{code:200,msg:"已关闭续保提醒"}`，404 表示不存在或无权操作。

## 示例

```json
{"code":200,"data":{"total":1,"table":[{"id":"21","code":"WO21","commercialReminder":true,"compulsoryReminder":false,"commercialActiveCycle":1,"compulsoryActiveCycle":0,"commercialRenewalStatus":0,"compulsoryRenewalStatus":0}]}}
```

```json
{"scope":"ALL","status":1,"followUpRes":"已办理本轮商业和交强续保"}
```

Mock：frontend/src/mock/workorderRenewalMock.js。原 remind_status/renewal_status_cycle 仅供迁移核对，不再作为新提醒依据；旧 /noCascade 不再接受单状态续保处理，客户端应切换到本契约。

日历平移只在最终月份截断：2024-02-29 起期的首轮提醒是 2025-01-29，2026-03-31 起期的首轮提醒是 2027-02-28。每轮均从原起期计算，避免月末累计偏移。
