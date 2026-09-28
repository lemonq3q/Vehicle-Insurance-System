/*
 * 使用真实 Vue 组件的计算属性与格式化方法检查财务口径，不连接服务端或修改资金。
 * 覆盖各订单状态隔离、门户同款列及正金额/方向分离规则，防止回退为统一“处理中”。
 */
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const source = fs.readFileSync(path.join(__dirname, '../src/views/enterprise/EnterpriseFinancePage.vue'), 'utf8');
const script = source.match(/<script>([\s\S]*?)<\/script>/)[1].replace(/^import .*;\r?$/gm, '').replace('export default', 'return');
// 测试环境仅加载组件脚本，导入的视图组件不参与格式化逻辑。
const component = new Function('PageHeader', 'AppPagination', 'EnterpriseNav', 'ElConfigProvider', 'ElDatePicker', 'zhCn', script)({}, {}, {}, {}, {}, {});
const context = { active: 'recharge-orders', ...component.methods };
for (const [status, label] of [[1,'待支付'],[2,'已支付'],[3,'已取消'],[4,'支付失败'],[5,'已过期'],[6,'已关闭'],[7,'支付处理中'],[8,'已部分退款'],[9,'已完全退款']]) {
  assert.strictEqual(context.statusText({status}), label);
}
assert(!component.computed.columns.call(context).some(column => column.key === 'refundAmount'));
context.active = 'subscription-orders';
assert.strictEqual(context.statusText({status:4}), '已退款');
assert.strictEqual(context.statusText({status:5}), '已关闭');
assert.strictEqual(context.statusText({status:2}), '已支付');
assert.strictEqual(context.cellText({refundAmount:30,creditAmount:50}, 'adjustment'), '退款 ¥30.00');
assert.strictEqual(context.cellText({autoRenew:'0'}, 'autoRenew'), '否');
assert.strictEqual(context.cellText({autoRenew:1}, 'autoRenew'), '是');
context.active = 'wallet-transactions';
assert.strictEqual(context.cellText({direction:'OUT',amount:300}, 'amount'), '¥300.00');
assert.strictEqual(context.cellText({balanceAfter:-280}, 'balanceAfter'), '¥-280.00');
assert.strictEqual(context.cellText({transactionType:'REFUND'}, 'transactionType'), '退款');
assert.strictEqual(context.cellText({paidAt:null}, 'paidAt'), '-');
assert.strictEqual(context.cellText({createdAt:'2026-09-28T10:00:00'}, 'createdAt'), '2026-09-28 10:00:00');
console.log('Finance presentation checks passed');
