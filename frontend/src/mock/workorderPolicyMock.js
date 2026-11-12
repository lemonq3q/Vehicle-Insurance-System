import { sumMoney, calculatePolicyAmount } from '@/utils/money';
import { POLICY_GROUPS, policyFields } from '@/utils/workorderPolicy';

/**
 * 可切换的工单报价/承保联调样例，仅在 VUE_APP_WORKORDER_POLICY_MOCK=true 时启用。
 * 数据字段来自两表迁移结构，响应与真实 axios 保持相同外壳，不接触数据库或真实文件。
 */
export const workorderPolicyInsuranceMock = [
  { id: '1', type: 1, name: '商业险', optionJson: '投保' },
  { id: '3', type: 3, name: '交强险', optionJson: '投保' }
];
const complete = {
  id: '101', code: 'MOCK-W101', status: 8, type: 0, ownerType: 0, ownerName: '联调客户',
  handleBy: '1', submitterName: '联调联系人', payName: '联调收款人', payIdNum: '13800000000',
  payBank: '测试开户行', payBankCardNum: '000000', quotationRemark: '报价已反馈',
  requiredPayAmount: '4510.00', acceptInsuranceRemark: '', upstreamComputeType: 0, downstreamComputeType: 1,
  commercialAmount: '3000.00', compulsoryAmount: '950.00', nonMotorAmount: '200.00', vehicleAndTaxAmount: '360.00',
  vehicleLicense: {}, vehicleCertificate: {}, vehicleInvoice: {},
  workorderInsuranceList: [{ insuranceId: '1', optionJson: '投保' }, { insuranceId: '3', optionJson: '投保' }],
  workorderFileList: []
};
for (const [index, group] of POLICY_GROUPS.entries()) {
  complete[group.prefix + 'InsurerName'] = '测试保险公司';
  complete[group.prefix + 'PolicyNo'] = 'MOCK-POLICY-' + index;
  complete[group.prefix + 'InsuranceStartTime'] = 1793980800;
  complete[group.prefix + 'InsuranceEndTime'] = 1825516800;
  complete.workorderFileList.push({ fileId: String(900 + index), type: group.fileType, fileName: group.title + '.pdf', path: '' });
}
/* 非车险单证沿用原其他单证附件类型，示例同时覆盖多文件和历史单文件。 */
complete.workorderFileList.push({ fileId: '903', type: 'acceptInsuranceOther', fileName: '非车险附加单证.pdf', path: '' });
complete.workorderFileList.push({ fileId: '904', type: 'acceptInsuranceMisc', fileName: '其他单证.pdf', path: '' });
for (const side of ['upstream', 'downstream']) {
  for (const name of ['Commercial', 'Compulsory', 'VehicleAndVesselTax', 'NonMotor']) {
    complete[side + name + 'Percentage'] = '10.00';
    complete[side + name + 'Amount'] = null;
  }
}
const records = { '101': complete, '102': { ...complete, id: '102', status: 2, workorderFileList: [] },
  '103': { ...complete, id: '103', status: 10, commercialInsurerName: null, commercialInsuranceEndTime: null,
    workorderFileList: complete.workorderFileList.map(file => file.fileId === '902'
      ? { ...file, type: 'acceptInsuranceNonMotor' } : { ...file }) },
  '104': { ...complete, id: '104', status: 10, commercialInsurerName: null, commercialPolicyNo: null,
    compulsoryInsurerName: null, compulsoryPolicyNo: null, nonMotorInsurerName: null, nonMotorPolicyNo: null,
    commercialInsuranceEndTime: null, compulsoryInsuranceEndTime: null, nonMotorInsuranceEndTime: null,
    vehicleAndTaxAmount: null, workorderFileList: [] } };

/**
 * 模拟详情、报价和承保更新；报价仅改变报价记录，不覆盖保单及政策。
 * @param {string} action detail / quotation / acceptInsurance。
 * @param {object|string} input 与真实 API 相同的 ID 或请求对象。
 * @returns {Promise<object>} axios 风格的统一业务响应。
 */
export async function workorderPolicyMockRequest(action, input) {
  const id = String(action === 'detail' ? input : input.id);
  const record = records[id];
  if (!record) return { data: { code: 404, msg: '工单不存在', data: null } };
  if (action === 'quotation') {
    record.quotationRemark = input.quotationRemark;
    record.status = Math.max(record.status, input.status);
    record.workorderFileList = record.workorderFileList.filter(file => file.type !== 'quotation').concat(input.workorderFileList || []);
  }
  if (action === 'acceptInsurance') {
    Object.assign(record, input, { status: 10 });
    for (const side of ['upstream', 'downstream']) {
      for (const [dimension, base] of [['Commercial', 'commercialAmount'], ['Compulsory', 'compulsoryAmount'],
        ['VehicleAndVesselTax', 'vehicleAndTaxAmount'], ['NonMotor', 'nonMotorAmount']]) {
        const key = side + dimension;
        if (record[key + 'Percentage'] != null && record[base] != null) {
          record[key + 'Amount'] = calculatePolicyAmount(record[key + 'Percentage'], record[base], record[side + 'ComputeType']);
        }
      }
    }
  }
  record.totalPremiumAmount = sumMoney([record.commercialAmount, record.compulsoryAmount, record.nonMotorAmount]) || null;
  record.payableAmount = sumMoney([record.totalPremiumAmount, record.vehicleAndTaxAmount]) || null;
  return { data: { code: 200, msg: '操作成功', data: JSON.parse(JSON.stringify(record)) } };
}

/**
 * 返回保单为空的独立样例，支持联调尚未承保时的空白表单状态。
 * @returns {object} 不修改已注册样例的深复制。
 */
export function emptyWorkorderPolicyMock() {
  const result = JSON.parse(JSON.stringify(records['102']));
  for (const group of POLICY_GROUPS) for (const field of policyFields(group)) result[field.key] = null;
  result.workorderFileList = [];
  return result;
}
records['102'] = emptyWorkorderPolicyMock();

