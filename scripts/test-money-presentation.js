const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { parse } = require('../frontend/node_modules/@vue/compiler-sfc');
const moneySource = fs.readFileSync(path.join(__dirname, '../shared/money.js'), 'utf8');
const money = import(`data:text/javascript;base64,${Buffer.from(moneySource).toString('base64')}`);

/**
 * 加载真实页面脚本，以真实金额工具替换其导入，其他视图与网络依赖仅提供空壳。
 * 不触发页面生命周期，不创建订单或修改资金，返回可直接调用的methods/computed。
 * @param {string} relative 仓库内Vue组件路径。
 * @returns {Promise<object>} 页面或图表的真实组件定义。
 */
async function component(relative) {
  const helpers = await money;
  const context = { module: { exports: {} }, ...helpers, formatAmount: helpers.formatMoney };
  const { descriptor } = parse(fs.readFileSync(path.join(__dirname, '..', relative), 'utf8'));
  const source = descriptor.script.content.replace(/import\s+([\s\S]*?)\s+from\s+['"][^'"]+['"];?/g, (_, names) => {
    const entries = names.startsWith('{') ? names.slice(1, -1).split(',') : [names];
    for (const entry of entries) {
      const name = entry.trim().split(/\s+as\s+/).pop();
      if (!(name in context)) context[name] = name === 'echarts' ? { use() {} } : () => {};
    }
    return '';
  }).replace('export default', 'module.exports =');
  vm.runInNewContext(source, context, { filename: relative });
  return context.module.exports;
}

/** 检查门户所有财务页面及监控财务页面实际方法，避免仍通过Number丢失字符串分位精度。 */
test('portal and monitor money methods preserve decimal strings and HALF_UP boundaries', async () => {
  const pages = [
    'systemportal/src/views/enterprise/EnterpriseInfoPage.vue',
    ...['RechargeOrderDetail', 'RechargeOrders', 'RechargePlaceholder', 'SubscriptionOrderDetail',
      'SubscriptionOrderRecordDetail', 'SubscriptionOrders', 'SubscriptionService', 'WalletTransactions']
      .map(name => `systemportal/src/views/finance/${name}Page.vue`),
    'monitor-frontend/src/views/enterprise/EnterpriseFinancePage.vue',
    'monitor-frontend/src/views/plan/PlanListPage.vue'
  ];
  for (const page of pages) {
    const { methods } = await component(page);
    const prefix = page.includes('EnterpriseFinance') ? '¥' : '';
    assert.equal(methods.money('1.005'), `${prefix}1.01`, page);
    assert.equal(methods.money(258.58000000000004), `${prefix}258.58`, page);
    assert.equal(methods.money('90071992547409.91'), `${prefix}90${page.includes('PlanList') ? ',071,992,547,409' : '071992547409'}.91`, page);
    assert.equal(methods.money(null), `${prefix}0.00`, page);
  }
});

/** 分组及官网省略整数小数位仅改变外观，不得把精确字符串转为Number。 */
test('dashboards, enterprise balances and marketing retain precise grouped amounts', async () => {
  for (const page of ['systemportal/src/views/DashboardPage.vue', 'monitor-frontend/src/views/DashboardPage.vue',
    'monitor-frontend/src/views/enterprise/EnterpriseListPage.vue', 'monitor-frontend/src/views/enterprise/EnterpriseDetailPage.vue']) {
    const { methods } = await component(page);
    const fn = methods.money || methods.formatMoney;
    assert.equal(fn('90071992547409.91').replace(/¥\s*/, ''), '90,071,992,547,409.91', page);
  }
  const { methods } = await component('systemportal/src/views/MarketingPage.vue');
  assert.equal(methods.money('1000'), '1,000');
  assert.equal(methods.money('1000.005'), '1,000.01');
});

/** 补充充值缺口来自权威试算和余额，验证一分钱临界值、负余额与余额充足分支及路由传值。 */
test('subscription shortfall subtracts exactly and clamps only genuine negative results', async () => {
  const { computed } = await component('systemportal/src/views/finance/SubscriptionOrderDetailPage.vue');
  for (const [required, balance, expected] of [['0.30', '0.10', '0.20'], ['0.29', '0.28', '0.01'],
    ['10.00', '-0.01', '10.01'], ['1.00', '2.00', '0.00']]) {
    const context = { preview: { payableAmount: required }, overview: { wallet: { balanceAmount: balance } } };
    context.shortfallAmount = computed.shortfallAmount.call(context);
    assert.equal(context.shortfallAmount, expected);
    assert.equal(computed.isBalanceInsufficient.call(context), Number(expected) > 0);
  }
});

/** 金额图表必须对提示与坐标轴显式格式化，数量图表不能被误加货币符号。 */
test('money chart tooltips and axes suppress long tails without changing count charts', async () => {
  const { methods } = await component('monitor-frontend/src/components/LineChart.vue');
  let option;
  const context = { money: true, labels: [], series: [], type: 'bar', chart: { setOption(value) { option = value; } } };
  methods.render.call(context);
  assert.equal(option.tooltip.valueFormatter(258.58000000000004), '¥258.58');
  assert.equal(option.yAxis.axisLabel.formatter('1000.005'), '1,000.01');
  context.money = false;
  methods.render.call(context);
  assert.equal(option.tooltip.valueFormatter, undefined);
});

/** 不新增线上接口，直接复用财务响应样例字段验证单位金额乘数量、周期折算及比例输出。 */
test('shared decimal helpers cover mock proration, signed amounts and percentage display', async () => {
  const { formatMoney, multiplyMoney, moneyShortfall, formatPercentage, compareMoney } = await money;
  assert.equal(formatMoney('-1234567.895', '', { group: true }), '-1,234,567.90');
  assert.equal(multiplyMoney('0.335', 3), '1.01');
  assert.equal(multiplyMoney('10.05', 1, 10), '1.01');
  assert.equal(multiplyMoney('10', 1, 0), '');
  assert.equal(moneyShortfall(null, null), '0.00');
  assert.equal(formatPercentage(10.100000000000001), '10.1');
  assert.equal(formatPercentage(0), '0');
  assert.equal(formatPercentage('0.125'), '0.13');
  assert.equal(compareMoney('90071992547409.91', '90071992547409.90'), 1);
  assert.equal(compareMoney('0.004', '0'), 1);
});

/** 执行门户真实mock的订单试算，在隔离内存中制造半分临界套餐价，不访问真实财务或支付接口。 */
test('portal mock subscription preview uses precise multiplication and balance subtraction', async () => {
  const helpers = await money;
  const context = { ...helpers };
  const source = fs.readFileSync(path.join(__dirname, '../systemportal/src/mock/portalMock.js'), 'utf8')
    .replace(/^import .*;\r?$/gm, '').replace(/export function /g, 'function ');
  vm.runInNewContext(`${source}\nsubscription = null; plans[0].price = '0.335'; wallet.balanceAmount = 0.1;
    result = calculateSubscriptionOrder(plans[0].id, 3);`, context);
  assert.equal(context.result.priceAmount, 1.01);
  assert.equal(context.result.shortfallAmount, 0.91);
});

/** 真实监控mock调账在隔离模块中运行，验证连续小额调整不会把浮点尾差写回内存余额。 */
test('monitor mock balance adjustments sum decimal amounts precisely', async () => {
  const moduleUrl = text => `data:text/javascript;base64,${Buffer.from(text).toString('base64')}`;
  const base = path.join(__dirname, '../monitor-frontend/src/mock');
  const databaseUrl = moduleUrl(fs.readFileSync(path.join(base, 'database.js'), 'utf8'));
  const reminderUrl = moduleUrl(fs.readFileSync(path.join(base, 'reminders.js'), 'utf8'));
  const source = fs.readFileSync(path.join(base, 'adapter.js'), 'utf8')
    .replace("'../utils/money'", JSON.stringify(moduleUrl(moneySource)))
    .replace("'./database'", JSON.stringify(databaseUrl))
    .replace("'./reminders'", JSON.stringify(reminderUrl));
  const { enterprises } = await import(databaseUrl);
  const { mockAdapter } = await import(moduleUrl(source));
  const row = enterprises[0];
  row.balance = 0.1;
  await mockAdapter({ url: `/enterprises/${row.id}/balance-adjustments`, method: 'POST', data: { amount: '0.20' } });
  assert.equal(row.balance, 0.3);
});
