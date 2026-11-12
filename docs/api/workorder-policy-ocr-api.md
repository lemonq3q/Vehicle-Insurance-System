# 承保保单 OCR 接口

## 业务链路

1. `POST /file/oss/sign?fileName=...&contentType=...` 预创建 `sys_file` 文件记录并返回五分钟有效的 OSS PUT 签名；前端使用现有 `uploadToOss` 直传原文件。
2. `POST /ocr/policy` 以该文件 ID 创建后台识别任务，立即返回 `jobId`；同一企业、工单、文件在任务有效期内重复提交会复用同一个任务。
3. 前端每一秒请求 `GET /ocr/policy/tasks/{jobId}`，直到任务成功或失败。成功后只将返回字段放入承保编辑模型和待确认候选；用户确认并提交 `PUT /workorder/acceptInsurance` 后才持久化保单资料。

## POST /ocr/policy

作用：为当前企业可编辑工单的一份已上传 PDF 或图片创建识别任务，不在 HTTP 请求内等待模型。需要 JWT 及 `workorder:update` 权限。无 query、path、file/form-data 参数；header 沿用现有 token 请求头。

请求 body：

| 字段 | 类型 | 必填 | 说明 | 示例 |
| --- | --- | --- | --- | --- |
| `workorderId` | long / 十进制字符串 | 是 | 当前承保工单 ID | `101` |
| `fileId` | long / 十进制字符串 | 是 | `/file/oss/sign` 返回的 `fileInfo.id` | `900` |

请求示例：`{"workorderId":"101","fileId":"900"}`。后端仅使用数据库文件路径，不接受客户端提交的外部 URL。文件必须属于当前企业、未删除且 OSS 对象已存在；待报价等任意有效工单状态均可识别。允许现有页面可上传的 PDF、JPG、JPEG、PNG、WEBP、BMP、TIF、TIFF，页面继续使用已有 20 MB 限制；供应商 PDF 页数限制还需遵守百炼模型规则。

提交成功响应遵循统一外壳 `{code,msg,data}`，`data.jobId` 为 UUID 字符串，例如：

```json
{"code":200,"msg":"识别任务已提交","data":{"jobId":"c36cd295-903d-434d-b973-b0cc13e81384"}}
```

同一企业、工单和文件的任务索引与结果在 Redis 保存 15 分钟。任务排队、运行、成功和失败分别为 `QUEUED`、`RUNNING`、`SUCCEEDED`、`FAILED`；结果不写工单主表或 quote。提交时校验工单、企业归属和文件类型；任务线程执行前再次校验并确认 OSS 对象已上传完成。线程池最多并发 4 个、排队 40 个，满载时提交返回业务码 `503`。

## GET /ocr/policy/tasks/{jobId}

作用：查询当前企业已提交任务的执行状态与最终结果。需要 JWT 及 `workorder:update` 权限。`jobId` 是必填 path 参数，类型为 UUID 字符串，示例 `c36cd295-903d-434d-b973-b0cc13e81384`；无 query、body、file/form-data 参数，header 沿用 token 请求头。任务不存在、已过期或不属于当前企业时返回业务码 `404`。

成功查询仍使用 `{code,msg,data}`。`data` 包含 `jobId:string`、`workorderId:string`、`fileId:string` 和 `status:string`。完成时额外包含 `result:{code:number,msg:string,data?:object}`：成功时 `result.code=200`，`result.data.fileInfo` 为系统文件记录、`recognitionData` 为可回填字段、`warnings` 为兼容旧接口保留的空数组；失败时 `result.code` 为原业务错误码，`result.msg` 为错误信息，`result.data` 可缺失。前端只在 `SUCCEEDED` 且当前表单仍附有相同文件时回填；`FAILED` 直接停止轮询并提示错误。日期字段为 Unix 秒；金额为两位小数的十进制字符串。

运行中 mock 响应：

```json
{"code":200,"msg":"查询成功","data":{"jobId":"c36cd295-903d-434d-b973-b0cc13e81384","workorderId":"101","fileId":"900","status":"RUNNING"}}
```

`recognitionData` 字段白名单：

| 险种 | 字段 | 类型与含义 |
| --- | --- | --- |
| 商业险 | `commercialInsurerName`, `commercialPolicyNo`, `commercialInsuranceStartTime`, `commercialInsuranceEndTime`, `commercialAmount` | 公司/号码为字符串，起终期为 Unix 秒，保费为金额字符串 |
| 交强险 | `compulsoryInsurerName`, `compulsoryPolicyNo`, `compulsoryInsuranceStartTime`, `compulsoryInsuranceEndTime`, `compulsoryAmount`, `vehicleAndTaxAmount` | 同上；车船税只在明确单列时返回 |
| 非车险 | `nonMotorInsurerName`, `nonMotorPolicyNo`, `nonMotorInsuranceStartTime`, `nonMotorInsuranceEndTime`, `nonMotorAmount`, `nonMotorInsuranceName`, `nonMotorCoverageAmount` | 同上；额外含险种名称和保额 |

混合保单任务成功 mock 响应：

```json
{"code":200,"msg":"查询成功","data":{"jobId":"c36cd295-903d-434d-b973-b0cc13e81384","workorderId":"101","fileId":"900","status":"SUCCEEDED","result":{"code":200,"msg":"识别成功","data":{"fileInfo":{"id":"900","fileName":"组合保单.pdf"},"recognitionData":{"commercialInsurerName":"示例保险公司","commercialPolicyNo":"COM-0001","commercialInsuranceStartTime":1793980800,"commercialAmount":"3000.00","compulsoryInsurerName":"示例保险公司","compulsoryPolicyNo":"CTP-0001","compulsoryInsuranceStartTime":1793980800,"compulsoryAmount":"950.00","vehicleAndTaxAmount":"360.00"},"warnings":[]}}}}
```

失败任务 mock 响应：

```json
{"code":200,"msg":"查询成功","data":{"jobId":"c36cd295-903d-434d-b973-b0cc13e81384","workorderId":"101","fileId":"900","status":"FAILED","result":{"code":502,"msg":"保单识别服务暂不可用，请稍后重试"}}}
```

无分页、排序、删除或导出参数。结果不写工单主表或 quote，也不建立文件与工单的持久关联；现有承保保存事务仍负责关联文件。

## 模型处理与错误

- 后端在取得识别内容后不重复读取文件；遇到连接错误、408、409、429 或 5xx 时，按 `POLICY_OCR_REQUEST_RETRIES` 最多重试两次（默认），每次实际请求均计入企业 OCR 用量。其他 4xx 不重试。`POLICY_OCR_PROVIDER=qwen` 时识别和整理默认使用 `qwen3.8-flash`，PDF 经百炼 Chat Completions 的 `file.file_url` 读取，图片经 Responses API 的 `input_image` 读取；`POLICY_OCR_PROVIDER=doubao` 时两阶段默认使用 `doubao-seed-2-1-lite-260915`，PDF 经方舟 Responses API 的 `input_file.file_url` 读取，图片经 `input_image` 读取，并关闭深度思考以控制等待时间。豆包通过 URL 传入的 PDF 不能超过 50 MB。`POLICY_OCR_PROVIDER=gemini` 时两阶段默认使用 `gemini-3.8-flash`，通过 Interactions API 以 OSS 签名 URL 提交 PDF 或图片，以 JSON Schema 约束输出；签名 URL 文件上限为 100 MB。三家共用同一提取提示词、本地字段校验和有界整理流程。缺失、模糊、多保单冲突或无法确定归属时给 `null`，不能推算时间或猜测金额；仅在对应险种没有明确保费合计且分项保费完整、归属明确、不重叠时，允许模型求和得到该险种的 `premiumAmount`。不得混入保额、车船税或其他非保费费用，不得重复叠加明细与小计、合计。整理阶段只有原始 OCR 文本包含这些完整分项时才允许同样求和。
- 本地校验先解析 JSON，再遍历全部三险和全部字段，**收集本轮所有可判定问题**，包括结构、类型、金额、日期及起终期关系。JSON 根本不可解析时只报告整体格式问题，不伪造字段级结论。只有年月日的有效日期按 `Asia/Shanghai` 当天 `00:00:00` 转换为 Unix 秒并正常回填，不产生缺时间警告；含时分秒的日期保留原时间。起期和终期使用相同转换规则，不额外加一天或改为日末。
- 校验未通过时，整理模型只接收**原始 OCR 文本和完整校验问题列表**，通过严格 JSON Schema 输出；每次整理后重新做完整校验。`POLICY_OCR_ORGANIZER_RETRIES` 默认为 `2`，表示首次整理失败后最多重试两次，因此整理模型总调用上限为三次。整理请求不再次读取原文件。
- 最终仍有无效字段时，已通过校验的字段可在 `recognitionData` 返回，失败项仅在后端记录；没有可回填字段返回 `502`。模型与网络异常不改动工单，原文件仍保留。

提交常见失败响应：`400` 参数缺失或格式不支持；`403` 缺少 `workorder:update` 权限；`404` 工单或文件不存在、跨企业访问；`503` Redis 或任务线程池暂不可用。查询常见失败响应：`403` 无权限；`404` 任务不存在、过期或跨企业；`503` Redis 暂不可用。错误仍使用 `{code,msg}` 外壳。OSS 上传未完成、模型错误、无可用字段及密钥未配置保存在任务的 `FAILED.result` 中；失败任务释放同文件去重索引，允许再次提交。前端上传流程独立展示一次 OCR 失败提示。

## 前端回填与 mock

`frontend/src/api/ocr.js` 封装任务提交与状态查询；`frontend/src/mock/policyOcrMock.js` 模拟运行中与混合商业险/交强险、非车险多来源成功样例。开发环境设置 `VUE_APP_WORKORDER_POLICY_MOCK=true` 时，承保附件上传和识别都走本地 mock，不访问 OSS、模型或数据库。

独立的其他单证附件类型 `acceptInsuranceMisc` 仅使用文件上传和承保附件保存流程，最多六份；前端不会为这类附件调用本 OCR 接口。

`frontend/src/utils/policyOcr.js` 只接收字段白名单并转换日期。页面收到识别结果时自动展开识别出的险种；空字段直接回填，已有字段保留识别候选和逐项应用按钮（不显示来源文件名前缀）；全部应用只处理当前已选险种的有效候选。移除文件、重建表单或替换文件时清除相应候选，迟到结果不得再次回填。

保单任务提交后每一秒查询一次，前端最多等待十分钟；单次请求沿用全局 60 秒超时。页面重建、附件移除或识别结束后停止轮询。后端每轮记录格式校验是否通过、可回填字段名和全部问题；大模型输出日志最多预览前 3000 个字符，并记录原始长度及是否截断。服务重启期间未完成的内存排队任务不会恢复，前端等待超时后可重新上传文件创建任务。

前端在 OSS 上传结束后立即解除上传区遮罩，随后在该上传区旁显示独立的“OCR正在识别”状态。非车险多文件并发识别时显示正在处理的文件数；识别结束、附件移除或表单重载后清除对应状态。其他单证只记录文件，不显示 OCR 状态。

## 部署环境变量

| 变量 | 必填 | 默认值 | 用途 |
| --- | --- | --- | --- |
| `POLICY_OCR_PROVIDER` | 否 | `qwen` | 供应商选择：`qwen`、`doubao` 或 `gemini`；两阶段使用同一家供应商，修改后重启服务 |
| `DASHSCOPE_API_KEY` | 选择 qwen 时 | 无 | 百炼对应地域的 API Key，仅后端保存 |
| `DASHSCOPE_BASE_URL` | 选择 qwen 时 | 无 | 百炼 OpenAI 兼容地址，例如 `https://<WorkspaceId>.cn-beijing.maas.aliyuncs.com/compatible-mode/v1` |
| `POLICY_OCR_MODEL` | 否 | `qwen3.8-flash` | 百炼图片/PDF 直接提取模型；PDF 需使用支持 PDF 理解的北京或新加坡地域 |
| `POLICY_OCR_ORGANIZER_MODEL` | 否 | `qwen3.8-flash` | 百炼纯文本严格 JSON Schema 整理模型 |
| `ARK_API_KEY` | 选择 doubao 时 | 无 | 火山方舟 API Key，仅后端保存 |
| `ARK_BASE_URL` | 否 | `https://ark.cn-beijing.volces.com/api/v3` | 火山方舟 OpenAI 兼容数据面地址 |
| `POLICY_OCR_DOUBAO_MODEL` | 否 | `doubao-seed-2-1-lite-260915` | 豆包图片/PDF 直接提取模型 |
| `POLICY_OCR_DOUBAO_ORGANIZER_MODEL` | 否 | `doubao-seed-2-1-lite-260915` | 豆包纯文本 JSON Schema 整理模型 |
| `GEMINI_API_KEY` | 选择 gemini 时 | 无 | Google Gemini Developer API Key，仅后端保存 |
| `GEMINI_BASE_URL` | 否 | `https://generativelanguage.googleapis.com/v1beta` | Gemini Interactions API 基础地址 |
| `POLICY_OCR_GEMINI_MODEL` | 否 | `gemini-3.8-flash` | Gemini 图片/PDF 直接提取模型 |
| `POLICY_OCR_GEMINI_ORGANIZER_MODEL` | 否 | `gemini-3.8-flash` | Gemini 纯文本 JSON Schema 整理模型 |
| `POLICY_OCR_ORGANIZER_RETRIES` | 否 | `2` | 首次整理失败后的重试次数，允许 0—5 |
| `POLICY_OCR_REQUEST_RETRIES` | 否 | `2` | 文件 OCR 临时网络/服务错误后的重试次数，允许 0—5；每次尝试分别计入 OCR 用量 |

所有变量只配置在车险后端进程；前端不持有模型密钥。默认继续使用百炼；切换豆包设置 `POLICY_OCR_PROVIDER=doubao` 和 `ARK_API_KEY`；切换 Gemini 设置 `POLICY_OCR_PROVIDER=gemini` 和 `GEMINI_API_KEY`。其余参数可按需覆盖，切换后重启后端。运行前应确认所选账号已开通指定模型，且 OSS 签名地址可供供应商访问。方舟参数参考[文档理解](https://docs.volcengine.com/docs/ark/document-understanding?lang=zh)、[模型列表](https://docs.volcengine.com/docs/ark/model-list?lang=zh)和[结构化输出](https://docs.volcengine.com/docs/ark/structured-output-beta?lang=zh)；Gemini 参数参考[文件输入](https://ai.google.dev/gemini-api/docs/file-input-methods)、[Interactions API](https://ai.google.dev/gemini-api/docs/interactions-overview)和[结构化输出](https://ai.google.dev/gemini-api/docs/structured-output)。


## 2026-11-12 回填容错与通知规则

模型仍须按标准格式输出：日期为 `YYYY-MM-DD`（明确时间时为 `YYYY-MM-DD HH:mm:ss`），金额为无单位、无千分位且最多两位小数的字符串。后端兼容斜杠、点分隔、中文年月日日期及 ISO 的 T 分隔时间，兼容人民币符号/单位、正确分组千分位及 JSON 数字金额；兼容值归一化后仍列入整理问题，要求模型修正标准格式。不存在的日期、错误金额分组、超范围/超精度金额仍拒绝。

整理次数耗尽后，若存在可解析 JSON 中通过校验的字段，仅返回这些字段，丢弃其余字段。使用最近一次有可用字段的校验结果，不跨轮拼接；后续畸形 JSON 不清除此前有效结果。无任何可用字段或识别调用失败则返回失败。响应 `warnings` 保持空数组兼容旧接口，成功及部分成功均使用 `msg=识别成功`；详细校验信息仅保留在后端日志和整理请求中。前端成功静默回填，不展示字段缺失、已有值或质量汇总警告，失败统一提示识别失败。上传、保存的错误提示不受影响。
