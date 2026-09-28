/**
 * 按当前用户、企业及充值订单隔离套餐意图，使 Stripe 整页回跳或刷新后仍能继续原套餐操作。
 * 数据只用于恢复用户选择，费用、权限和余额仍由后端重新校验，不作为付款或入账凭据。
 */
export function rechargeContextKey(orderId, state) {
  return `recharge-subscription:${state.user?.id}:${state.currentEnterprise?.id}:${orderId}`;
}

/**
 * 校验路由或缓存中的套餐选择，拒绝无效周期和非字符串套餐标识，防止缓存损坏触发错误提交。
 */
export function normalizeRechargeContext(value) {
  if (!value || !/^\d+$/.test(String(value.planId || ''))) return null;
  const periodCount = Number(value.periodCount);
  if (!Number.isSafeInteger(periodCount) || periodCount < 1) return null;
  return { planId: String(value.planId), periodCount, autoRenew: value.autoRenew === true || value.autoRenew === 'true', orderType: value.orderType || '' };
}

/**
 * 支付前保存当前订单的套餐意图。存储失败交由调用方提示，避免用户付款后无法继续套餐操作。
 */
export function saveRechargeContext(key, context) {
  window.sessionStorage.setItem(key, JSON.stringify(context));
}

/**
 * Stripe 返回后恢复套餐意图，缓存缺失或损坏时返回 null，页面不误显示继续操作入口。
 */
export function readRechargeContext(key) {
  try {
    const stored = JSON.parse(window.sessionStorage.getItem(key));
    return stored?.completed === true ? { completed: true } : normalizeRechargeContext(stored);
  }
  catch (error) { return null; }
}

/**
 * 消费本次支付的临时套餐选择。返回页面先取出再删除，刷新、再次访问订单或重新登录
 * 都不能重新激活确认按钮；删除失败时返回 null，优先避免历史订单重复出现购买入口。
 */
export function consumeRechargeContext(key) {
  const context = readRechargeContext(key);
  try { window.sessionStorage.removeItem(key); }
  catch (error) { return null; }
  return context?.completed ? null : context;
}
