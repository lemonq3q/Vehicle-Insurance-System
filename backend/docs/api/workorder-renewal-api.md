# 工单续保接口

## 续保列表

- 方法与路径：`GET /workorder/renew`
- 权限：`workorder:select`
- 作用：查询当前处于年度续保窗口且未永久关闭提醒的工单。
- 周期规则：以工单 `created_at` 为周年基准；从第一个周年日起，每个周年日前配置的提醒天数至周年日（含首尾）进入续保窗口。提醒天数统一由后端 `RENEWAL_REMIND_DAYS` 控制，当前测试值为 365 天。
- 查询参数：沿用工单续保页面现有参数，包括 `blurParam`、`createMerchantId`、`handleUserId`、`areaCode`、`insuranceId`、`remindStatus`、`pageNum`、`pageSize`。
- `remindStatus`：`0` 未处理、`1` 已续保、`2` 已流失。状态只用于展示和筛选，不影响工单进入后续年度续保窗口。
- 用户设置状态时，后端同时记录 `renewal_status_cycle`。每日凌晨 4 点任务会将已经结束周期的 `remind_status` 自动重置为 `0`，续保备注保留。
- 返回：统一 `ResponseResult`，`data.total` 为总数，`data.table` 为工单列表。

## 续保提醒数量

- 方法与路径：`GET /workorder/renew/count`
- 权限：`workorder:select`
- 作用：统计当前年度续保窗口内、未永久关闭提醒的工单；统计不排除已续保或已流失状态。
- 返回示例：`{"code":200,"data":{"selfCount":2,"allCount":5}}`。非管理员不返回 `allCount`。

## 永久关闭续保提醒

- 方法与路径：`PUT /workorder/renew/{id}/disable-reminder`
- 权限：`workorder:update`
- Path 参数：`id`，工单 ID，必填。
- Body：无。
- 作用：将工单 `renewal_reminder_disabled` 设置为 `1`。关闭后该工单不再进入任何后续年度续保窗口。
- 成功：`{"code":200,"data":"已关闭续保提醒"}`。
- 失败：工单不存在或当前用户无权操作时返回 `404`。
