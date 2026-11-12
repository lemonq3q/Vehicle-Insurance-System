# 工单详情返回与列表状态恢复

## 页面行为

从工单管理点击详情时，保留当前查询输入、日期、地区、机构/人员远程下拉选项、分页大小、页码、表格行和滚动位置。详情的“返回”按钮悬浮在工作台main视窗右上角，滚动详情时仍可操作。接单、分派和续保列表采用相同逻辑，详情返回各自的来源列表。

只读查看后返回不重新查询，直接恢复离开时的列表。详情成功保存报价、支付、承保或基础资料后，返回时使用离开前最近一次已提交的查询参数刷新数据，保留页面筛选输入和页码，再恢复滚动。尚未点击“查询”的输入不会被当作新的筛选请求。

## 实现方案

1. **缓存页面实例。** `frontend/src/page/HomePage.vue`使用RouterView插槽配合Vue KeepAlive，仅缓存AllWorkOrder、AcceptWorkOrder、DispatchWorkOrder、RenewWorkorder四个列表，最大4个实例。以路由path作为key，详情和编辑表单不缓存。筛选值、Date对象、下拉候选、表格数据及组件状态均由原实例保留，无需序列化后重建。
2. **保存真实视窗滚动。** 离开列表之前记录main的scrollTop/scrollLeft以及Element Plus表格内部滚动位置。路由切换完成并nextTick后恢复；查询刷新后再次恢复。异步恢复绑定原列表路径，快速进入其他页面时不滚动新页面。
3. **显式记录来源。** 各详情入口在query携带id和returnTo，ID保持字符串。详情优先读取路由ID，兼容旧sessionStorage入口，避免共享存储中的旧ID覆盖当前详情。返回地址只允许四个列表；历史上一页匹配来源时router.back，否则router.replace，直接打开详情默认返回工单管理。
4. **成功保存后刷新缓存数据。** HomePage提供内存中的修改revision；详情仅在后端确认成功或基础资料保存成功时推进版本。各列表激活时比较版本，必要时通过原有查询接口刷新，使用最近已提交的参数。单纯查看、失败提交不触发刷新。
5. **按钮悬浮定位。** 详情顶部零高度锚点使用position:sticky、top:0和右侧对齐；main本身是滚动容器，因此按钮参照main视窗而不是整个浏览器。锚点不拦截内容点击，按钮本身保持键盘及鼠标可操作。

导航和滚动工具位于`frontend/src/utils/workorderNavigation.js`，不新增依赖、接口或浏览器持久化客户数据。缓存及滚动快照随HomePage销毁而释放；退出登录、离开工作台或刷新整个浏览器页面后不保留这些内存状态。本需求覆盖列表与详情之间的正常路由往返；没有新增跨刷新持久化。

## 文件与验证

- `HomePage.vue`：页面缓存、视窗滚动快照和修改上下文。
- `AllWorkOrder.vue`、`AcceptWorkOrder.vue`、`DispatchWorkOrder.vue`、`RenewWorkorder.vue`：详情来源和最后已提交的查询参数。
- `DetailWorkorder.vue`：悬浮返回按钮、路由ID优先和成功保存的刷新通知。
- `frontend/tests/workorderNavigation.test.js`：真实HomePage编译并通过Vue/Router/KeepAlive隔离渲染器执行导航；验证状态保留、返回路径、各列表查询复用、main/表格滚动、保存后刷新、快速导航不串位、退出释放缓存。

接口继续复用现有工单查询/详情/更新契约，无新增API或数据库变更。导航测试中的列表数据与请求响应在隔离测试内提供，不接管生产接口；金额mock继续位于`frontend/src/mock/moneyDisplayMock.js`。

验证命令：frontend目录运行`node --test tests/*.test.js`、`npm run lint -- --no-fix`、`npm run build`。未进行真实浏览器交互或截图验收，未自动部署。

验证结果：工单导航4项测试全部通过；与金额跨应用回归和既有前端测试联合运行共26项全部通过。车险前端lint及生产构建通过，门户和监控的金额修改也分别通过lint与生产构建。构建存在既有包体积及Browserslist过期提示，无构建错误。
