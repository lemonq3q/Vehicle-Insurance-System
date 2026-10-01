const assert = require('assert');
const fs = require('fs');
const path = require('path');

/**
 * 无浏览器验证充值返回路径：创建/改订回订阅服务，订单详情回父列表。
 * 同时验证查询条件保留和非法来源兜底，不连接支付服务或修改业务数据。
 */
async function main() {
  const source = fs.readFileSync(path.join(__dirname, '../systemportal/src/utils/rechargeReturnPath.js'), 'utf8');
  const { normalizeRechargeReturnPath } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
  const names = {
    '/portal/finance/recharge': 'finance-recharge',
    '/portal/finance/subscription/order/1': 'finance-subscription-order-detail',
    '/portal/finance/orders/1': 'finance-order-detail',
    '/portal/finance/recharges/1': 'finance-recharge-detail',
    '/portal/finance/orders': 'finance-orders',
    '/portal/finance/recharges': 'finance-recharges',
    '/portal/finance/subscription': 'finance-subscription'
  };
  const router = { resolve: fullPath => ({ fullPath, name: names[fullPath.split('?')[0]], matched: names[fullPath.split('?')[0]] ? [{}] : [] }) };
  assert.strictEqual(normalizeRechargeReturnPath('/portal/finance/recharge?planId=1', router), '/portal/finance/subscription');
  assert.strictEqual(normalizeRechargeReturnPath('/portal/finance/subscription/order/1', router), '/portal/finance/subscription');
  assert.strictEqual(normalizeRechargeReturnPath('/portal/finance/orders/1', router), '/portal/finance/orders');
  assert.strictEqual(normalizeRechargeReturnPath('/portal/finance/orders?page=2', router), '/portal/finance/orders?page=2');
  assert.strictEqual(normalizeRechargeReturnPath('/portal/finance/recharges/1', router), '/portal/finance/recharges');
  for (const invalid of ['https://example.com', '/portal/missing', undefined]) {
    assert.strictEqual(normalizeRechargeReturnPath(invalid, router), '/portal/finance/recharges');
  }
  console.log('Recharge return path checks passed.');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
