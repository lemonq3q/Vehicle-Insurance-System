const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const axios = require('../systemportal/node_modules/axios/dist/node/axios.cjs');
const { reactive, ref, watch, nextTick } = require('../systemportal/node_modules/vue');

/**
 * 读取真实门户模块，在 Node 中只替换导入导出并提供可观察依赖。
 * 不访问业务服务器或微信，不修改数据；SFC 仅执行脚本以验证弹层的状态收尾。
 * @param {string} file 仓库相对文件路径。
 * @param {object} dependencies 模块依赖与测试时钟。
 * @returns {object} 模块导出及辅助状态。
 */
function load(file, dependencies) {
  let source = fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
  if (file.endsWith('.vue')) source = source.split('<script>')[1].split('</script>')[0];
  const namedExports = [...source.matchAll(/export (?:const|function) (\w+)/g)].map(match => match[1]);
  source = source.replace(/^import .*;\r?\n/gm, '')
    .replace(/export (const|function) /g, '$1 ')
    .replace(/export default /g, 'module.exports = ');
  if (namedExports.length) source += `\nObject.assign(module.exports, { ${namedExports.join(', ')} });`;
  const context = { module: { exports: {} }, process: { env: {} }, ...dependencies };
  vm.runInNewContext(source, context, { filename: file });
  return context.module.exports;
}

/**
 * 提供可手动推进的 160ms 延迟和最小 DOM，验证局部遮罩数量与卸载清理。
 * DOM 对象仅记录属性、子节点和 class，不复制生产加载逻辑。
 */
function browser() {
  let serial = 0;
  const timers = new Map();
  /** 创建能够记录父子关系与辅助属性的容器，供真实指令增删遮罩。 */
  function element() {
    const classes = new Set();
    const attributes = new Map();
    return {
      children: [], parent: null,
      classList: {
        add: (...names) => names.forEach(name => classes.add(name)),
        remove: (...names) => names.forEach(name => classes.delete(name)),
        toggle: (name, active) => active ? classes.add(name) : classes.delete(name),
        contains: name => classes.has(name)
      },
      setAttribute: (key, value) => attributes.set(key, value),
      getAttribute: key => attributes.get(key) ?? null,
      removeAttribute: key => attributes.delete(key),
      append(...nodes) { nodes.forEach(node => { node.remove(); node.parent = this; this.children.push(node); }); },
      remove() { if (this.parent) this.parent.children = this.parent.children.filter(node => node !== this); this.parent = null; }
    };
  }
  return {
    element, timers,
    document: { createElement: () => element() },
    window: { setTimeout: callback => { timers.set(++serial, callback); return serial; }, clearTimeout: id => timers.delete(id) },
    tick() { for (const [id, callback] of [...timers]) { timers.delete(id); callback(); } }
  };
}

/**
 * 验证默认不全屏、区域隔离、父级合并、防闪烁、提交模态层和请求失败清理。
 * 使用真实 Axios 调度和 Vue 观察器，确保请求与 UI 之间的配置契约一致。
 */
async function run() {
  const Loading = load('systemportal/src/utils/loading.js', { reactive });
  const { loadingState, isRegionLoading } = Loading;
  Loading.open()();
  assert.equal(loadingState.fullscreen, 0);
  const first = Loading.open({ scope: 'members' });
  const second = Loading.open({ scope: 'members' });
  assert(isRegionLoading('members'));
  assert(!isRegionLoading('dashboard'));
  assert.equal(loadingState.fullscreen, 0, 'Queries must never open fullscreen');
  first(); first();
  assert(isRegionLoading('members'));
  second();
  assert(!isRegionLoading('members'));

  const dom = browser();
  const directive = load('systemportal/src/directives/regionLoading.js', { ref, watch, isRegionLoading, loadingState, ...dom });
  const parent = dom.element();
  directive.mounted(parent, { value: ['overview', 'plans'] });
  const overview = Loading.open({ scope: 'overview' });
  const plans = Loading.open({ scope: 'plans' });
  await nextTick();
  assert.equal(parent.children.length, 0, 'Fast loads must not flash');
  dom.tick();
  assert.equal(parent.children.length, 1, 'Multiple child datasets share one parent mask');
  overview();
  await nextTick();
  assert.equal(parent.children.length, 1);
  const submit = Loading.open({ fullscreen: true });
  await nextTick();
  assert.equal(parent.children.length, 0, 'Fullscreen submission suppresses duplicate local spinners');
  submit();
  await nextTick(); dom.tick();
  assert.equal(parent.children.length, 1);
  plans(); await nextTick();
  assert.equal(parent.children.length, 0);
  directive.updated(parent, { value: { busy: true, compact: true } });
  await nextTick();
  assert(parent.classList.contains('portal-loading-compact'));
  directive.beforeUnmount(parent);
  dom.tick();
  assert.equal(parent.children.length, 0);
  assert.equal(parent.getAttribute('aria-busy'), null);

  const mainOne = dom.element();
  const mainTwo = dom.element();
  directive.mounted(mainOne, { value: { main: true, busy: true } });
  directive.mounted(mainTwo, { value: { main: true, busy: true } });
  await nextTick(); dom.tick();
  assert.equal(loadingState.main, 2);
  assert.equal(mainOne.children.length, 0, 'Frameless groups must not append rectangular masks');
  directive.beforeUnmount(mainOne);
  assert.equal(loadingState.main, 1);
  directive.beforeUnmount(mainTwo);
  assert.equal(loadingState.main, 0, 'Leaving the page must release its main loading ownership');

  let open = false;
  const instance = {
    busy: true, timer: null,
    $refs: { mask: { get open() { return open; }, showModal() { open = true; }, close() { open = false; } } }
  };
  /* 使用受控时钟验证原生顶层遮罩的延迟显示与结束收尾，不依赖浏览器动画时长。 */
  const modalHost = load('systemportal/src/components/SubmitLoading.vue', { loadingState, window: dom.window });
  modalHost.methods.syncVisibility.call(instance, true);
  assert(!open);
  const releaseSubmit = Loading.open({ fullscreen: true });
  assert.equal(modalHost.computed.busy.call({ $route: { path: '/portal/user/profile' } }), false);
  assert.equal(modalHost.computed.busy.call({ $route: { path: '/login' } }), true);
  releaseSubmit();
  dom.tick(); assert(open);
  instance.busy = false;
  modalHost.methods.syncVisibility.call(instance, false);
  assert(!open);

  const request = load('systemportal/src/api/request.js', {
    axios, Loading, Storage: { get() {}, set() {}, remove() {} },
    normalizeDateTimes: value => value, notifyError() {}, notifyWarning() {}
  });
  const pending = [];
  request.defaults.adapter = config => new Promise((resolve, reject) => pending.push({ config, resolve, reject }));
  /** 发起真实 Axios 调用，提前接收拒绝，返回原请求配置以模拟响应或网络错误。 */
  async function begin(loading) {
    const outcome = request.get('/portal/test', { loading }).then(value => ({ value }), error => ({ error }));
    await new Promise(resolve => setImmediate(resolve));
    return { job: pending.shift(), outcome };
  }
  /** 在保留配置身份的前提下完成业务响应，以验证错误通知与加载收尾互不干扰。 */
  function respond(job, code = 200) { job.resolve({ status: 200, headers: {}, config: job.config, data: { code } }); }
  const unconfigured = await begin();
  assert.equal(loadingState.fullscreen, 0);
  respond(unconfigured.job); await unconfigured.outcome;
  for (const policy of [{ scope: 'data' }, { fullscreen: true }]) {
    for (const code of ['ECONNABORTED', 'ERR_NETWORK', 'ERR_CANCELED']) {
      const task = await begin(policy);
      task.job.reject(new axios.AxiosError('模拟加载失败', code, task.job.config));
      assert((await task.outcome).error);
      assert.equal(loadingState.fullscreen, 0);
      assert.equal(Object.keys(loadingState.regions).length, 0);
    }
    const task = await begin(policy);
    respond(task.job, 400);
    assert.equal((await task.outcome).error.code, 400);
    assert.equal(loadingState.fullscreen, 0);
    assert.equal(Object.keys(loadingState.regions).length, 0);
    const http = await begin(policy);
    http.job.reject(new axios.AxiosError('失败', 'ERR_BAD_RESPONSE', http.job.config, null, { status: 503, data: {}, config: http.job.config }));
    assert.equal((await http.outcome).error.code, 503);
    assert.equal(loadingState.fullscreen, 0);
    assert.equal(Object.keys(loadingState.regions).length, 0);
  }
  const api = load('systemportal/src/api/portal.js', { request: {
    get: (url, config) => config, post: (url, data, config) => config,
    put: (url, data, config) => config, delete: (url, config) => config
  } });
  assert.equal(api.getDashboardStatistics().loading.scope, api.getRecentReminders().loading.scope);
  assert.equal(api.getMembers({}).loading.scope, api.getMemberChangeLogs({}).loading.scope);
  for (const config of [api.sendEmailCode({}), api.getSubscriptionOrderPreview({}), api.createStripeRechargeCheckoutSession(1), api.getAccountContext()]) {
    assert(config.loading.scope);
    assert.notEqual(config.loading.fullscreen, true, 'Minor actions must stay local');
  }
  for (const config of [api.login({}), api.updateProfile({}), api.createSubscriptionOrder({}), api.createVisitorLead({})]) {
    assert.equal(config.loading.fullscreen, true, 'Important submissions explicitly request fullscreen');
  }
  console.log('Portal loading: local grouping, isolation, delay, modal submission, unmount and error cleanup passed');
}

run().catch(error => { console.error(error); process.exitCode = 1; });
