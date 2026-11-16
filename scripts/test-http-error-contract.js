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
    normalizeDateTimes: value => value, getToken() {}, updateToken() {},
    Loading: { open: () => () => {} }
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
    assert.strictEqual(error.code, response.status >= 500 ? response.status : Number(response.data.code >= 400 ? response.data.code : response.status));
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
      assert.strictEqual(http.message, '测试失败');
      const notices = http.effects.filter(effect => ['warning', 'error'].includes(effect[0]));
      assert.strictEqual(notices.length, 1, `${file} ${code} must notify once`);
      assert.strictEqual(notices[0][0], code >= 500 ? 'error' : 'warning');
      const bare = { ...response, status: code, data: {} };
      const bareResult = await errorOf(load(file), bare, true);
      const fallback = await errorOf(load(file), { ...response, data: { code } }, false);
      assert.deepStrictEqual(bareResult, fallback, `${file} bare HTTP ${code}`);
    }
    const masked = { status: 503, data: { code: 200 }, headers: {}, config: { url: '/private' } };
    assert.strictEqual((await errorOf(load(file), masked, true)).code, 503);
    const maintenance = load(file);
    const conflict = { ...masked, data: { code: 401, msg: '请登录' } };
    assert.strictEqual((await errorOf(maintenance, conflict, true)).message, '请登录');
    assert(!maintenance.effects.some(effect => ['unauthorized', 'route', 'remove'].includes(effect[0])));
    for (const code of [400, 500, 503]) {
      const quiet = load(file);
      await errorOf(quiet, { status: 200, data: { code, msg: '自主处理' }, headers: {}, config: { url: '/private', skipErrorNotification: true } }, false);
      assert.strictEqual(quiet.effects.filter(effect => ['warning', 'error'].includes(effect[0])).length, 0);
    }
    const binary = load(file);
    const blobError = { status: 200, data: new Blob([JSON.stringify({ code: 400, msg: '导出参数不合法' })], { type: 'application/json' }), headers: { 'content-type': 'application/json' }, config: { url: '/export', responseType: 'blob' } };
    await errorOf(binary, blobError, false);
    assert.deepStrictEqual(binary.effects.filter(effect => ['warning', 'error'].includes(effect[0])), [['warning', '导出参数不合法']]);
    const network = load(file);
    await network.failure(Object.assign(new Error('network down'), { config: {} })).catch(error => {
      assert.strictEqual(error.notificationHandled, true);
    });
    assert.strictEqual(network.effects.filter(effect => effect[0] === 'error').length, 1);
    const ok = { status: 200, data: { code: 200, data: { ok: true } }, headers: {}, config: {} };
    const result = await load(file).success(ok);
    assert.strictEqual(file.startsWith('frontend/') ? result : file.startsWith('monitor-') ? result : result.data, file.startsWith('frontend/') ? ok : ok.data.data);
  }
  // 使用监控端实际反馈 mixin 验证回调不会重复显示请求层已通知的异常。
  const mixinSource = fs.readFileSync(path.join(__dirname, '../monitor-frontend/src/mixins/feedback.js'), 'utf8').replace('export default', 'this.feedback =');
  const mixinContext = {};
  vm.runInNewContext(mixinSource, mixinContext);
  const emitted = [];
  const owner = { notify: (...args) => emitted.push(args) };
  mixinContext.feedback.methods.errorMessage.call(owner, { code: 400, message: '状态不允许', notificationHandled: true });
  assert.strictEqual(emitted.length, 0);
  mixinContext.feedback.methods.errorMessage.call(owner, { code: 400, message: '自主提示' });
  assert.deepStrictEqual(emitted, [['自主提示', 'warning']]);
  // 执行真实上传签名封装，确保静默标志位于 Axios 真正读取的第三个 config 参数中。
  const uploadCalls = [];
  const uploadContext = { axios: { post: (...args) => uploadCalls.push(args) }, axiosLib: { create: () => ({}) } };
  const uploadSource = fs.readFileSync(path.join(__dirname, '../frontend/src/api/file.js'), 'utf8')
    .replace(/^import .*;?\r?\n/gm, '').replace(/^export /gm, '');
  vm.runInNewContext(uploadSource, uploadContext);
  uploadContext.createOssUploadSign({ fileName: 'test.pdf', suffix: 'pdf', size: 1, mimeType: 'application/pdf' });
  assert.strictEqual(uploadCalls[0].length, 3);
  assert.strictEqual(uploadCalls[0][2].skipErrorNotification, true);
  console.log('Three frontend HTTP/business error contracts passed');
}
run().catch(error => { console.error(error); process.exitCode = 1; });
