const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../src/utils/renewalFollowUp.js'), 'utf8');
const followUp = import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);

/** 已处理险种仍在当前提醒周期内，表单须显示且回填结果。 */
test('shows handled insurance while its current cycle is active', async () => {
  const { buildRenewalFollowUpState } = await followUp;
  const state = buildRenewalFollowUpState({ id: 21, commercialActiveCycle: 1, commercialReminder: false,
    commercialRenewalStatus: 1, compulsoryActiveCycle: 1, compulsoryReminder: true,
    compulsoryRenewalStatus: 0, followUpRes: '已联系客户' });
  assert.equal(state.commercialInCycle, true);
  assert.equal(state.commercialStatus, 1);
  assert.equal(state.compulsoryInCycle, true);
  assert.equal(state.compulsoryStatus, 0);
});

/** 已处理结果可改回未处理，表单会明确选中该状态。 */
test('fills unprocessed choice for an active reminder', async () => {
  const { buildRenewalFollowUpState } = await followUp;
  const state = buildRenewalFollowUpState({ id: 23, commercialActiveCycle: 1,
    commercialRenewalStatus: 0, compulsoryActiveCycle: 0 });
  assert.equal(state.commercialInCycle, true);
  assert.equal(state.commercialStatus, 0);
});

/** 未到当前周期的险种不显示，也不会携带历史处理结果。 */
test('hides insurance before its reminder cycle', async () => {
  const { buildRenewalFollowUpState } = await followUp;
  const state = buildRenewalFollowUpState({ id: 22, commercialActiveCycle: 0,
    commercialRenewalStatus: 2, compulsoryActiveCycle: 1, compulsoryRenewalStatus: 2 });
  assert.equal(state.commercialInCycle, false);
  assert.equal(state.commercialStatus, undefined);
  assert.equal(state.compulsoryInCycle, true);
  assert.equal(state.compulsoryStatus, 2);
});

/** 已处理商业险改为已续保、交强险仍未处理时，旧接口只接收实际变化的商业险。 */
test('omits unchanged unprocessed insurance from a single change', async () => {
  const { buildRenewalFollowUpState, buildRenewalFollowUpRequest } = await followUp;
  const form = buildRenewalFollowUpState({ id: 24, commercialActiveCycle: 1,
    commercialRenewalStatus: 2, compulsoryActiveCycle: 1, compulsoryRenewalStatus: 0 });
  form.commercialStatus = 1;
  assert.deepEqual(buildRenewalFollowUpRequest(form), {
    scope: 'COMMERCIAL', status: 1, followUpRes: ''
  });
});

/** 将已处理状态改回未处理仍使用新版字段，不能把状态零丢掉。 */
test('retains explicit zero when restoring an unprocessed reminder', async () => {
  const { buildRenewalFollowUpState, buildRenewalFollowUpRequest } = await followUp;
  const form = buildRenewalFollowUpState({ id: 25, commercialActiveCycle: 1,
    commercialRenewalStatus: 1, compulsoryActiveCycle: 1, compulsoryRenewalStatus: 0 });
  form.commercialStatus = 0;
  assert.deepEqual(buildRenewalFollowUpRequest(form), { commercialStatus: 0, followUpRes: '' });
});
