# 车险管理系统前端 UI 重构说明

## 目标与边界

本次重构将 `frontend/` 的视觉语言对齐现有 SaaS 门户和监控系统：深色导航、浅色工作区、白色内容卡片、蓝色主操作、统一的边框和文字层级。静态参照样例位于 `outputs/ui-preview/vehicle-insurance/index.html`。

本次没有修改车险前端的 API、路由、Vuex、权限判断、查询参数、工单状态、表单校验或批量导入处理流程。工单录入仍由原来的 `EditBaseWorkorder.vue` 抽屉承载，批量导入仍由 `MerchantBatchImportDialog.vue` 调用原有接口和校验逻辑。列表中的查询、重置、导出、分页和详情入口均保留原组件与事件绑定。

## 实现方式

原有的 `global.css` 和 `container.css` 仍管理通用重置及页面布局。新增 `frontend/src/style/theme.css`，在 `main.js` 中排在原样式之后引入，集中定义车险主题变量，并覆盖现有容器类及 Element Plus 的外观。这样各列表页共享主题，而原先筛选区字段排列、卡片内边距、按钮位置、表格列及分页结构都由旧样式和原模板保持。

主题只对 `.home` 下的业务页面做大部分覆盖。Element Plus 弹窗、抽屉和下拉浮层使用 Teleport 挂载在 `body` 下，相关覆盖仅针对这些组件类，避免影响登录业务。现有 Element Plus 继续使用，没有引入新的组件库或依赖。

| 部分 | 原结构 | 本次结构与视觉变化 |
| --- | --- | --- |
| 应用入口 | `main.js` 加载 Element Plus、`global.css`、`container.css` | 顺序不变，末尾再加载 `theme.css` 作为视觉覆盖层 |
| 页面布局 | `HomePage.vue` 顶栏横跨全屏，下方再分左右区域 | 改为左侧固定导航、右侧上方页头和下方内容区；保留原 `HeaderComponent`、`MenuComponent`、`router-view` 及业务页面 |
| 菜单 | `MenuComponent.vue` 的 `el-menu` 与原权限、路由 | 菜单数据和逻辑不变；参考监控系统增加分组标题、品牌区、图标与高亮样式 |
| 列表与表单 | 既有 `container_body`、`search`、`params_container`、Element Plus 表格和输入框 | 类名及模板不变；统一卡片、输入、按钮、表格、分页的颜色与圆角 |
| 详情与导入 | 原工单详情分区、录入抽屉、批量导入弹窗 | 流程不变；详情区、上传提示区、结果汇总的表面样式与主题一致 |
| 登录和注册 | 原背景图片、蓝色按钮与标签 | 保留表单结构与逻辑；背景与按钮颜色改为三系统协调的深色品牌方案 |

## 具体修改

- `frontend/src/style/theme.css`：新增品牌色、语义文字色、边框、卡片阴影与 Element Plus 主题变量；覆盖业务容器、表格、输入、按钮、分页、弹窗和键盘焦点样式。输入框聚焦时只显示 1px 蓝色内边框，不再出现额外粗蓝色轮廓。
- `frontend/src/style/global.css`：移除旧的全局焦点轮廓清零规则，避免键盘用户无法辨认当前操作位置。
- `frontend/src/page/HomePage.vue`：调整外层模板的呈现顺序为左导航、右侧页头及内容；添加与监控系统相同的品牌标识展示，保留原顶栏、菜单和 `router-view` 组件。
- `frontend/src/components/MenuComponent.vue`、`HeaderComponent.vue`：菜单增加纯展示分组标题，样式参考监控系统；菜单权限、路由高亮、通知、用户菜单和返回门户逻辑不变。菜单内容区使用 `overflow-y: auto`，且不再强制占满父级高度，所以仅在内容高度超过可用高度时出现滚动条。
- `frontend/src/assets/brand/idatag-monitor-logo.png`：复用监控系统现有品牌图形，避免三套前端使用不同标识。
- `frontend/src/components/DetailWorkorder.vue`：只改详情分区的颜色、边框、圆角和静态链接色；未改业务脚本或事件。
- `frontend/src/components/common/MerchantBatchImportDialog.vue`：只改模板提示、汇总卡片的配色；文件选择、模板下载、上传、失败明细和重试流程不变。
- `frontend/src/components/SystemNotice.vue`、`PersonalCenter.vue`：局部颜色和边框对齐主题。
- `frontend/src/page/LoginPage.vue`、`SavePage.vue`：只改样式区的背景、卡片与主要按钮配色。

## 验证与维护

已运行 `npm run lint`，无错误。生产构建在当前受限执行环境中首次因 `thread-loader` 无法启动子进程而报 `spawn EPERM`；随后仅在校验命令运行时将 Node 识别到的 CPU 数量设为 1，关闭并行 worker 后构建成功。构建仅有现有第三方资源体积阈值警告，没有编译错误；未修改 `vue.config.js` 或构建依赖。

今后若要调整三系统协调色，优先修改 `theme.css` 中的 `--insurance-*` 变量。局部业务页面确有独特语义时，再修改页面自己的 `scoped` 样式；不要把新色值散落到业务脚本、表单字段或 API 中。上线前仍建议在真实数据与权限角色下检查工单列表、录入抽屉、详情状态流转、上下游批量导入、登录和注册页面的视觉呈现。
