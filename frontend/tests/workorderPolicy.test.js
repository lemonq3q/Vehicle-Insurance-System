const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/utils/workorderPolicy.js'), 'utf8');
const moduleUrl = `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`;
const policy = import(moduleUrl);

/** 构建只选择商业险的完整编辑快照，保留其他旧值以验证取消后会被显式清空。 */
function fixture() {
  return {
    model: { commercialInsurerName: '测试公司', commercialPolicyNo: 'C001',
      commercialInsuranceStartTime: new Date('2026-11-01T00:00:00+08:00'),
      commercialInsuranceEndTime: new Date('2027-11-01T00:00:00+08:00'),
      commercialAmount: '100.05', compulsoryAmount: '950', vehicleAndTaxAmount: '360', nonMotorAmount: '200',
      upstreamComputeType: 1, downstreamComputeType: 0, upstreamCommercialPercentage: '10',
      upstreamCommercialAmount: '999', downstreamCommercialAmount: '0', downstreamCommercialPercentage: '99',
      compulsoryPolicyNo: 'OLD', nonMotorCoverageAmount: 0 },
    flags: { insuranceType1: true, insuranceType3: false, is_have_non_motor: false,
      upstream_commercial: false, downstream_commercial: true }
  };
}

/** 取消险种、零元与模式切换必须明确表达，不把旧金额或旧比例继续提交。 */
test('complete policy payload clears canceled groups and stale commission mode', async () => {
  const { buildPolicyPayload } = await policy;
  const { model, flags } = fixture();
  const data = buildPolicyPayload(model, flags);
  assert.deepEqual(data.policyTypes, ['COMMERCIAL']);
  assert.equal(data.commercialInsuranceStartTime, 1793462400);
  assert.equal(data.compulsoryPolicyNo, null);
  assert.equal(data.compulsoryAmount, null);
  assert.equal(data.vehicleAndTaxAmount, null);
  assert.equal(data.nonMotorAmount, null);
  assert.equal(data.upstreamCommercialAmount, null);
  assert.equal(data.upstreamCommercialPercentage, '10');
  assert.equal(data.downstreamCommercialPercentage, null);
  assert.equal(data.downstreamCommercialAmount, '0');
  assert.ok(!('requiredPayAmount' in data));
  assert.ok(!('quotationRemark' in data));
  flags.is_have_non_motor = true;
  assert.equal(buildPolicyPayload(model, flags).nonMotorCoverageAmount, 0);
});

/** 仅起期与保费必填，终期、其他资料及附件可空；已填终期仍须晚于起期。 */
test('policy validation requires only start date and premium', async () => {
  const { buildPolicyPayload, validatePolicies, policyFields, POLICY_GROUPS } = await policy;
  for (const group of POLICY_GROUPS) {
    assert.deepEqual(policyFields(group).filter(field => field.required).map(field => field.key),
      [group.prefix + 'InsuranceStartTime', group.amount]);
  }
  const { model, flags } = fixture();
  const data = buildPolicyPayload(model, flags);
  const files = [{ fileId: '901', type: 'acceptInsuranceCommercial' }];
  assert.equal(validatePolicies(data, files), '');
  assert.equal(validatePolicies({ ...data, commercialInsurerName: null, commercialPolicyNo: null,
    commercialInsuranceEndTime: null }, []), '');
  assert.match(validatePolicies({ ...data, commercialInsuranceStartTime: null }, []), /起期/);
  assert.match(validatePolicies({ ...data, commercialAmount: null }, []), /保费/);
  assert.match(validatePolicies({ ...data, commercialInsuranceEndTime: data.commercialInsuranceStartTime }, files), /终期/);
  assert.match(validatePolicies({ ...data, commercialAmount: '-1' }, files), /非负/);
  assert.match(validatePolicies({ ...data, commercialAmount: '1.001' }, files), /两位/);
  assert.equal(validatePolicies({ ...data, commercialAmount: '0' }, files), '');
  assert.equal(validatePolicies({ ...data, commercialInsurerName: ' ' }, files), '');
  assert.match(validatePolicies({ ...data, commercialInsurerName: 'x'.repeat(201) }, files), /输入内容过长/);
  assert.match(validatePolicies(data, files.concat({ fileId: '902', type: 'acceptInsuranceCommercial' })), /最多上传/);
});

/** 历史资料只有起期、零元或附件时仍应展示，全部缺失则不能凭空生成投保事实。 */
test('historical policies preserve zero and file-only evidence', async () => {
  const { hasPolicy, POLICY_GROUPS } = await policy;
  assert.equal(hasPolicy({}, POLICY_GROUPS[2]), false);
  assert.equal(hasPolicy({ nonMotorAmount: 0 }, POLICY_GROUPS[2]), true);
  assert.equal(hasPolicy({ workorderFileList: [{ type: 'acceptInsuranceNonMotor' }] }, POLICY_GROUPS[2]), true);
  assert.equal(hasPolicy({ workorderFileList: [{ type: 'acceptInsuranceOther' }] }, POLICY_GROUPS[2]), true);
  assert.equal(hasPolicy({ commercialInsuranceStartTime: 1793462400 }, POLICY_GROUPS[0]), true);
});

/** 非车险与其他单证共用一组文件，一至六份有效，取消险种后载荷不携带这些文件。 */
test('non motor policy submits one shared multi-file document group', async () => {
  const { buildPolicyPayload, validatePolicies } = await policy;
  const component = fs.readFileSync(path.join(__dirname, '../src/components/DetailWorkorder.vue'), 'utf8');
  const { model, flags } = fixture();
  flags.is_have_non_motor = true;
  Object.assign(model, { nonMotorInsurerName: '测试公司', nonMotorPolicyNo: 'NM-1',
    nonMotorInsuranceStartTime: new Date('2026-11-01T00:00:00+08:00'),
    nonMotorInsuranceEndTime: new Date('2027-11-01T00:00:00+08:00') });
  const context = { buildPolicyPayload, info: model, showFlag: flags, oriInfo: { value: { id: '101' } },
    workorderFileIds: { acceptInsuranceCommercialFile: '901', acceptInsuranceOtherFile: ['902', '903'] } };
  const builder = component.match(/const buildAcceptInsuranceFrom = [\s\S]*?\n\}/)[0];
  const payload = JSON.parse(vm.runInNewContext(`${builder}\nJSON.stringify(buildAcceptInsuranceFrom())`, { ...context }));
  assert.deepEqual(payload.workorderFileList.map(file => file.type),
    ['acceptInsuranceCommercial', 'acceptInsuranceOther', 'acceptInsuranceOther']);
  assert.equal(validatePolicies(payload, payload.workorderFileList), '');
  assert.match(validatePolicies(payload, payload.workorderFileList.concat(
    Array.from({ length: 5 }, (_, index) => ({ fileId: String(910 + index), type: 'acceptInsuranceOther' })))), /最多上传6份/);
  flags.is_have_non_motor = false;
  const canceled = JSON.parse(vm.runInNewContext(`${builder}\nJSON.stringify(buildAcceptInsuranceFrom())`, { ...context }));
  assert.deepEqual(canceled.workorderFileList.map(file => file.type), ['acceptInsuranceCommercial']);
  assert.doesNotMatch(component, /<div class="workorder_file_title">其他单证/);
});

/** 其他单证独立于非车险，可在仅投保商业险时记录六份；超限由前端拒绝。 */
test('misc documents remain independent and never enter policy OCR', async () => {
  const { buildPolicyPayload, validatePolicies } = await policy;
  const component = fs.readFileSync(path.join(__dirname, '../src/components/DetailWorkorder.vue'), 'utf8');
  const { model, flags } = fixture();
  const builder = component.match(/const buildAcceptInsuranceFrom = [\s\S]*?\n\}/)[0];
  const ids = Array.from({ length: 6 }, (_, index) => String(920 + index));
  const payload = JSON.parse(vm.runInNewContext(`${builder}\nJSON.stringify(buildAcceptInsuranceFrom())`, {
    buildPolicyPayload, info: model, showFlag: flags, oriInfo: { value: { id: '101' } },
    workorderFileIds: { acceptInsuranceCommercialFile: '901', acceptInsuranceMiscFile: ids }
  }));
  assert.equal(payload.workorderFileList.filter(file => file.type === 'acceptInsuranceMisc').length, 6);
  assert.equal(validatePolicies(payload, payload.workorderFileList), '');
  assert.match(validatePolicies(payload, payload.workorderFileList.concat({ fileId: '930', type: 'acceptInsuranceMisc' })), /其他单证最多上传6份/);
  const uploader = component.match(/const handleAcceptInsuranceMiscChange = [\s\S]*?\n\};/)[0];
  assert.doesNotMatch(uploader, /recognizeUploadedPolicy|policyRecognition/);
});

/** 直接执行页面实际载荷函数，验证报价不会夹带保费，承保不会用旧保单号覆盖取消值。 */
test('actual page builders isolate quote fields and canceled policy data', async () => {
  const { buildPolicyPayload } = await policy;
  const component = fs.readFileSync(path.join(__dirname, '../src/components/DetailWorkorder.vue'), 'utf8');
  const { model, flags } = fixture();
  const context = { buildPolicyPayload, info: { ...model, quotationRemark: '只更新报价', commercialPolicyNo: 'C001' },
    showFlag: flags, oriInfo: { value: { id: '101', status: 10 } }, workorderFileIds: { quotationFile: '899', acceptInsuranceCommercialFile: '901' } };
  const quote = component.match(/const buildQuotationUpdateFrom = [\s\S]*?\n\}\);/)[0];
  const quoteData = vm.runInNewContext(`${quote}\nJSON.stringify(buildQuotationUpdateFrom(8))`, context);
  assert.deepEqual(Object.keys(JSON.parse(quoteData)).sort(), ['id', 'quotationRemark', 'status', 'workorderFileList']);
  assert.equal(JSON.parse(quoteData).status, 10);
  const accept = component.match(/const buildAcceptInsuranceFrom = [\s\S]*?\n\}/)[0];
  const accepted = JSON.parse(vm.runInNewContext(`${accept}\nJSON.stringify(buildAcceptInsuranceFrom())`, context));
  assert.equal(accepted.compulsoryPolicyNo, null);
  assert.deepEqual(accepted.workorderFileList.map(file => file.type), ['acceptInsuranceCommercial']);
});

/** mock 返回与真实接口相同的响应，并复现报价不覆盖保单和精确汇总口径。 */
test('switchable mock keeps quote separate and returns policy totals', async () => {
  const moneySource = fs.readFileSync(path.join(__dirname, '../../shared/money.js'), 'utf8');
  const moneyUrl = `data:text/javascript;base64,${Buffer.from(moneySource).toString('base64')}`;
  const mockSource = fs.readFileSync(path.join(__dirname, '../src/mock/workorderPolicyMock.js'), 'utf8')
    .replace("'@/utils/workorderPolicy'", JSON.stringify(moduleUrl)).replace("'@/utils/money'", JSON.stringify(moneyUrl));
  const { workorderPolicyMockRequest } = await import(`data:text/javascript;base64,${Buffer.from(mockSource).toString('base64')}`);
  const response = await workorderPolicyMockRequest('quotation', { id: '101', status: 8, quotationRemark: '更新报价', commercialAmount: 1, workorderFileList: [] });
  assert.equal(response.data.data.commercialAmount, '3000.00');
  assert.equal(response.data.data.totalPremiumAmount, '4150.00');
  assert.equal(response.data.data.payableAmount, '4510.00');
  assert.equal((await workorderPolicyMockRequest('detail', '102')).data.data.totalPremiumAmount, null);
  const optional = (await workorderPolicyMockRequest('detail', '104')).data.data;
  assert.equal(optional.commercialPolicyNo, null);
  assert.equal(optional.commercialInsuranceEndTime, null);
  assert.equal(optional.vehicleAndTaxAmount, null);
  assert.deepEqual(optional.workorderFileList, []);
});
