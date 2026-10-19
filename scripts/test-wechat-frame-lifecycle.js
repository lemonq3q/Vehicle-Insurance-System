const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { webcrypto } = require('node:crypto');

/**
 * 执行两端真实扫码组件的脚本，以可控制的 iframe 事件、请求和时钟模拟退出后再次打开。
 * 不请求微信或业务服务器，不消耗 OAuth state；验证组件状态，不模拟微信内部扫码成功。
 * @param {string} file 仓库内的 Vue 组件路径。
 * @returns {object} 组件实例、伪时钟和二维码事件控制器。
 */
function loadComponent(file) {
  let serial = 0;
  let state = 0;
  let immediateLoad = false;
  let requested;
  const timers = new Map();
  const listeners = new Set();
  const frames = [];
  const storage = new Map();
  const container = {
    replaceChildren() {},
    addEventListener(type, listener, capture) {
      assert.equal(type, 'load');
      assert.equal(capture, true);
      listeners.add(listener);
    },
    removeEventListener(type, listener) { listeners.delete(listener); },
    querySelector() { return frames.at(-1); }
  };
  const context = {
    module: { exports: {} }, console, crypto: webcrypto, TextEncoder, URL,
    process: { env: { BASE_URL: '/test/' } },
    document: { getElementById: () => container },
    sessionStorage: { setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) },
    startWechat: async () => {
      if (requested) await requested;
      const config = { state: `state-${++state}`, appId: 'test-app', redirectUri: 'https://example.com/callback', expiresInSeconds: 120 };
      return { data: file.startsWith('frontend/') ? { data: config } : config };
    },
    window: {
      location: { origin: 'https://example.com' },
      setTimeout(callback, delay) { timers.set(++serial, { callback, delay }); return serial; },
      clearTimeout(id) { timers.delete(id); },
      WxLogin: function () {
        const frame = { tagName: 'IFRAME' };
        frames.push(frame);
        if (immediateLoad) [...listeners].forEach(listener => listener({ target: frame }));
      }
    }
  };
  const source = fs.readFileSync(path.join(__dirname, '..', file), 'utf8')
    .split('<script>')[1].split('</script>')[0]
    .replace(/^import .*;\r?\n/gm, '')
    .replace('export default', 'module.exports =');
  vm.runInNewContext(source, context, { filename: file });
  const component = context.module.exports;
  const instance = { ...component.data(), binding: false, $nextTick: async () => {} };
  for (const [name, method] of Object.entries(component.methods)) instance[name] = method.bind(instance);
  return {
    instance, frames, listeners, timers,
    unmount: () => component.beforeUnmount.call(instance),
    immediate: () => { immediateLoad = true; },
    blockRequest: promise => { requested = promise; },
    emit: () => [...listeners].forEach(listener => listener({ target: frames.at(-1) })),
    tick(delay) {
      const entry = [...timers].find(([, timer]) => timer.delay === delay);
      assert(entry, `Expected ${delay} ms timer`);
      timers.delete(entry[0]);
      entry[1].callback();
    }
  };
}

/**
 * 覆盖快速加载、超时后成功、刷新交错、二维码过期和离开页面时请求未返回五种情况。
 * 两端执行同一组状态断言，防止只修复车险而门户绑定仍遗留相同缺陷。
 */
async function run() {
  for (const file of ['frontend/src/components/WechatLogin.vue', 'systemportal/src/components/WechatLogin.vue']) {
    const fast = loadComponent(file);
    fast.immediate();
    await fast.instance.refresh();
    assert.equal(fast.instance.frameReady, true, 'Listener must exist before SDK creates iframe');
    assert.equal(fast.instance.error, '');

    const slow = loadComponent(file);
    await slow.instance.refresh();
    slow.tick(15000);
    assert.match(slow.instance.error, /加载超时/);
    slow.emit();
    assert.equal(slow.instance.error, '', 'Late load must clear recoverable timeout');
    assert.equal(slow.instance.frameReady, true);

    const retry = loadComponent(file);
    await retry.instance.refresh();
    const staleListener = [...retry.listeners][0];
    await retry.instance.refresh();
    staleListener({ target: retry.frames[0] });
    assert.equal(retry.instance.frameReady, false, 'Old iframe must not reveal new QR');
    retry.emit();
    assert.equal(retry.instance.frameReady, true);
    retry.unmount();
    assert.equal(retry.listeners.size, 0);
    assert.equal(retry.timers.size, 0);

    const expired = loadComponent(file);
    await expired.instance.refresh();
    expired.tick(120000);
    expired.emit();
    assert.match(expired.instance.error, /已过期/);
    assert.equal(expired.listeners.size, 0);

    const leaving = loadComponent(file);
    let release;
    leaving.blockRequest(new Promise(resolve => { release = resolve; }));
    const pending = leaving.instance.refresh();
    leaving.unmount();
    release();
    await pending;
    assert.equal(leaving.frames.length, 0, 'Unmounted request must not create iframe');
    assert.equal(leaving.timers.size, 0);
    console.log(`${file}: 5 lifecycle cases passed`);
  }
}

run().catch(error => { console.error(error); process.exitCode = 1; });
