/**
 * 复用三个前端统一的十进制金额计算和展示规则，避免各应用维护不同的舍入实现。
 * 该入口仅提供无状态工具，不访问接口、会话或数据库，所有结算仍以后端结果为准。
 */
export { formatMoney, sumMoney, calculatePolicyAmount, moneyShortfall, compareMoney, multiplyMoney, formatPercentage } from '../../../shared/money';
