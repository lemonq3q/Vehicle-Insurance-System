const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../src/utils/ocrCandidates.js'), 'utf8');
const candidates = import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);

/** 检查原始识别值和原表单均不被候选生成修改，空识别不覆盖人工资料，数值零不丢失。 */
test('candidates preserve source and form while keeping zero and rejecting unknown fields', async () => {
  const { normalizeOcrCandidates } = await candidates;
  const result = { ownerName: '新姓名', seats: 0, vehicleCode: ' ', unknown: '不允许写入' };
  const form = { ownerName: '原姓名', seats: 5, vehicleCode: '原车架号' };
  assert.deepEqual(normalizeOcrCandidates(result, form, 'vehicleLicense'), {
    'vehicleLicense.ownerName': '新姓名', 'vehicleLicense.seats': 0
  });
  assert.equal(form.ownerName, '原姓名');
  assert.equal(form.seats, 5);
  assert.equal(result.vehicleCode, ' ');
});

/** 日期恢复为表单Date，非法日期无候选；基础证件通过明确别名映射到工单字段。 */
test('dates and identity aliases retain the target form types', async () => {
  const { normalizeOcrCandidates } = await candidates;
  const result = normalizeOcrCandidates({ registrationDate: 1793203200, issueDate: '非法日期' }, { registrationDate: null, issueDate: null }, 'vehicleLicense');
  assert.ok(result['vehicleLicense.registrationDate'] instanceof Date);
  assert.equal(result['vehicleLicense.issueDate'], undefined);
  assert.deepEqual(normalizeOcrCandidates({ name: '张三', idNum: '00123' }, { name: 'ownerName', idNum: 'ownerIdNum' }), { ownerName: '张三', ownerIdNum: '00123' });
});

/** 确认后清除所有来源的同一字段，其他字段、零值和日期候选仍可继续逐项确认。 */
test('applying a field consumes all its candidates without removing other fields', async () => {
  const { consumeOcrCandidates } = await candidates;
  const results = {
    licenseFront: { 'vehicleLicense.vehicleCode': '正页识别', 'vehicleLicense.seats': 0 },
    licenseBack: { 'vehicleLicense.vehicleCode': '副页识别', 'vehicleLicense.issueDate': new Date(2026, 9, 29) },
    certificate: { 'vehicleCertificate.brandModel': '车型' }
  };
  consumeOcrCandidates(results, 'vehicleLicense.vehicleCode');
  assert.equal(results.licenseFront['vehicleLicense.vehicleCode'], undefined);
  assert.equal(results.licenseBack['vehicleLicense.vehicleCode'], undefined);
  assert.equal(results.licenseFront['vehicleLicense.seats'], 0);
  assert.ok(results.licenseBack['vehicleLicense.issueDate'] instanceof Date);
  assert.equal(results.certificate['vehicleCertificate.brandModel'], '车型');
});

/**
 * 识别完成后空字段直接补齐，已有值含0与false保留并提供候选；自动填入日期不共享引用。
 */
test('OCR fills empty fields and retains populated fields for confirmation', async () => {
  const { fillEmptyOcrFields } = await candidates;
  const date = new Date(2026, 9, 31);
  const form = { ownerName: ' ', ownerIdNum: '原证件号', vehicleLicense: {
    engineCode: '', vehicleCode: null, issueDate: undefined, seats: 0, isTransfer: false
  } };
  const values = { ownerName: '张三', ownerIdNum: '新证件号',
    'vehicleLicense.engineCode': '新发动机号', 'vehicleLicense.vehicleCode': '新车架号',
    'vehicleLicense.issueDate': date, 'vehicleLicense.seats': 5, 'vehicleLicense.isTransfer': true };
  assert.deepEqual(fillEmptyOcrFields(form, values), {
    ownerIdNum: '新证件号', 'vehicleLicense.seats': 5, 'vehicleLicense.isTransfer': true
  });
  assert.equal(form.ownerName, '张三');
  assert.equal(form.ownerIdNum, '原证件号');
  assert.equal(form.vehicleLicense.engineCode, '新发动机号');
  assert.equal(form.vehicleLicense.vehicleCode, '新车架号');
  assert.equal(form.vehicleLicense.seats, 0);
  assert.equal(form.vehicleLicense.isTransfer, false);
  assert.equal(form.vehicleLicense.issueDate.getTime(), date.getTime());
  assert.notEqual(form.vehicleLicense.issueDate, date);
  assert.equal(Object.keys(values).length, 7);
});

/**
 * 同一字段先被自动或人工填入后，后续证件的冲突结果必须待确认，不能按首次上传的空值覆盖。
 */
test('later OCR responses preserve values already filled during recognition', async () => {
  const { fillEmptyOcrFields } = await candidates;
  const form = { vehicleLicense: { brandModel: '' } };
  assert.deepEqual(fillEmptyOcrFields(form, { 'vehicleLicense.brandModel': '首次车型' }), {});
  form.vehicleLicense.brandModel = '人工修正车型';
  assert.deepEqual(fillEmptyOcrFields(form, { 'vehicleLicense.brandModel': '再次识别车型' }), {
    'vehicleLicense.brandModel': '再次识别车型'
  });
  assert.equal(form.vehicleLicense.brandModel, '人工修正车型');
});
