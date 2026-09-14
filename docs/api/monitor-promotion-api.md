# 监控平台销售推广 API

## 统一约定

- 基础路径：`/monitor`
- 鉴权头：`Authorization: Bearer <监控平台令牌>`
- 统一响应：`{ "code": 200, "msg": "...", "data": ... }`
- Excel 仅支持 `.xlsx`，文件字段名固定为 `file`，工作表固定为“推广信息”。
- 目标列表只返回 `deleted=0` 数据；推广候选额外限制 `status=1` 且所选渠道联系方式非空。
- 单次推广上限由 `monitor.promotion.max-targets` 配置，默认 `6000`。
- 电话和邮件渠道当前均为 mock 实现，不会调用真实供应商。

## 推广目标分页查询

`GET /monitor/promotion-targets`

用于信息录入列表、信息推广的系统目标选择器和按条件全选。

Query 参数：

| 字段 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `keyword` | string | 否 | 名称、电话或邮箱包含匹配，最长 100 字符 | `杭州` |
| `sourceType` | string | 否 | `MANUAL`、`EXCEL_IMPORT` | `MANUAL` |
| `status` | integer | 否 | `1` 可推广，`0` 停用 | `1` |
| `channel` | string | 否 | 推广页使用；`PHONE` 或 `EMAIL`，传入后只返回该渠道联系方式非空的数据 | `PHONE` |
| `pageNo` | integer | 否 | 页码，默认 1 | `1` |
| `pageSize` | integer | 否 | 每页数量 1–100，默认 10；推广页全选通过连续分页累计目标 | `10` |

响应业务数据：

```json
{
  "list": [
    {
      "id": 18,
      "name": "王海峰",
      "phone": "13800138001",
      "email": "wanghf@example.com",
      "companyName": "杭州安途汽车服务有限公司",
      "position": "总经理",
      "sourceType": "MANUAL",
      "importBatchNo": null,
      "status": 1,
      "remark": "华东区域意向客户",
      "createdAt": "2026-08-27T10:20:00",
      "updatedAt": "2026-08-27T10:20:00"
    }
  ],
  "pageNo": 1,
  "pageSize": 10,
  "total": 1
}
```

## 下载导入模板

`GET /monitor/promotion-targets/template`

返回 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` 文件。固定表头为：名称、电话、邮箱、备注。名称必填，电话和邮箱至少填写一项。模板所有列的默认单元格格式均为文本，避免电话变为数值或邮箱变为超链接。公司和职位字段仍保留在后端数据结构中，但不再通过信息录入页面或 Excel 模板编辑。

## 新增推广目标

`POST /monitor/promotion-targets`

Body：

```json
{
  "name": "王海峰",
  "phone": "13800138001",
  "email": "wanghf@example.com",
  "companyName": "杭州安途汽车服务有限公司",
  "position": "总经理",
  "status": 1,
  "remark": "华东区域意向客户"
}
```

字段约束：名称最长 100 字符；电话最长 32 字符；邮箱最长 254 字符；公司最长 150 字符；职位最长 100 字符；备注最长 500 字符。成功返回创建后的完整目标。

## 修改推广目标

`PUT /monitor/promotion-targets/{id}`

Body 与新增接口一致。成功返回数据库最终值；不存在或已删除返回 404。

## 删除推广目标

`DELETE /monitor/promotion-targets/{id}`

无请求体。执行软删除，不要求填写删除原因，也不写入 `monitor_system_log`。目标不存在或已删除时返回 404。

## 批量导入推广目标

`POST /monitor/promotion-targets/import`

Content-Type：`multipart/form-data`。有效行写入 `monitor_promotion_target`，来源为 `EXCEL_IMPORT`，同一文件使用相同 `importBatchNo`。单行失败不影响其他有效行，接口级异常会回滚整批数据库写入。

```json
{
  "batchNo": "PT202608271020301A2B3C",
  "totalRows": 3,
  "successRows": 2,
  "failureRows": 1,
  "failures": [
    { "rowNumber": 4, "name": "无联系方式客户", "reason": "电话和邮箱至少填写一项" }
  ]
}
```

## 解析推广 Excel

`POST /monitor/promotions/import-preview`

使用与信息录入相同的模板。后端只进行一次性解析并返回全部数据行（包括校验失败行），不写入目标表，也不在后端内存中保存名单；页面负责本地分页和勾选，失败行不可勾选。

```json
{
  "batchNo": null,
  "totalRows": 6200,
  "successRows": 6180,
  "failureRows": 20,
  "failures": [],
  "list": [
    { "rowNumber": 2, "name": "王海峰", "phone": "13800138001", "email": "wanghf@example.com", "remark": "重点客户", "valid": true },
    { "rowNumber": 3, "name": "无联系方式客户", "phone": "", "email": "", "remark": "", "valid": false, "reason": "电话和邮箱至少填写一项" }
  ]
}
```

## 预览推广数量

`POST /monitor/promotions/preview`

Body：

```json
{
  "channel": "PHONE",
  "selectionMode": "MIXED",
  "targetIds": [18],
  "importedTargets": [
    { "name": "王海峰", "phone": "13800138001", "email": "wanghf@example.com", "remark": "重点客户" }
  ]
}
```

`channel` 支持 `PHONE`、`EMAIL`。`selectionMode`：

- `IDS`：使用 `targetIds` 明确选择，最多 6000 个 ID。
- `FILTER`：选择 `filters` 对应的全部有效目标。
- `ALL`：忽略所有筛选，无条件选择全部有效目标。
- `IMPORT`：使用请求体 `importedTargets` 中由浏览器暂存的 Excel 名单。
- `MIXED`：使用页面明确勾选的 `targetIds` 与 `importedTargets`；两类目标合计最多 6000 条。推广页当前使用此模式。

响应：

```json
{
  "matchedCount": 6358,
  "deliverableCount": 6358,
  "limit": 6000,
  "sendCount": 6000,
  "truncated": true
}
```

## 执行模拟推广

`POST /monitor/promotions/send`

Body 在预览请求基础上增加必填 `content`，最长 2000 字符。若范围超过上限，数据库目标按 `id ASC`、临时 Excel 按文件行序稳定截取前 6000 条。

```json
{
  "channel": "EMAIL",
  "content": "欢迎体验小马e保企业车险服务。",
  "selectionMode": "MIXED",
  "targetIds": [18, 19],
  "importedTargets": []
}
```

响应：

```json
{
  "batchNo": "MOCK20260827103520",
  "channel": "EMAIL",
  "requestedCount": 6358,
  "sentCount": 6000,
  "successCount": 6000,
  "failureCount": 0,
  "truncated": true,
  "mock": true
}
```

## 常见错误

| 状态/业务码 | 条件 | 信息示例 |
| --- | --- | --- |
| 400 | 分页、渠道、选择模式或字段格式错误 | `电话和邮箱至少填写一项` |
| 400 | 模板 Sheet/表头错误或超过 10000 行 | `模板表头已被修改` |
| 400 | 显式勾选的系统与 Excel 目标合计超过上限 | `推广目标不能超过6000条` |
| 401/403 | 未登录或无监控平台权限 | `请先登录` |
| 404 | 目标不存在或已软删除 | `推广目标不存在` |
| 500 | 数据库、模板输出或 JSON 序列化异常 | 统一脱敏错误信息 |
