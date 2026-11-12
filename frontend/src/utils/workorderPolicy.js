/**
 * 保单字段与附件类别统一定义，供承保编辑、只读详情、提交载荷和 mock 共用。
 * 三种起期均对应工单主表；保费与车船税对应合并后的 quote 表，避免混入报价载荷。
 */
export const POLICY_GROUPS = [
  { type: 'COMMERCIAL', prefix: 'commercial', title: '商业保单', amount: 'commercialAmount', flag: 'insuranceType1', fileType: 'acceptInsuranceCommercial', loading: 'commercial' },
  { type: 'COMPULSORY', prefix: 'compulsory', title: '交强保单', amount: 'compulsoryAmount', flag: 'insuranceType3', fileType: 'acceptInsuranceCompulsory', loading: 'compulsory' },
  { type: 'NON_MOTOR', prefix: 'nonMotor', title: '非车险保单', amount: 'nonMotorAmount', flag: 'is_have_non_motor', fileType: 'acceptInsuranceOther', loading: 'other', maxFiles: 6 }
];

/**
 * 将一类保单展开为数据库已有的扁平字段，不引入另一套 OCR 专用表项。
 * @param {object} group 保单类别配置。
 * @returns {Array} 表单字段、标签和控件类型；交强险末尾附加车船税。
 */
export function policyFields(group) {
  const fields = [
    { key: group.prefix + 'InsurerName', label: '保险公司名称', kind: 'text', max: 200, required: false },
    { key: group.prefix + 'PolicyNo', label: '保单号码', kind: 'text', max: 100, required: false },
    { key: group.prefix + 'InsuranceStartTime', label: '保险起期', kind: 'date', required: true },
    { key: group.prefix + 'InsuranceEndTime', label: '保险终期', kind: 'date', required: false },
    { key: group.amount, label: '保费金额', kind: 'money', required: true }
  ];
  if (group.type === 'COMPULSORY') fields.push({ key: 'vehicleAndTaxAmount', label: '代收车船税金额', kind: 'money', required: false });
  if (group.type === 'NON_MOTOR') fields.push(
    { key: 'nonMotorInsuranceName', label: '非车险险种名称', kind: 'text', max: 100, required: false },
    { key: 'nonMotorCoverageAmount', label: '非车险保额', kind: 'money', required: false }
  );
  return fields;
}

/**
 * 识别历史保单资料，包括明确零元和只有起期/附件的旧记录；不把缺失金额转换为零。
 * @param {object} model 工单接口或表单模型。
 * @param {object} group 保单配置。
 * @returns {boolean} 是否存在该类历史资料。
 */
export function hasPolicy(model, group) {
  return policyFields(group).some(field => model[field.key] !== null && model[field.key] !== undefined && model[field.key] !== '')
    || (model.workorderFileList || []).some(file => file.type === group.fileType
      || (group.type === 'NON_MOTOR' && file.type === 'acceptInsuranceNonMotor'));
}

/**
 * 构建承保完整资料载荷，显式清空取消的险种和非选中的费用计算模式。
 * @param {object} model 编辑模型，金额保留十进制字符串，时间转换为 Unix 秒。
 * @param {object} flags 投保开关及上下游金额模式。
 * @returns {object} 保单、费用字段和 policyTypes；不包含报价反馈或支付历史金额。
 */
export function buildPolicyPayload(model, flags) {
  const data = { policyTypes: [], upstreamComputeType: model.upstreamComputeType, downstreamComputeType: model.downstreamComputeType };
  for (const group of POLICY_GROUPS) {
    const enabled = !!flags[group.flag];
    if (enabled) data.policyTypes.push(group.type);
    for (const field of policyFields(group)) {
      const value = model[field.key];
      data[field.key] = !enabled || value === '' || value === undefined || value === null ? null
        : field.kind === 'date' ? Math.floor(new Date(value).getTime() / 1000) : value;
    }
  }
  for (const side of ['upstream', 'downstream']) {
    for (const [name, flag, enabled] of [
      ['Commercial', 'commercial', flags.insuranceType1], ['Compulsory', 'compulsory', flags.insuranceType3],
      ['VehicleAndVesselTax', 'tax', flags.insuranceType3], ['NonMotor', 'non_motor', flags.is_have_non_motor]
    ]) {
      const amount = side + name + 'Amount';
      const percentage = side + name + 'Percentage';
      const byAmount = !!flags[side + '_' + flag];
      data[amount] = enabled && byAmount && model[amount] !== '' ? model[amount] ?? null : null;
      data[percentage] = enabled && !byAmount && model[percentage] !== '' ? model[percentage] ?? null : null;
    }
  }
  data.nonMotorInsuranceName = flags.is_have_non_motor ? model.nonMotorInsuranceName ?? null : null;
  data.nonMotorCoverageAmount = flags.is_have_non_motor ? (model.nonMotorCoverageAmount === '' ? null : model.nonMotorCoverageAmount ?? null) : null;
  return data;
}

/**
 * 提交前只要求选中保单的起期和保费；终期及其他字段、附件允许留空。
 * 已填写金额仍须符合精度范围，附件仍须符合每组数量上限。
 * @param {object} data 承保载荷，时间为 Unix 秒。
 * @param {Array} files 将随本次承保保存的完整附件列表。
 * @returns {string} 首个校验错误，空字符串表示通过。
 */
export function validatePolicies(data, files) {
  if (!data.policyTypes.length) return '请至少选择一种保单';
  if (files.filter(file => file.type === 'acceptInsuranceMisc').length > 6) return '其他单证最多上传6份';
  for (const group of POLICY_GROUPS.filter(item => data.policyTypes.includes(item.type))) {
    const groupFiles = files.filter(file => file.type === group.fileType && file.fileId);
    if (groupFiles.length > (group.maxFiles || 1)) return group.title + '最多上传' + (group.maxFiles || 1) + '份';
    for (const field of policyFields(group)) {
      const value = data[field.key];
      if (value === null || value === undefined || value === '') {
        if (field.required) return '请填写' + group.title + '的' + field.label;
        continue;
      }
      if (field.kind === 'text' && String(value).trim().length > field.max) return group.title + '的' + field.label + '输入内容过长';
      if (field.kind === 'money' && !/^\d{1,10}(\.\d{1,2})?$/.test(String(value))) return group.title + '的' + field.label + '应为非负金额，最多两位小数';
      if (field.kind === 'date' && (!Number.isSafeInteger(value) || value <= 0)) return '请选择有效的' + group.title + field.label;
    }
    const end = data[group.prefix + 'InsuranceEndTime'];
    if (end != null && end <= data[group.prefix + 'InsuranceStartTime']) return group.title + '的终期必须晚于起期';
  }
  return '';
}

