# 导入与 OCR 交互调整（2026-10-30）

## 本轮变更

- 工单管理的“续保工单批量导入”入口沿用上下游批量导入按钮样式。
- 上游、下游及工单单次最多导入 10,000 条非空数据，文件上限维持 20 MiB。上下游写入按每批 500 条执行，仍处于原有整文件事务内。
- 下游列表、编辑页及新模板统一使用“联系电话”；提交人输入提示为“请输入提交人”。
- OCR 候选仅显示识别值，去掉“身份证：”“合格证：”等来源前缀；逐项应用后移除该字段候选，各识别区与页面均可全部应用。
- OCR 识别区字段名称对齐第一行输入框，输入框去除边框与阴影。

## 主要文件与接口契约

- 前端：`frontend/src/components/AllWorkOrder.vue`、`DownstreamMerchant.vue`、`EditDownstreamMerchant.vue`、`EditBaseWorkorder.vue`、`OcrCandidate.vue`；导入公共限制在 `frontend/src/api/batchImport.js`，OCR 候选处理在 `frontend/src/utils/ocrCandidates.js`。
- 后端：`BatchImportLimits.java`、`MerchantBatchImportServiceImpl.java`、`WorkorderBatchImportService.java`，以及下游 Excel 字段和校验文案。
- 联调 mock：`frontend/src/mock/batchImportMock.js`、`frontend/src/mock/downstreamContactMock.js`。OCR 候选为页面临时状态，不新增接口字段。
- API 文档：`docs/api/merchant-batch-import-api.md`、`docs/api/workorder-batch-import-api.md`、`docs/api/downstream-contact-api.md`。
- 发布模板：`backend/insruance/src/main/resources/import/` 下的三个工作簿，打包后由现有模板下载接口提供。本轮不调整数据库结构。

## 容量样本与验证

Python 生成并重读核验 `outputs/20261029-system-optimization/工单导入容量测试_10000条.xlsx`，包含 10,000 条数据、93 列，共 930,000 个已填业务单元格。文件为 17,739,720 字节（16.918 MiB），占 20 MiB 上限的 84.59%，本样本可通过大小限制。样本仅用于文件容量验证，不代表所有字段最大长度，也未向业务数据库写入一万条测试工单。

已通过前端 lint、生产构建及 OCR 候选的 3 项测试；后端导入相关 10 项测试通过，包含第 10,001 条拒绝且不写入的边界验证。浏览器独立组件检查通过字段对齐、无边框、隐藏前缀、单项应用和全部应用；完整登录后的业务流程未进行端到端验证。
