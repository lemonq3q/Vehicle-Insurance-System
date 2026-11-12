/**
 * 根据服务端计算的本期轮次构造跟进表单。已处理状态仍属于当前提醒周期，
 * 因此只用 activeCycle 判断是否显示险种，并回填该周期的未处理、已续保或已流失结果。
 * @param {object} row 续保列表中的工单及双险种当前周期、状态。
 * @returns {object} 表单可见性、现有结果与备注；未进入周期的险种结果为空。
 */
export function buildRenewalFollowUpState(row) {
  const commercialInCycle = Number(row.commercialActiveCycle) > 0;
  const compulsoryInCycle = Number(row.compulsoryActiveCycle) > 0;
  return {
    id: row.id,
    commercialInCycle,
    compulsoryInCycle,
    commercialStatus: commercialInCycle && [0, 1, 2].includes(row.commercialRenewalStatus)
      ? row.commercialRenewalStatus : undefined,
    compulsoryStatus: compulsoryInCycle && [0, 1, 2].includes(row.compulsoryRenewalStatus)
      ? row.compulsoryRenewalStatus : undefined,
    followUpRes: row.followUpRes ?? '',
    originalCommercialStatus: commercialInCycle ? row.commercialRenewalStatus : undefined,
    originalCompulsoryStatus: compulsoryInCycle ? row.compulsoryRenewalStatus : undefined,
    originalFollowUpRes: row.followUpRes ?? ''
  };
}

/**
 * 只提交本次改动的险种，避免未变化的“未处理”被旧服务误判为非法。
 * 单险种改为已续保/已流失时沿用既有 scope/status 契约；改回未处理或双险种
 * 同时变化时使用新版独立状态契约，确保两种不同结果仍可原子保存。
 * @param {object} form 当前跟进表单及打开时的原始状态。
 * @returns {object|null} 接口请求体；状态和备注均未变化时返回 null。
 */
export function buildRenewalFollowUpRequest(form) {
  const changed = [];
  if (form.commercialInCycle && form.commercialStatus !== form.originalCommercialStatus) {
    changed.push(['COMMERCIAL', 'commercialStatus', form.commercialStatus]);
  }
  if (form.compulsoryInCycle && form.compulsoryStatus !== form.originalCompulsoryStatus) {
    changed.push(['COMPULSORY', 'compulsoryStatus', form.compulsoryStatus]);
  }
  const followUpRes = form.followUpRes ?? '';
  if (!changed.length && followUpRes === form.originalFollowUpRes) return null;
  if (!changed.length) {
    const scope = form.commercialInCycle ? 'COMMERCIAL' : 'COMPULSORY';
    const status = form.commercialInCycle ? form.commercialStatus : form.compulsoryStatus;
    changed.push([scope, scope === 'COMMERCIAL' ? 'commercialStatus' : 'compulsoryStatus', status]);
  }
  if (changed.length === 1 && [1, 2].includes(changed[0][2])) {
    return { scope: changed[0][0], status: changed[0][2], followUpRes };
  }
  return Object.fromEntries([...changed.map(([, key, status]) => [key, status]), ['followUpRes', followUpRes]]);
}
