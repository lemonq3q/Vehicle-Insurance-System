const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const Vue = require('../frontend/node_modules/vue');
const { renderToString } = require('../frontend/node_modules/@vue/server-renderer');

/**
 * 执行车险真实回调组件及模板，用可控的 API 失败替身检查业务标题和实际渲染结果。
 * 不连接微信、不注册账号、不产生真实 JWT；记录存储和路由副作用，确保失败不建立登录态。
 * @param {object} options 接口失败、绑定用途或过期浏览器事务。
 * @returns {Promise<object>} 渲染 HTML、接口调用次数与登录副作用。
 */
async function runCase({ error, binding = false, expired = false, success = false }) {
  const source = fs.readFileSync(path.join(__dirname, '../frontend/src/page/WechatCallbackPage.vue'), 'utf8');
  const effects = [];
  let calls = 0;
  const context = {
    module: { exports: {} },
    window: { history: { state: null, replaceState() {} }, location: { pathname: '/wechat/callback' } },
    sessionStorage: {
      getItem: () => JSON.stringify({ binding, verifier: 'test-verifier', expiresAt: expired ? 0 : Date.now() + 60000 }),
      removeItem() {}
    },
    localStorage: { setItem: (...args) => effects.push(['local', ...args]) },
    Storage: { set: (...args) => effects.push(['storage', ...args]) },
    completeWechat: async () => {
      calls++;
      if (error) throw error;
      return { data: { data: { token: 'test-only-token', user: { id: 1 } } } };
    }
  };
  const script = source.split('<script>')[1].split('</script>')[0]
    .replace(/^import .*;\r?\n/gm, '').replace('export default', 'module.exports =');
  vm.runInNewContext(script, context);
  const component = context.module.exports;
  const state = component.data();
  const instance = Object.assign(state, {
    $route: { query: { code: 'test-code', state: 'test-state' } },
    $store: { commit: (...args) => effects.push(['commit', ...args]) },
    $router: { replace: url => effects.push(['route', url]) }
  });
  await component.mounted.call(instance);
  if (!success) assert.equal(effects.length, 0, 'Failed authorization must not write login state or navigate');
  const app = Vue.createSSRApp({ ...component, data: () => state, render: Vue.compile(source.split('<template>')[1].split('</template>')[0]) });
  app.component('router-link', { render: () => Vue.h('a') });
  return { html: await renderToString(app), state, calls, effects };
}

/**
 * 覆盖账号缺失、网关路径缺失、微信服务故障、授权过期、绑定冲突以及成功登录。
 * 关键断言是只有后端明确确认的缺失身份显示“账号不存在”，其他异常不得误导用户注册。
 */
async function run() {
  const absent = await runCase({ error: { code: 404, message: '账号不存在，请先前往企业门户注册或绑定微信' } });
  assert(absent.html.includes('<h1>账号不存在</h1>'));
  assert(absent.html.includes('车险系统不提供注册'));
  assert(absent.html.includes('已有邮箱账号登录并绑定微信'));
  const wrongPath = await runCase({ error: { code: 404, message: 'Not Found' } });
  assert(!wrongPath.html.includes('<h1>账号不存在</h1>'));
  for (const code of [502, 503]) {
    const service = await runCase({ error: { code, message: '微信授权服务暂不可用，请重试' } });
    assert(service.html.includes('<h1>微信登录服务暂不可用</h1>'));
    assert(!service.html.includes('账号不存在'));
  }
  const denied = await runCase({ error: { code: 403, message: '企业套餐不可用' } });
  assert(denied.html.includes('企业套餐不可用'));
  const expired = await runCase({ expired: true });
  assert.equal(expired.calls, 0);
  assert(expired.html.includes('授权已过期'));
  const binding = await runCase({ binding: true, error: { code: 409, message: '该微信已关联其他账号' } });
  assert(binding.html.includes('<h1>微信绑定失败</h1>'));
  assert(binding.html.includes('该微信已关联其他账号'));
  const success = await runCase({ success: true });
  assert.equal(success.state.error, '');
  assert(success.effects.some(item => item[0] === 'storage'));
  assert(success.effects.some(item => item[0] === 'route' && item[1] === '/home'));
  console.log('Insurance WeChat callback: 8 rendered success/error cases passed');
}

run().catch(error => { console.error(error); process.exitCode = 1; });
