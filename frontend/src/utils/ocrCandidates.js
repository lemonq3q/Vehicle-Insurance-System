/**
 * 将证件 OCR 原始结果限制在当前表单已有字段内，生成路径与值的候选映射。
 * 空值不会覆盖已有候选；日期按后端 Unix 秒转换，非法日期忽略。此函数不修改原结果或表单。
 * @param {object} data OCR 服务返回的证件字段。
 * @param {object} fields 当前证件表单的字段白名单，基础证件可传字段别名映射。
 * @param {string} prefix 车辆证件在工单聚合对象中的路径，基础信息为空。
 * @returns {object} 以完整表单路径为键的非空候选。
 */
export function normalizeOcrCandidates(data, fields, prefix = '') {
  const result = {};
  const allowed = {
    vehicleLicense: ['licensePlate', 'vehicleType', 'ownerName', 'usageNature', 'brandModel', 'vehicleCode', 'engineCode', 'registrationDate', 'issueDate', 'seats', 'approvedLoadCapacity', 'curbWeight', 'isTransfer', 'transferDate'],
    vehicleCertificate: ['brandModel', 'vehicleType', 'vehicleCode', 'engineCode', 'seats', 'curbWeight', 'displacement', 'approvedLoadCapacity'],
    vehicleInvoice: ['invoiceAmount', 'buyerName', 'buyerIdNum', 'vehicleType', 'brandModel', 'vehicleCode', 'engineCode', 'seats', 'approvedLoadCapacity']
  };
  Object.keys(fields).forEach(key => {
    if (prefix && !allowed[prefix]?.includes(key)) return;
    let value = data?.[key];
    if (value == null || (typeof value === 'string' && !value.trim())) return;
    if (['registrationDate', 'issueDate', 'transferDate'].includes(key)) {
      value = new Date(Number(value) * 1000);
      if (!Number.isFinite(value.getTime())) return;
    }
    const target = prefix ? `${prefix}.${key}` : fields[key];
    result[target] = value;
  });
  return result;
}

/**
 * 用户确认某字段后清除该字段的所有来源候选，防止应用后继续显示小字或被一键应用重复覆盖。
 * 其余字段保留，后续重新上传并识别仍可产生新候选；不修改表单或上传附件。
 * @param {object} results 编辑器按证件来源保存的候选集合。
 * @param {string} path 已应用的工单字段路径。
 * @returns {void} 原地移除该路径，便于Vue响应式更新候选显示。
 */
export function consumeOcrCandidates(results, path) {
  Object.values(results).forEach(values => { delete values[path]; });
}

/**
 * 接收已按白名单转换的 OCR 候选，仅自动补齐当前表单空字段，已填内容交给用户确认。
 * 以识别响应到达时的表单为准，避免覆盖识别等待期间的手工录入；0 和 false 均视为已有内容。
 * @param {object} form 当前工单响应式表单，支持基础字段及一层证件对象。
 * @param {object} values normalizeOcrCandidates 产生的路径和值，不修改输入候选。
 * @returns {object} 需要人工应用的候选；自动填入的日期复制为独立对象，不在返回值中重复展示。
 */
export function fillEmptyOcrFields(form, values) {
  const pending = {};
  /*
   * 每个字段独立检查最新内容，只有空白字符串、null 和 undefined 可自动补齐。
   * 缺失目标对象时保留候选，不创建额外业务结构，也不丢弃识别值。
   */
  Object.entries(values).forEach(([path, value]) => {
    const parts = path.split('.');
    const target = parts.length === 2 ? form[parts[0]] : form;
    const key = parts[parts.length - 1];
    const current = target?.[key];
    if (target && (current == null || (typeof current === 'string' && !current.trim()))) {
      target[key] = value instanceof Date ? new Date(value.getTime()) : value;
    } else {
      pending[path] = value;
    }
  });
  return pending;
}
