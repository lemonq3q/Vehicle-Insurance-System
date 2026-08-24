# 上下游批量导入 API

## 统一约定

- 基础地址：车险后端，例如 `http://localhost:8080`。
- 鉴权头：`token: <车险系统登录令牌>`。
- 统一响应：`{ "code": 200, "msg": "...", "data": ... }`。
- 上传格式：`multipart/form-data`，文件字段名固定为 `file`，只支持 `.xlsx`。
- 业务权限：模板查询和结构校验需要 `merchant:select`；正式导入需要 `merchant:update`。
- 模板结构校验只检查文件格式、Sheet 名称以及第 4 行表头，不校验数据行。
- 正式导入会逐行校验，成功行按表使用单条批量 INSERT 写入，失败行不入库并返回原因。

## 获取模板地址

`GET /merchant/batch-import/templates`

作用：服务端读取配置中的 OSS 对象路径，通过统一 `OSSUtil` 返回私有对象临时签名地址。地址优先从 Redis
读取，缓存到期后自动重新向 OSS 签名；OSS 地址有效期和 Redis 缓存期由
`insurance.oss.temporary-url` 统一配置，且缓存期必须短于签名有效期。

响应示例：

```json
{
  "code": 200,
  "data": {
    "upstreamUrl": "https://lemonqwq.oss-cn-hangzhou.aliyuncs.com/insurance/templates/vehicle-insurance-upstream-import.xlsx?x-oss-signature=...",
    "downstreamUrl": "https://lemonqwq.oss-cn-hangzhou.aliyuncs.com/insurance/templates/vehicle-insurance-downstream-import.xlsx?x-oss-signature=..."
  }
}
```

## 校验上游模板

`POST /merchant/batch-import/upstream/validate`

请求：`file`，必填，官方上游 `.xlsx` 文件。

## 校验下游模板

`POST /merchant/batch-import/downstream/validate`

请求：`file`，必填，官方下游及商户人员 `.xlsx` 文件。

校验响应示例：

```json
{
  "code": 200,
  "data": {
    "valid": true,
    "fileName": "车险系统_下游及商户人员批量导入模板.xlsx",
    "message": "模板结构校验通过，可以继续导入"
  }
}
```

`valid=false` 的常见原因包括：不是 `.xlsx`、缺少固定 Sheet、数据 Sheet 第 4 行表头被删除、重命名或调换顺序。数据为空、地区错误、手机号错误不会导致此阶段失败。

## 导入上游数据

`POST /merchant/batch-import/upstream`

请求：`file`，必填，已经通过结构校验的上游文件。

校验内容：必填字段、字段长度、手机号、所在地区、业务区域、文件内重复名称及当前企业已有名称。业务区域允许省份展开为全部市级编码。

## 导入下游及商户人员

`POST /merchant/batch-import/downstream`

请求：`file`，必填，已经通过结构校验的下游文件。

校验内容：

- 下游机构：必填字段、类型枚举、字段长度、所在地区、银行卡号、文件内及数据库内重复名称。
- 商户人员：必须关联本文件内通过校验的下游；校验姓名、手机号、角色、身份证号、状态、重复手机号；同一下游最多导入一个联系人。
- 人员 Sheet 可以为空。人员行失败不会影响其对应的有效下游机构入库。

导入响应示例：

```json
{
  "code": 200,
  "msg": "导入完成",
  "data": {
    "totalRows": 3,
    "successRows": 2,
    "failureRows": 1,
    "failures": [
      {
        "sheetName": "商户人员",
        "rowNumber": 8,
        "rowData": {
          "下游名称": "广州示例汽车销售有限公司",
          "人员姓名": "李四",
          "手机号码": "13900139000",
          "人员角色": "联系人"
        },
        "reason": "当前企业已存在相同手机号的商户人员"
      }
    ]
  }
}
```

## 错误情况

- `code=400`：正式导入时模板结构不符。
- HTTP 401/403：未登录或缺少对应权限。
- HTTP 500：工作簿损坏、分类字典缺失或批量 SQL 出现未预期数据库错误；事务整体回滚。
- 单个数据行错误不会返回接口级失败，而会进入 `data.failures`。
