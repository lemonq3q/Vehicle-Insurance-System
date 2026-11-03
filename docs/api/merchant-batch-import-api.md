# 上下游批量导入 API（2026-10-29版本）

## 统一约定

- 基础地址：车险后端，例如 `http://localhost:8080`。
- 鉴权头：`token: <车险系统登录令牌>`。
- 统一响应：`{ "code": 200, "msg": "...", "data": ... }`。
- 上传格式：`multipart/form-data`，文件字段名固定为 `file`，只支持 `.xlsx`。
- 业务权限：模板查询和结构校验需要 `merchant:select`；正式导入需要 `merchant:update`。
- 模板结构校验只检查文件格式、Sheet 名称以及第 4 行表头，不校验数据行。
- 正式导入会逐行校验，成功行按500条分批写入，全部批次处于同一事务中，失败行不入库并返回原因。

## 下载当前版本模板

`GET /import-templates/upstream`、`GET /import-templates/downstream`，权限merchant:select，token请求头。直接返回当前后端版本内的官方xlsx，Content-Disposition提供文件名。前端使用统一请求封装下载，不依赖旧OSS人员绑定模板。

上游、下游各一张独立数据表，分别导入，无需人员绑定。下游数据Sheet固定为“下游机构”，第四行表头严格为：

`* 下游名称`、`* 下游类型`、`* 所在地区`、`详细地址`、`* 开户银行`、`* 银行卡号`、`* 联系人`、`* 联系电话`、`备注`。

contact为100字内姓名，phone为11位大陆手机号；每行直接保存下游与联系人。类型为车商店铺/汽修厂/代理人；其他地区及银行卡校验沿用既有规则。新增与编辑资料详见downstream-contact-api.md。

## 校验上游模板

`POST /merchant/batch-import/upstream/validate`

请求：`file`，必填，官方上游 `.xlsx` 文件。

## 校验下游模板

`POST /merchant/batch-import/downstream/validate`

请求：`file`，必填，官方下游 `.xlsx` 文件。

校验响应示例：

```json
{
  "code": 200,
  "data": {
    "valid": true,
    "fileName": "下游批量导入模板.xlsx",
    "message": "模板结构校验通过，可以继续导入"
  }
}
```

`valid=false` 的常见原因包括：不是 `.xlsx`、缺少固定 Sheet、数据 Sheet 第 4 行表头被删除、重命名或调换顺序。数据为空、地区错误、手机号错误不会导致此阶段失败。

## 导入上游数据

`POST /merchant/batch-import/upstream`

请求：`file`，必填，已经通过结构校验的上游文件。

校验内容：必填字段、字段长度、手机号、所在地区、业务区域、文件内重复名称及当前企业已有名称。业务区域允许省份展开为全部市级编码。

## 导入下游

`POST /merchant/batch-import/downstream`，权限merchant:update，multipart/form-data的file必填，官方xlsx。

校验主体名称、类型、地区、开户银行、银行卡号、联系人与手机号。名称按当前企业同方向有效机构唯一，联系人姓名或手机号可在不同下游重复。文件内重名行拒绝，数据库并发唯一冲突回滚当前批次。

响应示例：

```json
{"code":200,"msg":"导入完成","data":{"totalRows":2,"successRows":1,"failureRows":1,"failures":[{"sheetName":"下游机构","rowNumber":6,"rowData":{"下游名称":"示例商家","联系人":"李四","联系电话":"123"},"reason":"联系电话必须为11位中国大陆手机号"}]}}
```

## 错误情况

- `code=400`：正式导入时模板结构不符。
- HTTP 401/403：未登录或缺少对应权限。
- HTTP 500：工作簿损坏、分类字典缺失或批量 SQL 出现未预期数据库错误；事务整体回滚。
- 单个数据行错误不会返回接口级失败，而会进入 `data.failures`。

单次最多10,000条非空业务数据行，超出上限整体拒绝且不写入；示例行若未删除也计入数据行。文件大小仍为20MiB（20×1024×1024字节）。

失败明细在前端本地分页展示，默认每页20条，可切换50或100条并跳转页码。接口仍一次返回完整`data.failures`，不新增分页请求参数；翻页不重复发起导入。切换每页条数、新结果及重新选择文件均回到第一页，重新打开弹窗恢复每页20条。联调分页样例为`frontend/src/mock/batchImportMock.js`中的`batchImportPaginatedResultMock`（101条失败）。
