const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

/**
 * 在项目既有 Node 测试方式下加载纯前端工具函数，注入同一份保单字段配置。
 * 验证组合保单跨上传区域回填和未知字段拒绝，不复制页面实现逻辑。
 */
async function loadPolicyOcr() {
  const policySource = fs.readFileSync(path.join(__dirname, '../src/utils/workorderPolicy.js'), 'utf8');
  const policy = await import(`data:text/javascript;base64,${Buffer.from(policySource).toString('base64')}`);
  const ocrSource = fs.readFileSync(path.join(__dirname, '../src/utils/policyOcr.js'), 'utf8')
    .replace(/^import .*;\r?\n/m, '')
    .replace(/export function /g, 'function ');
  const context = { POLICY_GROUPS: policy.POLICY_GROUPS, policyFields: policy.policyFields };
  vm.runInNewContext(`${ocrSource}\nthis.api = { normalizePolicyOcrCandidates, detectedPolicyGroups };`, context);
  return context.api;
}

/**
 * 一份组合保单应同时返回商业险和交强险字段，陌生字段不能写入承保表单。
 */
test('mixed policy OCR populates all recognized groups', async () => {
  const { normalizePolicyOcrCandidates, detectedPolicyGroups } = await loadPolicyOcr();
  const values = normalizePolicyOcrCandidates({
    commercialPolicyNo: 'C001', compulsoryAmount: '950.00',
    compulsoryInsuranceStartTime: 1793980800, unknown: 'discard'
  });
  assert.equal(values.commercialPolicyNo, 'C001');
  assert.equal(values.compulsoryAmount, '950.00');
  assert.equal(values.unknown, undefined);
  assert.ok(values.compulsoryInsuranceStartTime instanceof Date ||
    typeof values.compulsoryInsuranceStartTime?.getTime === 'function');
  assert.equal(detectedPolicyGroups(values).map(group => group.type).join(','), 'COMMERCIAL,COMPULSORY');
});

/**
 * 模拟提交接口与两次状态查询，确认成功、失败均通过任务状态返回，
 * 同一文件重复提交保持相同任务 ID，前端不会重复发起模型识别。
 */
test('policy OCR mock follows asynchronous task contract', async () => {
  const source = fs.readFileSync(path.join(__dirname, '../src/mock/policyOcrMock.js'), 'utf8');
  const mock = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
  const first = (await mock.mockPolicyRecognition('101', 'MOCK-POLICY-1')).data.data.jobId;
  const duplicate = (await mock.mockPolicyRecognition('101', 'MOCK-POLICY-1')).data.data.jobId;
  assert.equal(first, duplicate);
  assert.equal((await mock.mockPolicyRecognitionStatus(first)).data.data.status, 'RUNNING');
  const completed = (await mock.mockPolicyRecognitionStatus(first)).data.data;
  assert.equal(completed.status, 'SUCCEEDED');
  assert.equal(completed.result.data.recognitionData.compulsoryAmount, '950.00');

  const failed = (await mock.mockPolicyRecognition('101', 'MOCK-POLICY-3')).data.data.jobId;
  await mock.mockPolicyRecognitionStatus(failed);
  const outcome = (await mock.mockPolicyRecognitionStatus(failed)).data.data;
  assert.equal(outcome.status, 'FAILED');
  assert.equal(outcome.result.code, 502);
});
