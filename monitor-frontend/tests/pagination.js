/*
 * 直接读取真实组件的方法验证分页事件和各页面的参数传递，不连接后端。
 * 覆盖全部服务端分页及推广发送的独立本地分页，防止选择条数后仅改变外观而未改变数据。
 */
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const parser = require('@babel/parser');

/**
 * 从 Vue 脚本的默认导出提取真实方法或计算属性，避免加载路由、网络和视图依赖。
 * 文件路径及方法名由本测试提供，返回的方法保留原参数默认值和业务实现。
 */
function method(file, group, name) {
  const source = fs.readFileSync(path.join(__dirname, '../src', file), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
  const ast = parser.parse(source, { sourceType: 'module' });
  const component = ast.program.body.find(node => node.type === 'ExportDefaultDeclaration').declaration;
  const block = component.properties.find(node => node.key.name === group).value;
  const node = block.properties.find(node => node.key.name === name);
  return new Function(`return ({${source.slice(node.start, node.end)}}).${name}`)();
}

const emitted = [];
const pagination = { pageNo: 3, pageSize: 10, pageCount: 12, $emit: (...args) => emitted.push(args) };
const changeSize = method('components/AppPagination.vue', 'methods', 'changeSize');
for (const size of [20, 50, 100]) changeSize.call(pagination, String(size));
assert.deepStrictEqual(emitted, [['change', 1, 20], ['change', 1, 50], ['change', 1, 100]]);
for (const size of ['10', '0', '200', 'invalid']) changeSize.call(pagination, size);
assert.strictEqual(emitted.length, 3);
method('components/AppPagination.vue', 'methods', 'change').call(pagination, 4);
assert.deepStrictEqual(emitted[3], ['change', 4, 10]);
pagination.pageNo = 1;
changeSize.call(pagination, '20');
assert.deepStrictEqual(emitted[4], ['change', 1, 20]);

/*
 * 每个服务端页面必须同时更新页码和条数，仅发起一次查询，且保留原筛选条件。
 */
for (const [file, state] of [
  ['visitor/VisitorLeadPage.vue', null], ['user/UserListPage.vue', 'query'],
  ['system/SystemLogPage.vue', 'query'], ['reminder/ReminderListPage.vue', 'query'],
  ['promotion/PromotionTargetPage.vue', 'query'], ['promotion/PromotionSendPage.vue', 'query'],
  ['enterprise/EnterpriseFinancePage.vue', 'result'], ['enterprise/EnterpriseListPage.vue', 'query'],
  ['enterprise/EnterpriseMembersPage.vue', 'query']
]) {
  let loads = 0;
  const context = { pageNo: 3, pageSize: 10, load: () => loads++ };
  if (state) context[state] = { pageNo: 3, pageSize: 10, keyword: '保留筛选' };
  const change = method(`views/${file}`, 'methods', 'changePage');
  change.call(context, 1, 50);
  const params = state ? context[state] : context;
  assert.strictEqual(params.pageNo, 1, file);
  assert.strictEqual(params.pageSize, 50, file);
  assert.strictEqual(loads, 1, file);
  if (state) assert.strictEqual(params.keyword, '保留筛选');
  change.call(context, 2);
  assert.strictEqual(params.pageSize, 50, file);
}

/*
 * 本地导入和已选分页分别控制切片，调整一侧不会改变另一侧或清空接收人。
 */
const sendFile = 'views/promotion/PromotionSendPage.vue';
const rows = Array.from({ length: 125 }, (_, id) => ({ id }));
const local = { importRows: rows, selectedItems: rows, importPageNo: 3, selectedPageNo: 4, importPageSize: 10, selectedPageSize: 10 };
method(sendFile, 'methods', 'changeImportPage').call(local, 1, 50);
assert.strictEqual(method(sendFile, 'computed', 'importPageRows').call(local).length, 50);
assert.strictEqual(local.selectedPageSize, 10);
method(sendFile, 'methods', 'changeSelectedPage').call(local, 1, 20);
assert.strictEqual(method(sendFile, 'computed', 'selectedPageRows').call(local).length, 20);
assert.strictEqual(local.selectedItems, rows);
local.selectedPageNo = 9;
method(sendFile, 'methods', 'fixSelectedPage').call(local);
assert.strictEqual(local.selectedPageNo, 7);
console.log('All monitoring pagination checks passed');
