const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../shared/money.js'), 'utf8');
const money = import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);

/** 验证二进制尾差及十进制舍入边界，防止仅使用toFixed导致分位截低或负零。 */
test('money uses decimal HALF_UP and never exposes floating point tails', async () => {
  const { formatMoney } = await money;
  for (const [input, output] of [[258.58000000000004, '258.58'], [1.005, '1.01'],
    ['2.675', '2.68'], ['-1.005', '-1.01'], [-0.001, '0.00'], ['1e-2', '0.01'],
    ['9999999999.99', '9999999999.99'], ['0.00', '0.00']]) {
    assert.equal(formatMoney(input), output);
  }
});

/** 缺失与非法金额不能变为已确认零元；只允许十进制数值，防止隐式转换布尔值、十六进制等。 */
test('missing and invalid money remains empty and supports placeholders', async () => {
  const { formatMoney, sumMoney, calculatePolicyAmount } = await money;
  for (const input of [null, undefined, '', '  ', NaN, Infinity, '-Infinity', 'abc', true, {}, '0x10', '1e999']) {
    assert.equal(formatMoney(input), '');
    assert.equal(formatMoney(input, '-'), '-');
    assert.equal(sumMoney([input]), '');
    assert.equal(calculatePolicyAmount(input, 100, 0), '-');
  }
});

/** 金额可来自JSON数字或精确字符串；合计不能发生拼接，且只在汇总完成后舍入。 */
test('sums preserve exact decimal inputs, negatives, empty and zero semantics', async () => {
  const { sumMoney } = await money;
  assert.equal(sumMoney([235.58, 0, 0, 23]), '258.58');
  assert.equal(sumMoney(['0.10', '0.20', null]), '0.30');
  assert.equal(sumMoney([0.1, 0.2]), '0.30');
  assert.equal(sumMoney(['0.004', '0.004']), '0.01');
  assert.equal(sumMoney(['-10.01', '0.01']), '-10.00');
  assert.equal(sumMoney([0, '', null]), '0.00');
  assert.equal(sumMoney([]), '');
});

/** 政策预览必须与后端/100和/106公式一致，中间不提前舍入，并支持小数比例。 */
test('policy preview rounds only after applying the complete tax formula', async () => {
  const { calculatePolicyAmount } = await money;
  assert.equal(calculatePolicyAmount(10, 2355.83, 0), '235.58');
  assert.equal(calculatePolicyAmount(10, 10.05, 0), '1.01');
  assert.equal(calculatePolicyAmount(10, '106.53', '1'), '10.05');
  assert.equal(calculatePolicyAmount('0.125', '1000', 0), '1.25');
  assert.equal(calculatePolicyAmount(0, 100, 0), '0.00');
  assert.equal(calculatePolicyAmount('', 100, 0), '-');
});

/** 执行详情页实际的合计函数与表单计算，确保四个入口均采用修复逻辑，未改写接口模型。 */
test('detail and quotation form use decimal sums on the real component functions', async () => {
  const { sumMoney } = await money;
  const component = fs.readFileSync(path.join(__dirname, '../src/components/DetailWorkorder.vue'), 'utf8');
  const mockSource = fs.readFileSync(path.join(__dirname, '../src/mock/moneyDisplayMock.js'), 'utf8');
  const { moneyDisplayMock } = await import(`data:text/javascript;base64,${Buffer.from(mockSource).toString('base64')}`);
  const row = Object.freeze(moneyDisplayMock.data);
  const policySource = fs.readFileSync(path.join(__dirname, '../src/utils/workorderPolicy.js'), 'utf8');
  const { POLICY_GROUPS } = await import(`data:text/javascript;base64,${Buffer.from(policySource).toString('base64')}`);
  const context = { sumMoney, POLICY_GROUPS, showFlag: { insuranceType1: true, insuranceType3: true, is_have_non_motor: true }, oriInfo: { value: row }, info: row, computed: fn => fn() };
  for (const [name, expected] of [['computeSumAmount', '2585.83'], ['computeUpstreamAmount', '258.58'],
    ['computeDownstreamAmount', '0.30'], ['sumAmount', '2585.83']]) {
    const expression = component.match(new RegExp(`const ${name} = [\\s\\S]*?;`))[0];
    assert.equal(vm.runInNewContext(`${expression}\n${name}${name === 'sumAmount' ? '' : '()'}`, context), expected);
  }
  assert.equal(row.upstreamSumAmount, 258.58000000000004);
});
