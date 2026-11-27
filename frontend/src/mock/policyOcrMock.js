/**
 * 一份附件同时包含商业险和交强险的联调响应，字段完全对应 OCR 接口的扁平 recognitionData。
 * 文件 ID 由承保表单的 mock 上传生成；非车险第二份文件用于验证多来源候选。
 */
const policyTasks = new Map();

/**
 * 模拟异步任务提交，同一文件重复提交复用任务 ID，供页面联调去重和轮询。
 * @param {string|number} workorderId 当前表单工单 ID。
 * @param {string|number} fileId 当前上传的系统文件 ID。
 * @returns {Promise<object>} 与后端任务提交接口一致的响应。
 */
export function mockPolicyRecognition(workorderId, fileId) {
  const jobId = `mock-policy-${workorderId}-${fileId}`;
  if (!policyTasks.has(jobId)) policyTasks.set(jobId, { workorderId, fileId, polls: 0 });
  return Promise.resolve({ data: { code: 200, msg: '识别任务已提交', data: { jobId } } });
}

/**
 * 首次轮询保持运行状态，第二次返回原有三险示例结果。
 * 结果中的 fileId 与提交一致，页面可按附件生命周期判断是否仍应回填。
 * @param {string} jobId 提交接口返回的 mock 任务 ID。
 * @returns {Promise<object>} 与后端状态接口一致的响应。
 */
export function mockPolicyRecognitionStatus(jobId) {
  const task = policyTasks.get(jobId);
  if (!task) return Promise.resolve({ data: { code: 404, msg: '识别任务不存在或已过期' } });
  task.polls += 1;
  if (task.polls === 1) return Promise.resolve({ data: { code: 200, msg: '查询成功', data: {
    jobId, workorderId: String(task.workorderId), fileId: String(task.fileId), status: 'RUNNING'
  } } });
  const fileId = task.fileId;
  if (String(fileId).endsWith('3')) return Promise.resolve({ data: {
    code: 200, msg: '查询成功', data: { jobId, workorderId: String(task.workorderId), fileId: String(fileId), status: 'FAILED', result: {
      code: 502, msg: '保单识别结果无法解析，请重试或手动填写'
    } }
  } });
  /*
   * 第四份附件模拟有效但全空的模型结果，任务成功结束，由页面按上传类别提示一次。
   */
  if (String(fileId).endsWith('4')) return Promise.resolve({ data: {
    code: 200, msg: '查询成功', data: { jobId, workorderId: String(task.workorderId), fileId: String(fileId), status: 'SUCCEEDED', result: {
      code: 200, msg: '识别成功', data: {
        fileInfo: { id: fileId, fileName: '未识别到内容的附件.pdf' }, recognitionData: {}, warnings: []
      }
    } }
  } });
  const nonMotor = String(fileId).endsWith('2');
  return Promise.resolve({ data: {
    code: 200, msg: '查询成功', data: { jobId, workorderId: String(task.workorderId), fileId: String(fileId), status: 'SUCCEEDED', result: {
      code: 200, msg: '识别成功', data: {
      fileInfo: { id: fileId, fileName: nonMotor ? '非车险保单.pdf' : '组合保单.pdf' },
      recognitionData: nonMotor ? {
        nonMotorInsurerName: '示例保险公司', nonMotorPolicyNo: 'NM-0002',
        nonMotorInsuranceStartTime: 1793980800, nonMotorInsuranceEndTime: 1825516800,
        nonMotorAmount: '200.00', nonMotorInsuranceName: '驾乘意外险', nonMotorCoverageAmount: '100000.00'
      } : {
        commercialInsurerName: '示例保险公司', commercialPolicyNo: 'COM-0001',
        commercialInsuranceStartTime: 1793980800, commercialInsuranceEndTime: 1825516800,
        commercialAmount: '3000.00', compulsoryInsurerName: '示例保险公司',
        compulsoryPolicyNo: 'CTP-0001', compulsoryInsuranceStartTime: 1793980800,
        compulsoryInsuranceEndTime: 1825516800, compulsoryAmount: '950.00', vehicleAndTaxAmount: '360.00'
      },
      warnings: []
    } }
  } } });
}
