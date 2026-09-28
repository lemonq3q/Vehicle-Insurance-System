/*
 * 使用实际Mock Adapter及页面脚本验证生效筛选与默认值，覆盖旧生效、失效和已处理记录。
 * 不连接后端、不修改数据库，确保前端查询参数与分页响应契约一致。
 */
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const babel = require('@babel/core');
const vm = require('vm');

/**
 * 以现有Babel依赖加载Mock模块，把相对import映射回真实源码，不引入额外测试运行器。
 */
function moduleAt(relative) {
  const filename = path.join(__dirname, '../src', relative);
  const source = fs.readFileSync(filename, 'utf8');
  const code = babel.transformSync(source, { configFile: false, babelrc: false, plugins: ['@babel/plugin-transform-modules-commonjs'] }).code;
  const exports = {};
  vm.runInNewContext(code, { exports, require: name => moduleAt(path.relative(path.join(__dirname, '../src'), path.resolve(path.dirname(filename), `${name}.js`))), setTimeout, Date, Promise, console });
  return exports;
}

/**
 * 默认查询仅生效，失效和全部条件可独立切换；一月前仍生效的示例不能被隐藏。
 */
async function run() {
  const { mockAdapter } = moduleAt('mock/adapter.js');
  const query = params => mockAdapter({ url: '/reminders', method: 'GET', params }).then(response => response.data.data);
  assert.strictEqual((await query({})).total, 2);
  assert.strictEqual((await query({ isActive: 0 })).total, 1);
  assert.strictEqual((await query({ isActive: -1 })).total, 3);
  const data = await query({ isActive: 1, processStatus: 0 });
  assert.strictEqual(data.list[0].lastTriggeredAt, '2026-07-01T04:00:00');
  assert.strictEqual(data.total, 1);
  const source = fs.readFileSync(path.join(__dirname, '../src/views/reminder/ReminderListPage.vue'), 'utf8');
  assert(source.includes("isActive: '1'"));
  assert(source.includes('v-model="query.isActive"'));
  console.log('Reminder lifecycle frontend checks passed');
}
run().catch(error => { console.error(error); process.exitCode = 1; });
