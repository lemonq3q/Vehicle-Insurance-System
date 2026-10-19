const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const Vue = require('../systemportal/node_modules/vue');
const { parse } = require('../systemportal/node_modules/@vue/compiler-sfc');
const { renderToString } = require('../systemportal/node_modules/@vue/server-renderer');

/**
 * 加载真实的首屏状态管理函数；测试只提供数据读取任务，不访问业务服务。
 */
function loadState() {
  const source = fs.readFileSync(path.join(__dirname, '../systemportal/src/utils/pageLoad.js'), 'utf8')
    .replace(/export (async function|function) /g, '$1 ');
  const context = { module: { exports: {} } };
  vm.runInNewContext(source + '\nmodule.exports = { createPageLoad, loadPageData };', context);
  return context.module.exports;
}
const stateHelpers = loadState();

/**
 * 编译真实 SFC 模板并使用 Vue 服务端渲染检查首帧内容，保留页面数据和条件判断。
 * 替换接口与无关子组件，关闭生命周期以手动注入慢请求、失败和成功后的状态快照。
 * @param {string} relative 门户 src 内的 Vue 文件路径。
 * @returns {object} 含实际 render 与 data/computed/methods 的页面组件。
 */
function component(relative) {
  const filename = path.join(__dirname, '../systemportal/src', relative);
  const { descriptor } = parse(fs.readFileSync(filename, 'utf8'));
  const context = { module: { exports: {} }, ...stateHelpers };
  const placeholder = { render: () => Vue.h('span') };
  const source = descriptor.script.content.replace(/^import (.*?) from ['"].*?['"];\r?$/gm, (line, names) => {
    const entries = names.startsWith('{') ? names.slice(1, -1).split(',') : [names];
    for (const entry of entries) {
      const name = entry.trim();
      if (!(name in context)) context[name] = /^[A-Z]/.test(name) ? placeholder : () => '';
    }
    return '';
  }).replace('export default', 'module.exports =');
  if (relative !== 'components/DataLoadFeedback.vue') context.DataLoadFeedback = component('components/DataLoadFeedback.vue');
  vm.runInNewContext(source, context, { filename });
  const options = context.module.exports;
  delete options.created;
  delete options.mounted;
  options.render = Vue.compile(descriptor.template.content);
  return options;
}

/**
 * 在真实 Vue 环境渲染指定业务快照，注入只读角色和路由以支持页面现有 computed。
 * @param {object} options 真实页面组件。
 * @param {object} snapshot 需要覆盖的服务端数据与首屏加载状态。
 */
async function html(options, snapshot) {
  const app = Vue.createSSRApp({ ...options, data: () => ({ ...options.data(), ...snapshot }) });
  app.directive('region-loading', { getSSRProps: () => ({}) });
  app.component('router-link', { render: () => Vue.h('a') });
  app.component('router-view', { render: () => Vue.h('div') });
  app.config.globalProperties.$store = { getters: { roleCode: 'ISSUER' }, state: { user: {}, currentMember: {} } };
  app.config.globalProperties.$route = { params: {}, query: {}, path: '/portal', meta: {} };
  app.config.globalProperties.$router = {};
  return renderToString(app);
}

/**
 * 慢请求期间及失败后都禁止渲染默认业务分支；只有成功后，空数据才具有“无企业/无记录”的语义。
 * 同时验证刷新失败保留已确认的数据，不把失败转换为新的空快照。
 */
async function run() {
  const state = stateHelpers.createPageLoad();
  let finish;
  const pending = stateHelpers.loadPageData(state, () => new Promise(resolve => { finish = resolve; }));
  assert.equal(state.ready, false);
  assert.equal(state.pending, true);
  finish(); await pending;
  assert.equal(state.ready, true);
  await stateHelpers.loadPageData(state, async () => { throw new Error('读取失败'); });
  assert.equal(state.ready, true);
  assert.equal(state.error, '读取失败');

  const cases = [
    ['views/enterprise/EnterpriseInfoPage.vue', '创建并成为企业拥有者'],
    ['views/user/UserCenterPage.vue', '未绑定邮箱'],
    ['views/finance/SubscriptionServicePage.vue', '暂未订阅'],
    ['views/finance/RechargePlaceholderPage.vue', '创建充值订单'],
    ['views/finance/SubscriptionOrderRecordDetailPage.vue', '未找到该订阅订单'],
    ['views/finance/SubscriptionOrderDetailPage.vue', '未找到对应套餐'],
    ['views/finance/RechargeOrderDetailPage.vue', '充值已到账'],
    ['views/finance/RechargeOrdersPage.vue', '暂无'],
    ['views/finance/SubscriptionOrdersPage.vue', '暂无'],
    ['views/finance/WalletTransactionsPage.vue', '暂无'],
    ['views/enterprise/EnterpriseMembersPage.vue', '暂无'],
    ['components/InviteManagementPanel.vue', '暂无']
  ];
  for (const [file, wrongDefault] of cases) {
    const page = component(file);
    const initial = await html(page, {});
    assert(initial.includes('正在加载，请稍候'), file);
    assert(!initial.includes(wrongDefault), `${file}: initial render leaked a default branch`);
    const failed = await html(page, { pageLoad: { ready: false, pending: false, error: '读取失败' }, loading: false });
    assert(failed.includes('重新加载'), file);
    assert(!failed.includes(wrongDefault), `${file}: failure must not be rendered as empty data`);
  }
  const enterprise = component('views/enterprise/EnterpriseInfoPage.vue');
  const ready = { ready: true, pending: false, error: '' };
  const existing = await html(enterprise, { pageLoad: ready, enterprise: { name: '真实企业' } });
  assert(existing.includes('真实企业'));
  assert(!existing.includes('创建并成为企业拥有者'));
  const absent = await html(enterprise, { pageLoad: ready, enterprise: null });
  assert(absent.includes('创建并成为企业拥有者'));
  const layout = component('layout/PortalLayout.vue');
  layout.computed.contentBusy = () => true;
  layout.computed.submittingContent = () => false;
  const covered = await html(layout, {});
  assert(/<main[^>]*\binert\b/.test(covered), 'Only main content becomes inert while loading');
  assert(!/<(?:header|aside)[^>]*\binert\b/.test(covered), 'Navigation and header remain usable');
  assert(/<\/main>\s*<div[^>]*portal-main-cover/.test(covered), 'Mask must be outside padded scrolling main');
  layout.computed.contentBusy = () => false;
  const idle = await html(layout, {});
  assert(!/<main[^>]*\binert\b/.test(idle), 'Completed loading must restore main interaction');
  console.log('Portal initial render: 12 pages hide provisional/failed branches; enterprise branches render only after confirmed data');
}

run().catch(error => { console.error(error); process.exitCode = 1; });
