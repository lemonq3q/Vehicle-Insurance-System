import { POLICY_GROUPS, policyFields } from './workorderPolicy';

/**
 * 将后端扁平识别结果限制在承保表单现有字段内，并把 Unix 秒转为日期控件使用的 Date。
 * 只接收有效非空值；上传区域不参与过滤，因此组合保单可以同时回填三组。
 * @param {object} recognitionData 后端已校验的三险字段。
 * @returns {object} 以当前表单键为路径的可回填候选。
 */
export function normalizePolicyOcrCandidates(recognitionData) {
  const values = {};
  for (const group of POLICY_GROUPS) {
    for (const field of policyFields(group)) {
      const value = recognitionData?.[field.key];
      if (value === null || value === undefined || value === '') continue;
      if (field.kind === 'date') {
        const date = new Date(Number(value) * 1000);
        if (Number.isFinite(date.getTime())) values[field.key] = date;
      } else {
        values[field.key] = value;
      }
    }
  }
  for (const key of ['nonMotorInsuranceName', 'nonMotorCoverageAmount']) {
    const value = recognitionData?.[key];
    if (value !== null && value !== undefined && value !== '') values[key] = value;
  }
  return values;
}

/**
 * 按识别字段判断哪些险种需要展开并纳入本次承保表单。
 * 不根据附件上传位置勾选险种，避免组合保单遗漏其他险种。
 */
export function detectedPolicyGroups(values) {
  return POLICY_GROUPS.filter(group => policyFields(group).some(field => values[field.key] !== undefined)
    || (group.type === 'NON_MOTOR' && ['nonMotorInsuranceName', 'nonMotorCoverageAmount']
      .some(key => values[key] !== undefined)));
}
