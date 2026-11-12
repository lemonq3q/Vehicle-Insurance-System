import { formatMoney } from './money';

/**
 * 从接口的可选身份字段提取有效展示文本；空白、null和未返回字段均视为缺失。
 * @param {*} value 接口字段值。
 * @returns {string} 去首尾空白的文本，不改变原始接口数据。
 */
function displayText(value) {
  return value == null ? '' : String(value).trim();
}

/**
 * 工单列表只展示处理人的首选身份，避免姓名下再拼接内部微信账号撑宽表格。
 * 姓名缺失时依次采用绑定邮箱、微信昵称、手机号和账号；兼容旧服务未返回新增
 * 字段的情况，最后从旧handleUser组合文本中取第一段有效身份，不进行额外请求。
 * @param {object} row 工单查询接口原始行，身份信息来自所属处理账号。
 * @returns {string} 单一身份文本；没有任何可用信息时返回空字符串。
 */
export function formatWorkorderHandler(row) {
  const candidates = [row.handleUserName, row.handleUserEmail, row.handleUserWechatNickname,
    row.handleUserPhone, row.handleUserUsername];
  const identity = candidates.map(displayText).find(Boolean);
  if (identity) return identity;
  return displayText(row.handleUser).split(/\r?\n/).map(displayText).find(Boolean) || '';
}

/**
 * 区分“尚未完成报价”与“报价完成但政策费用为零”。成功报价会记录quotationTime
 * 并进入4至10的核保、支付、承保或完成阶段；保留历史报价时间的重新报价工单
 * 也属于曾完成报价。新建/待报价/首次报价失败且无成功时间的工单不补零。
 * @param {object} row 工单接口行，状态可为数字或数字字符串。
 * @returns {boolean} 是否已有成功报价依据，不改变工单流程状态。
 */
export function hasCompletedQuotation(row) {
  const status = Number(row.status);
  const quotedAt = Number(row.quotationTime);
  return (Number.isFinite(quotedAt) && quotedAt > 0)
    || (Number.isInteger(status) && status >= 4 && status <= 10);
}

/**
 * 仅将已报价工单缺失的费用转换为展示零；已有金额（包括零、负值和精确字符串）
 * 统一显示到分，不重新计算、不取绝对值，也不写回原始费用字段。
 * @param {object} row 当前工单，用于判定是否完成报价。
 * @param {number|string|null} amount 接口返回的上下游政策费用合计。
 * @returns {string} 两位小数金额、展示用0.00，或未报价时的空字符串。
 */
export function formatWorkorderPolicyFee(row, amount) {
  if (displayText(amount) !== '') return formatMoney(amount);
  return hasCompletedQuotation(row) ? '0.00' : '';
}

/**
 * 为各工单列表生成一致的只读展示字段，保留接口原有字段供详情及业务操作使用。
 * 在页面组装表格行时合并返回值，保证总览、待接单、派单及续保入口采用同一规则。
 * @param {object} row 未修改的工单接口数据。
 * @returns {object} 处理人、上下游费用的三个界面展示字段。
 */
export function buildWorkorderListDisplay(row) {
  return {
    handleUserDisplay: formatWorkorderHandler(row),
    upstreamPolicyFeeDisplay: formatWorkorderPolicyFee(row, row.upstreamSumAmount),
    downstreamPolicyFeeDisplay: formatWorkorderPolicyFee(row, row.downstreamSumAmount)
  };
}
