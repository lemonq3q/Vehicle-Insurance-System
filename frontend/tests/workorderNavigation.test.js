const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const Vue = require('vue');
const VueRouter = require('vue-router');
const { parse, compileScript } = require('@vue/compiler-sfc');
const context = { module: { exports: {} }, inject: Vue.inject, onActivated: Vue.onActivated };
const navigationSource = fs.readFileSync(path.join(__dirname, '../src/utils/workorderNavigation.js'), 'utf8')
  .replace(/^import .*;\r?$/gm, '').replace(/export (function|const) /g, '$1 ');
vm.runInNewContext(`${navigationSource}\nmodule.exports = { WORKORDER_NAVIGATION_KEY, WORKORDER_LIST_COMPONENTS,
  workorderReturnTarget, returnToWorkorderList, captureWorkorderScroll, restoreWorkorderScroll, useWorkorderListRefresh };`, context);
const navigation = context.module.exports;

/** 校验来源白名单与返回历史策略，直接链接详情不得跳出系统或无限往返详情。 */
test('return chooses the known source history and rejects external or unrelated targets', () => {
  for (const route of Object.keys(navigation.WORKORDER_LIST_COMPONENTS)) {
    assert.equal(navigation.workorderReturnTarget(`${route}?page=3`), `${route}?page=3`);
  }
  for (const value of [null, undefined, [], '/login', 'https://other.example/home/allWorkorder', '//other.example']) {
    assert.equal(navigation.workorderReturnTarget(value), '/home/allWorkorder');
  }
  const calls = [];
  const router = { options: { history: { state: { back: '/home/allWorkorder' } } },
    back: () => calls.push('back'), replace: target => calls.push(target) };
  navigation.returnToWorkorderList(router, '/home/allWorkorder');
  assert.equal(calls.pop(), 'back');
  router.options.history.state.back = '/login';
  navigation.returnToWorkorderList(router, '/home/dispatchWorkOrder');
  assert.equal(calls.pop(), '/home/dispatchWorkOrder');
});

/** 恢复实际main及Element Plus表格内部横纵滚动，不使用window滚动代替业务视窗。 */
test('scroll snapshots restore main and internal table positions independently', () => {
  const table = { scrollTop: 8, scrollLeft: 360 };
  const main = { scrollTop: 720, scrollLeft: 10, querySelectorAll: () => [table] };
  const snapshot = navigation.captureWorkorderScroll(main);
  main.scrollTop = 0; table.scrollLeft = 0;
  navigation.restoreWorkorderScroll(main, snapshot);
  assert.equal(main.scrollTop, 720);
  assert.equal(main.scrollLeft, 10);
  assert.equal(table.scrollLeft, 360);
  assert.equal(table.scrollTop, 8);
  navigation.restoreWorkorderScroll(main, null);
  assert.equal(main.scrollTop, 0);
  assert.equal(table.scrollLeft, 0);
});

/** 执行四个列表真实查询函数，验证保存详情后的刷新复用已提交查询，不误提交正在输入的新筛选。 */
test('all workorder lists refresh the last applied query and pass their source to detail', async () => {
  for (const name of ['AllWorkOrder', 'AcceptWorkOrder', 'DispatchWorkOrder', 'RenewWorkorder']) {
    const source = fs.readFileSync(path.join(__dirname, `../src/components/${name}.vue`), 'utf8');
    const { descriptor } = parse(source, { filename: `${name}.vue` });
    assert.ok(compileScript(descriptor, { id: name }).content.includes(`__name: '${name}'`), `${name} must match KeepAlive include`);
    const requests = [];
    let keyword = '原筛选'; let destination;
    const query = params => {
      requests.push(params);
      return Promise.resolve({ data: { code: 200, data: { total: 50, table: [] } } });
    };
    const sandbox = { lastSearchParams: null, buildSearchParams: () => ({ blurParam: keyword, pageNum: 5 }),
      selectWorkorder: query, selectRenew: query, loading: {}, page: {}, buildTableData() {}, console: { log() {} },
      sessionStorage: { setItem() {} }, router: { currentRoute: { value: { fullPath: '/home/allWorkorder?tab=current' } },
        push(value) { destination = value; } } };
    vm.runInNewContext(`${source.match(/const getData = [\s\S]*?\n}/)[0]}
      ${source.match(/const handleDetail = [\s\S]*?\n};/)[0]}
      runQuery = getData; openDetail = handleDetail;`, sandbox);
    await sandbox.runQuery();
    keyword = '尚未提交的输入';
    await sandbox.runQuery(true);
    assert.equal(requests[1].blurParam, '原筛选', name);
    assert.equal(requests[1].pageNum, 5, name);
    await sandbox.runQuery();
    assert.equal(requests[2].blurParam, keyword, name);
    sandbox.openDetail(0, { id: '9007199254740999' });
    assert.equal(destination.query.id, '9007199254740999', name);
    assert.equal(destination.query.returnTo, '/home/allWorkorder?tab=current', name);
  }
});

/**
 * 编译真实HomePage的script setup与模板，依赖使用真实Vue/Router及导航工具，只有无关头部视图为占位。
 * @returns {object} 可挂载到隔离渲染器的真实工作台组件。
 */
function homeComponent() {
  const { descriptor } = parse(fs.readFileSync(path.join(__dirname, '../src/page/HomePage.vue'), 'utf8'));
  const compiled = compileScript(descriptor, { id: 'navigation-test', inlineTemplate: true }).content;
  const imports = [];
  const placeholder = { render: () => Vue.h('div') };
  const source = compiled.replace(/import\s+([\s\S]*?)\s+from\s+['"]([^'"]+)['"];?/g, (_, names, file) => {
    const imported = file === 'vue' ? Vue : file === 'vue-router' ? VueRouter
      : file.includes('workorderNavigation') ? navigation : file.includes('assets/') ? '' : placeholder;
    const index = imports.push(imported) - 1;
    return names.startsWith('{')
      ? `const ${names.replace(/\s+as\s+/g, ': ')} = imports[${index}];`
      : `const ${names.trim()} = imports[${index}];`;
  }).replace('export default', 'return');
  return new Function('imports', source)(imports);
}

/**
 * 创建无需浏览器的Vue宿主，保留节点移动/卸载和滚动属性以执行真实KeepAlive生命周期。
 * @returns {object} 渲染器及根节点；没有网络、持久化存储或真实业务数据。
 */
function isolatedRenderer() {
  const node = type => ({ type, children: [], parent: null, scrollTop: 0, scrollLeft: 0, querySelectorAll: () => [] });
  const detach = child => {
    if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1);
    child.parent = null;
  };
  return { root: node('root'), renderer: Vue.createRenderer({
    createElement: node, createText: text => ({ ...node('text'), text }), createComment: text => ({ ...node('comment'), text }),
    insert(child, parent, anchor = null) { detach(child); child.parent = parent;
      const index = anchor ? parent.children.indexOf(anchor) : -1;
      if (index < 0) parent.children.push(child); else parent.children.splice(index, 0, child); },
    remove: detach, setText(child, text) { child.text = text; }, setElementText(child, text) { child.text = text; },
    parentNode: child => child.parent, nextSibling: child => child.parent?.children[child.parent.children.indexOf(child) + 1] || null,
    patchProp(child, key, oldValue, value) { child[key] = value; }
  }) };
}

/** 在渲染树中寻找真实main节点，模拟其本地滚动而不依赖页面window。 */
function findMain(node) {
  if (node.type === 'main') return node;
  return node.children.map(findMain).find(Boolean);
}

/**
 * 实际导航穿过HomePage、RouterView与KeepAlive，验证筛选/页码/行数据保留、详情不缓存、保存后刷新及退出释放。
 * 列表仅用隔离数据代替接口，缓存、路由、导航生命周期及滚动恢复均运行真实实现。
 */
test('real workbench retains list state, restores scrolling and refreshes only after detail saves', async () => {
  let state; let mounted = 0; let refreshed = 0; let disposed = 0; let markChanged; let pauseRefresh;
  const list = Vue.defineComponent({ name: 'AllWorkOrder', setup() {
    state = Vue.reactive({ keyword: '', page: 1, rows: ['original'] });
    Vue.onMounted(() => mounted++);
    Vue.onUnmounted(() => disposed++);
    navigation.useWorkorderListRefresh(async () => { refreshed++; if (pauseRefresh) await pauseRefresh; state.rows = ['updated']; });
    return () => Vue.h('section', state.keyword);
  } });
  const detail = Vue.defineComponent({ setup() {
    const current = Vue.inject(navigation.WORKORDER_NAVIGATION_KEY);
    markChanged = () => current.revision.value++;
    return () => Vue.h('article', 'detail');
  } });
  const router = VueRouter.createRouter({ history: VueRouter.createMemoryHistory(), routes: [
    { path: '/home', component: homeComponent(), children: [
      { path: 'allWorkorder', component: list }, { path: 'detailWorkorder', component: detail }
    ] }, { path: '/login', component: { render: () => Vue.h('div') } }
  ] });
  const { renderer, root } = isolatedRenderer();
  const app = renderer.createApp({ render: () => Vue.h(VueRouter.RouterView) });
  app.use(router);
  await router.push('/home/allWorkorder');
  app.mount(root);
  await Vue.nextTick();
  const originalState = state;
  state.keyword = '京A'; state.page = 5;
  findMain(root).scrollTop = 640;
  await router.push('/home/detailWorkorder?id=1&returnTo=/home/allWorkorder');
  await Vue.nextTick();
  assert.equal(findMain(root).scrollTop, 0);
  await router.push('/home/allWorkorder');
  await Vue.nextTick();
  assert.equal(state, originalState);
  assert.equal(state.keyword, '京A'); assert.equal(state.page, 5);
  assert.equal(state.rows[0], 'original');
  assert.equal(mounted, 1); assert.equal(refreshed, 0);
  assert.equal(findMain(root).scrollTop, 640);
  await router.push('/home/detailWorkorder?id=2&returnTo=/home/allWorkorder');
  markChanged();
  await router.push('/home/allWorkorder');
  await Vue.nextTick(); await Vue.nextTick();
  assert.equal(state.rows[0], 'updated'); assert.equal(refreshed, 1);
  assert.equal(state.page, 5); assert.equal(findMain(root).scrollTop, 640);
  await router.push('/home/detailWorkorder?id=3&returnTo=/home/allWorkorder');
  markChanged();
  let finishRefresh;
  pauseRefresh = new Promise(resolve => { finishRefresh = resolve; });
  await router.push('/home/allWorkorder');
  await router.push('/home/detailWorkorder?id=4&returnTo=/home/allWorkorder');
  await Vue.nextTick(); await Vue.nextTick();
  findMain(root).scrollTop = 320;
  finishRefresh();
  await Vue.nextTick(); await Vue.nextTick();
  assert.equal(findMain(root).scrollTop, 320, 'late list refresh must not scroll a newer detail page');
  await router.push('/login');
  await Vue.nextTick();
  assert.equal(disposed, 1);
  app.unmount();
});
