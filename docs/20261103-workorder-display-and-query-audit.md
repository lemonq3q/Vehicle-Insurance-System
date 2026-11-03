# 工单列表展示调整与其他查询检查

## 本次展示调整

处理人列在总览、接单、派单、续保四个入口统一采用：姓名、绑定邮箱、微信昵称、手机号、登录账号，依次选择第一个非空白字段。姓名存在时不再附带内部微信账号。

上下游政策费用仅在展示层补零：已有成功报价时间，或流程进入核保及其后阶段（状态4至10），费用合计缺失时显示0；无成功报价依据的待处理、待报价、报价失败继续显示空白。已有金额及精确金额字符串不改写。保留旧成功报价时间的重新报价工单也显示已确认的零费用。

后端不是纯零改动：WorkorderDTO及WorkorderMapper增加了处理人的handleUserEmail、handleUserWechatNickname、handleUserUsername三个兼容返回字段，均来自现有tenant_user列；没有新增数据库字段、接口路径、额外查询或修改分页。费用计算、原sum字段与Excel导出语义不变。部署需要同步更新前端和保险后端服务，SaaS/监控服务无需为本次展示单独修改；common是保险后端构建依赖。

### 修改位置

- frontend/src/utils/workorderDisplay.js：统一展示函数，不覆盖原接口业务字段。
- frontend/src/components/AllWorkOrder.vue、AcceptWorkOrder.vue、DispatchWorkOrder.vue、RenewWorkorder.vue：接入展示字段。
- backend/common/src/main/java/com/example/insurancesystem/domain/workorder/WorkorderDTO.java：新增只读身份字段。
- backend/insruance/src/main/resources/mapper/WorkorderMapper.xml：同一处理人关联中补充列及映射。
- frontend/src/mock/workorderListDisplayMock.js：待报价、已报价零费用、邮箱回退及精确金额联调样例。
- docs/api/downstream-contact-api.md：兼容字段和展示规则契约。
- frontend/tests/workorderDisplay.test.js：姓名优先、身份回退、状态边界、金额精度及原始字段不变验证。

## 验证

- `node --test tests/workorderDisplay.test.js`：5项通过。
- `npm run build`：成功，存在依赖包体积及Browserslist数据过期提示，没有构建错误。
- `mvn -o -pl insruance -am -Dtest=WorkorderMapperRenewalSqlTest -Dsurefire.failIfNoSpecifiedTests=false test`：common/insruance编译及现有Mapper测试通过。
- 定向diff空白检查通过。
- 没有进行浏览器截图/交互验收，未自动发布或重启现有服务。

## 其他查询的只读检查

抽查了本地当前数据上的23种查询形状，采用EXPLAIN ANALYZE，单条限制12秒。覆盖当前Mapper展开SQL、注解SQL代表形状及实际统计/监控SQL；不是对每一个接口、每一种条件和云端并发的穷举。没有在检查过程中改数据、索引或业务代码。

| 场景 | 数据库内部时间 |
| --- | ---: |
| 上游/下游首页 | 约0.11ms / 0.12ms |
| 下游总数 | 4.19ms |
| 上游/下游区域筛选计数 | 1.24ms / 1.21ms |
| 批量读取20个商户的区域 | 4.61ms |
| 工单详情 | 0.069ms |
| 单工单险种、附件 | 0.050ms / 0.072ms |
| 续保列表、待续保计数、列表总数 | 22.8ms / 14.5ms / 15.0ms |
| 企业成员列表、当前成员关系 | 0.126ms / 0.020ms |
| 订单、充值、钱包流水 | 约0.02至0.03ms |
| 监控企业完整首页SQL | 0.431ms |
| 每日新增客户统计、日终盈利统计 | 1.66ms / 19.5ms |

以上为单次数据库内部时间，不包括HTTP、网络、Java鉴权/组装、前端渲染；财务、监控样本很小，续保列表当前没有命中，不能据此承诺未来大数据量性能。邮箱登录只测试未命中路径，执行计划为Impossible WHERE，不代表成功登录的性能。

**本次抽查未复现之前约5秒、千万级重复扫描的关联瓶颈。** 工单详情、险种和附件已能受益于前次新增索引。

### 仍存在的潜在风险（未擅自修改）

1. **商户区域表biz_merchant_area只有主键。** 按merchant_id、area_code及enterprise_id查询没有对应联合索引。当前4399条，区域EXISTS可被优化为一次扫描/物化，单次仅1至5ms；不是已确认的秒级慢查询。但区域增长或逐商户读取时可能退化，后续可评估 `(enterprise_id,merchant_id,deleted,area_code)`，并同步考虑区域优先检索的顺序。
2. **tenant_member按user_id查当前企业关系缺少以user_id开头的索引。** 现有联合索引都以enterprise_id开头，目前仅7名成员，约0.02ms；若未来大批用户，需评估 `(user_id,deleted,status)` 等适配索引。
3. **支付回调表按checkout_session_id读取退款历史缺少对应索引。** 目前清理后只剩3条事件，约0.018ms；回调历史累积后应评估 `(checkout_session_id,event_type)`。不可把这一条低样本测量解读成今后永远不会慢。
4. **邮箱登录条件使用LOWER(TRIM(email))，未直接利用active_email生成列的唯一索引。** 当前用户量很小，本次仅检查了未命中路径；规模增大时需结合登录兼容规则评估，不能直接替换而影响历史账号鉴权。
5. **日终盈利查询先按全表佣金聚合再关联目标日期工单。** 当前7350条佣金，约19.5ms，按全企业日终批处理符合用途；未来增长后可观察是否值得限定工单范围。没有出现核保表逐工单重复全扫描。

本轮只按要求检查并报告，以上潜在风险没有新增索引或修改SQL。证据位于 `outputs/other-query-audit-20261103/`：results.json、各查询SQL和对应执行计划。
