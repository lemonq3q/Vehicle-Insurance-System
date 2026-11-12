const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const moneySource = fs.readFileSync(path.join(__dirname, '../../shared/money.js'), 'utf8');
const moneyUrl = `data:text/javascript;base64,${Buffer.from(moneySource).toString('base64')}`;
const source = fs.readFileSync(path.join(__dirname, '../src/utils/workorderDisplay.js'), 'utf8')
  .replace("'./money'", JSON.stringify(moneyUrl));
const display = import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);

/**
 * 姓名存在时不能在其下拼接微信内部账号或邮箱，避免列表被长账号撑宽。
 */
test('handler shows only the nonblank real name', async () => {
  const { formatWorkorderHandler } = await display;
  assert.equal(formatWorkorderHandler({ handleUserName: ' 小马科技 ', handleUserEmail: 'a@example.com',
    handleUserWechatNickname: '微信昵称', handleUserPhone: 'wx_internal_id', handleUser: '小马科技\nwx_internal_id' }), '小马科技');
});

/**
 * 姓名为空时应逐级回退，空白也算缺失；旧组合文本只选一段而不是原样双行展示。
 */
test('handler falls back through email, wechat, phone, username and legacy display', async () => {
  const { formatWorkorderHandler } = await display;
  const row = { handleUserName: ' ', handleUserEmail: 'issuer@example.com', handleUserWechatNickname: '微信昵称',
    handleUserPhone: '13800000000', handleUserUsername: 'wx_internal' };
  assert.equal(formatWorkorderHandler(row), 'issuer@example.com');
  row.handleUserEmail = null;
  assert.equal(formatWorkorderHandler(row), '微信昵称');
  row.handleUserWechatNickname = '';
  assert.equal(formatWorkorderHandler(row), '13800000000');
  row.handleUserPhone = null;
  assert.equal(formatWorkorderHandler(row), 'wx_internal');
  assert.equal(formatWorkorderHandler({ handleUser: '\n历史姓名\n旧账号' }), '历史姓名');
  assert.equal(formatWorkorderHandler({}), '');
});

/**
 * 待处理、待报价和首次报价失败均不能将尚未发生的费用显示为确认后的零费用。
 */
test('unquoted orders keep missing fees empty', async () => {
  const { formatWorkorderPolicyFee } = await display;
  for (const status of [1, 2, 3, '2', null, undefined, 99]) {
    assert.equal(formatWorkorderPolicyFee({ status, quotationTime: null }, null), '');
  }
});

/**
 * 报价后各流程状态均应区分零费用和未报价；历史缺少报价时间也不能漏补零。
 */
test('quoted workflow states show zero for missing policy fees', async () => {
  const { formatWorkorderPolicyFee } = await display;
  for (const status of [4, 5, 6, 7, 8, 9, 10, '4', '10']) {
    for (const missing of [null, undefined, '', '  ']) {
      assert.equal(formatWorkorderPolicyFee({ status }, missing), '0.00');
    }
  }
  assert.equal(formatWorkorderPolicyFee({ status: 2, quotationTime: 1793577600 }, null), '0.00');
  assert.equal(formatWorkorderPolicyFee({ status: 2, quotationTime: 'invalid' }, null), '');
});

/**
 * 格式化统一两位小数且保留显式零、负号及金额字符串的有效精度，也不得改写接口或业务操作字段。
 */
test('display formats money without changing original business fields', async () => {
  const { formatWorkorderPolicyFee, buildWorkorderListDisplay } = await display;
  for (const amount of [0, '0.00', '128.50', -10, '-0.01']) {
    assert.equal(formatWorkorderPolicyFee({ status: 2 }, amount), Number(amount).toFixed(2));
  }
  assert.equal(formatWorkorderPolicyFee({ status: 4 }, 258.58000000000004), '258.58');
  assert.equal(formatWorkorderPolicyFee({ status: 4 }, '9999999999.99'), '9999999999.99');
  const row = Object.freeze({ status: 4, handleUserName: '李鸣', handleUser: '李鸣\n旧账号',
    upstreamSumAmount: null, downstreamSumAmount: '0.00' });
  assert.deepEqual(buildWorkorderListDisplay(row), {
    handleUserDisplay: '李鸣', upstreamPolicyFeeDisplay: '0.00', downstreamPolicyFeeDisplay: '0.00'
  });
  assert.equal(row.upstreamSumAmount, null);
  assert.equal(row.handleUser, '李鸣\n旧账号');
});
