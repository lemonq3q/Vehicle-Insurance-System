# SaaS 门户加载反馈

本次替换原先“所有请求和路由均全屏遮罩”的实现。使用自定义绿色圆环、白色半透明遮罩，不再引入 Element Plus 依赖，也不修改现有页面布局或后端接口。

## 范围规则

| 场景 | 加载反馈 |
| --- | --- |
| 后台保存资料、创建/加入企业、成员权限变更、删除、订阅支付等重要写入 | 只覆盖 main 内容区；侧栏、页头保持可操作；已有绑定弹窗在框内反馈 |
| 访客登录、注册、重置密码、提交联系表单 | 全屏白色遮罩；请求成功、失败、取消、超时均释放 |
| 普通数据读取、查询、筛选、分页、费用试算、支付状态刷新、结账页面初始化 | 对应业务父区域的局部白色遮罩；页面导航和其他区域仍可操作 |
| 邮箱验证码、进入车险系统 | 对应按钮内的小圆环 |
| 路由切换 | 不统一开启全屏加载；目标页面的数据请求由自身局部区域显示反馈 |
| 微信组件 SDK 与 iframe 加载 | 二维码区域内的加载反馈；失败、超时沿用重试提示 |

## 父区域分组

| 父区域 | 合并数据 |
| --- | --- |
| 仪表盘 | 全部统计卡片、近期提醒 |
| 企业人员 | 成员列表、成员变更日志 |
| 企业信息 | 企业资料；邀请码作为独立子区域 |
| 订阅服务 | 财务概览、可选套餐 |
| 订阅订单试算 | 财务概览、套餐、费用预览 |
| 各订单/流水列表 | 表格与分页作为一个区域 |
| 支付详情 | 订单摘要和结账区域分别加载；支付状态刷新不遮挡结账表单 |

同一 scope 并发计数，全部任务结束后才关闭一个父级动画。局部遮罩只附着在有明确边框的卡片或按钮上，使用绝对定位 inset:0，覆盖完整 padding box。无边框的整页数据组通过 main:true 使用内容区遮罩，不绘制浮空的白色矩形。

main 遮罩是滚动内容层的兄弟节点；外层负责侧栏宽度、页头高度和定位，内层 main 单独负责 padding 与滚动。因此遮罩不受内边距、滚动位置或已展开的页面高度影响。main 加载期间仅 main 进入 inert，导航和页头不受影响。

## 首次数据确认

企业信息、用户中心、订阅服务、充值创建与详情、订阅试算与详情、企业人员、邀请码及订单/流水列表统一使用 createPageLoad/loadPageData。首次 pending=true、ready=false，只呈现加载反馈，不渲染真实/空状态的业务分支。

完整快照取得后才设 ready=true；成功返回空数据时才能显示加入企业、未订阅或无记录。失败保持未确认状态并提供重试，不能误显示空业务分支。刷新失败保留此前已确认数据并显示错误，不清空为默认值。DataLoadFeedback 负责有边框的初始占位及错误恢复。

## 前端配置

API 配置由 `systemportal/src/api/portal.js` 集中维护：

```js
// 仅由前端消费，不放入 URL、请求 body 或服务端字段。
{ loading: { scope: 'members' } }
{ loading: { fullscreen: true } }
```

模板使用 `v-region-loading="'invites'"`；多个查询组使用数组，也支持 `{ scopes: ['checkout'], busy: checkoutState === 'loading' }` 接入第三方 SDK 的本地状态。无边框的整页分组使用 `{ main: true, busy: pageLoad.pending }`。按钮使用 `compact: true`。未声明 loading 的请求不自动开启遮罩。

普通局部刷新延迟 160ms，快速完成的任务不闪现动画；首次未知数据通过 ready 门控，main 立即显示。圆环使用 `--portal-accent`，文字使用 `--portal-accent-strong`，遮罩使用白色。支持 `aria-busy`、状态播报与系统减少动态效果偏好。

## 验证与接口影响

- `node scripts/test-portal-loading.js`：真实 Axios 与 Vue 观察器验证区域隔离、父级合并、延迟、卸载、重要提交分类、异常收尾。
- `node scripts/test-portal-initial-render.js`：使用真实 Vue 模板进行 12 页首帧、失败状态及企业成功/空数据分支验证。
- `node scripts/test-http-error-contract.js`：既有三端 HTTP/业务错误契约回归。
- `node scripts/test-wechat-frame-lifecycle.js`：微信二维码生命周期回归。
- 门户 `npm run lint -- --no-fix`、`npm run build`。

本次不改变数据库、后端响应或 API 契约，无需新增业务 Mock；测试中的接口与时钟替身位于 `scripts/test-portal-loading.js`，不进入生产包。
