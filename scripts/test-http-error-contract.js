/*
 * 加载三个前端实际请求拦截器，用隔离的Axios及通知依赖模拟两类失败响应。
 * 不访问网络或修改用户会话，验证相同code、消息、附加数据和登录失效处理，避免仅测试复制的逻辑。
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');

/**
 * 仅替换模块导入导出语法以在Node沙箱运行真实拦截器；通知、存储、路由和Axios为可观察Mock。
 * 每次新建沙箱保证前一次401不影响下一次测试，返回拦截器与副作用记录。
 */
function load(relative) {
  const effects = [];
  let success;
  let failure;
  const axios = {
    defaults: {},
    interceptors: {
      request: { use() {} },
      response: { use(ok, bad) { success = ok; failure = bad; } }
    }
  };
  axios.create = () => axios;
  const context = {
    axios, process: { env: {} }, console, Promise, Blob,
    Message: { error: msg => effects.push(['error', msg]), warning: msg => effects.push(['warning', msg]) },
    Storage: { get() {}, set() {}, remove: key => effects.push(['remove', key]) },
    router: { push: url => effects.push(['route', url]) },
    notifyError: msg => effects.push(['error', msg]), notifyWarning: msg => effects.push(['warning', msg]),
    normalizeDateTimes: value => value, getToken() {}, updateToken() {}
  };
  const source = fs.readFileSync(path.join(__dirname, '..', relative), 'utf8')
    .replace(/^import .*;\r?\n/gm, '')
    .replace(/^export default .*;\r?$/gm, '')
    .replace(/\bexport (const|function) /g, '$1 ');
  vm.runInNewContext(source, context, { filename: relative });
  if (context.setUnauthorizedHandler) context.setUnauthorizedHandler(() => effects.push(['unauthorized']));
  return { success, failure, effects };
}

/**
 * 执行真实响应回调，断言失败一定进入catch而非继续成功流程，并采集跨端可比较的异常字段。
 */
async function errorOf(client, response, http) {
  try {
    await (http ? client.failure(Object.assign(new Error('axios failure'), { response, config: response.config })) : client.success(response));
    assert.fail('Failure response must reject');
  } catch (error) {
    assert.strictEqual(error.code, response.status === 503 ? 503 : Number(response.data.code >= 400 ? response.data.code : response.status));
    return { code: error.code, message: error.message, data: error.data, effects: client.effects };
  }
}

/**
 * 验证400/401/403/409/500/503两种传输方式等价，HTTP失败不能被code=200掩盖，门户免通知仍有效。
 * 成功响应保持各端原有外壳契约，避免统一错误处理误改正常业务返回结构。
 */
async function run() {
  for (const file of ['frontend/src/api/config.js', 'monitor-frontend/src/api/request.js', 'systemportal/src/api/request.js']) {
    for (const code of [400, 401, 403, 409, 500, 503]) {
      const response = { status: 200, data: { code, msg: '测试失败', data: { reason: 'test' } }, headers: {}, config: { url: '/private' } };
      const business = await errorOf(load(file), response, false);
      const http = await errorOf(load(file), { ...response, status: code }, true);
      assert.deepStrictEqual(business, http, `${file} ${code}`);
      if (code === 503) assert.strictEqual(http.message, '系统维护中，服务不可用');
      const bare = { ...response, status: code, data: {} };
      const bareResult = await errorOf(load(file), bare, true);
      const fallback = await errorOf(load(file), { ...response, data: { code } }, false);
      assert.deepStrictEqual(bareResult, fallback, `${file} bare HTTP ${code}`);
    }
    const masked = { status: 503, data: { code: 200 }, headers: {}, config: { url: '/private' } };
    assert.strictEqual((await errorOf(load(file), masked, true)).code, 503);
    const maintenance = load(file);
    const conflict = { ...masked, data: { code: 401, msg: '请登录' } };
    assert.strictEqual((await errorOf(maintenance, conflict, true)).message, '系统维护中，服务不可用');
    assert(!maintenance.effects.some(effect => ['unauthorized', 'route', 'remove'].includes(effect[0])));
    const ok = { status: 200, data: { code: 200, data: { ok: true } }, headers: {}, config: {} };
    const result = await load(file).success(ok);
    assert.strictEqual(file.startsWith('frontend/') ? result : file.startsWith('monitor-') ? result : result.data, file.startsWith('frontend/') ? ok : ok.data.data);
  }
  const quiet = load('systemportal/src/api/request.js');
  await errorOf(quiet, { status: 503, data: {}, config: { url: '/private', skipErrorNotification: true } }, true);
  assert.strictEqual(quiet.effects.length, 0);
  console.log('Three frontend HTTP/business error contracts passed');
}
run().catch(error => { console.error(error); process.exitCode = 1; });
