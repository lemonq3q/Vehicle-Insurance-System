/**
 * 将充值流程来源归一到业务入口，跳过充值创建页和订单详情等中间步骤。
 * 保留正常入口的查询条件；仅允许已匹配的门户路由，外站或失效地址返回充值列表。
 * @param {string} source 来源完整路径或当前标签页保存的历史路径
 * @param {object} router 门户路由实例，用于校验并解析来源
 * @returns {string} 可直接用于返回导航的业务入口路径
 */
export function normalizeRechargeReturnPath(source, router) {
  if (typeof source !== 'string' || !source.startsWith('/portal/')) return '/portal/finance/recharges';
  const route = router.resolve(source);
  if (!route.matched.length) return '/portal/finance/recharges';
  if (['finance-recharge', 'finance-subscription-order-detail'].includes(route.name)) return '/portal/finance/subscription';
  if (route.name === 'finance-order-detail') return '/portal/finance/orders';
  if (route.name === 'finance-recharge-detail') return '/portal/finance/recharges';
  return route.fullPath;
}
