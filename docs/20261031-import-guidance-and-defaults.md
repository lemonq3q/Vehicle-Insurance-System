# 险种默认值、导入填写说明与续保提醒名称

- 工单导入有商业保费时按当前企业各险种默认值建立快照，保留默认不投保的附加险。标准配置下为车损投保、三者100万，其余商业附加险不投保；无商业保费时商业险不投保。
- 独立交强险、车船税按各自金额判定；合并的“交强险、车船税”任一金额为正时采用原默认项，无金额时采用配置中的关闭项。
- 处理人姓名、登录邮箱、证件号至少填写一项；多条件同时匹配本企业有效用户。后端已同步拒绝三项全空，未新增数据库字段。
- 工单详情状态卡片使用纵向居中布局，使图标和状态文字在不同状态下保持整体水平、垂直居中。
- 菜单和路由标题“工单续保”统一改为“续保提醒”，原路由地址保留。
- 上游、下游、工单三个模板的“使用说明”改为面向填表用户的规则说明，并完善逐列字段说明。保留第四行表头、第五行示例、数据录入表默认样式及列宽、10,000条和20MB限制。
- 地区说明区分上/下游“所在地区”中文匹配、上游“业务区域”省份展开及多项分隔，以及工单“业务区域编码”原样保存。

## 实现与发布位置

前端：`frontend/src/components/DetailWorkorder.vue`、`MenuComponent.vue`、`frontend/src/router/index.js`。

后端：`backend/insruance/src/main/java/com/example/insurancesystem/service/WorkorderBatchImportService.java`。

API契约：`docs/api/workorder-batch-import-api.md`。Mock：`frontend/src/mock/batchImportMock.js` 中的 `workorderMissingHandlerMock`。

模板资源：`backend/insruance/src/main/resources/import/upstream.xlsx`、`downstream.xlsx`、`workorder.xlsx`，随服务打包发布，重新部署后下载即为新版。可直接查看的副本位于 `outputs/20261029-system-optimization/`，名称后缀为“（1万条版）”。

本轮只影响新导入工单，已导入工单的险种快照不会自动重写；不需要新增SQL迁移。

## 验证

- 前端 `npm run lint -- --no-fix`、`npm run build` 通过。
- 后端相关导入测试及 Maven 打包通过，覆盖商业险默认不投保保留、单独/合并交强车船税的正数/零/空值判定、处理人条件全空拒绝及单行失败隔离。
- 浏览器独立布局检查中，三种状态文字在不同卡片高度下均水平、垂直居中；未执行完整登录业务流程。
- 三个新版模板已渲染查看，并重读校验表头、录入表默认样式和资源副本一致。最终JAR中的三个模板与资源文件一致，重新部署后可通过下载接口获得新版。
