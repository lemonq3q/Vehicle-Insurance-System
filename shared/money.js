/* eslint-env es2020 */

/**
 * 将接口或表单十进制金额解析为整数系数和十进制倍率，供金额展示、求和及政策预览共用。
 * 不经过Number转换，保留字符串精度；空值、非有限数及异常超长输入视为无效，且不改写来源。
 * @param {number|string|null} value 元或百分比单位的原始值，兼容科学计数法。
 * @returns {{units: bigint, scale: number}|null} 精确十进制表示，或无效值标记。
 */
function parseDecimal(value) {
  if (typeof value !== 'number' && typeof value !== 'string') return null;
  const text = String(value).trim();
  if (text.length > 100) return null;
  const match = /^([+-]?)(\d*)(?:\.(\d*))?(?:e([+-]?\d+))?$/i.exec(text);
  if (!match || !(match[2] || match[3])) return null;
  const exponent = Number(match[4] || 0);
  if (Math.abs(exponent) > 100) return null;
  const fraction = match[3] || '';
  let units = BigInt(`${match[1] === '-' ? '-' : ''}${match[2] || '0'}${fraction}`);
  const scale = fraction.length - exponent;
  if (scale < 0) units *= BigInt(10) ** BigInt(-scale);
  return { units, scale: Math.max(0, scale) };
}

/**
 * 将精确分数按HALF_UP舍入到分，最后以字符串组装两位小数，避免输出阶段再次引入浮点误差。
 * 供求和与百分比预览复用；分母由内部十进制倍率构造且始终为正，负数同样按绝对值四舍五入。
 * @param {bigint} numerator 元单位分子。
 * @param {bigint} denominator 正分母。
 * @returns {string} 固定两位小数金额；舍入后的负零统一为0.00。
 */
function formatRatio(numerator, denominator) {
  const negative = numerator < BigInt(0);
  const scaled = (negative ? -numerator : numerator) * BigInt(100);
  let cents = scaled / denominator;
  if ((scaled % denominator) * BigInt(2) >= denominator) cents += BigInt(1);
  const digits = String(cents).padStart(3, '0');
  return `${negative && cents !== BigInt(0) ? '-' : ''}${digits.slice(0, -2)}.${digits.slice(-2)}`;
}

/**
 * 统一格式化只读金额；有效值（包括零与负值）保留两位小数，缺失和异常值使用调用方占位。
 * 仅用于显示，不写回表单、接口原值或影响后端结算。
 * @param {number|string|null} value 元单位金额。
 * @param {string} empty 缺失值占位，默认空串以保留未报价语义。
 * @param {object} options 展示选项，group开启千分位，trim省略整数金额的.00；默认均关闭。
 * @returns {string} 不含货币符号的金额文本。
 */
export function formatMoney(value, empty = '', { group = false, trim = false } = {}) {
  const decimal = parseDecimal(value);
  if (!decimal) return empty;
  let result = formatRatio(decimal.units, BigInt(10) ** BigInt(decimal.scale));
  if (group) result = result.replace(/\B(?=(\d{3})+\.)/g, ',');
  if (trim) result = result.replace(/\.00$/, '');
  return result;
}

/**
 * 计算订阅应付与可用余额之间的补充充值缺口，先精确相减再舍入到分，负缺口归零。
 * 输入来自后端订单试算及钱包快照；不持久化或授权扣款，空值沿用财务页面零值语义。
 * @param {number|string|null} required 订单应付金额。
 * @param {number|string|null} balance 当前钱包余额，允许欠费负数。
 * @returns {string} 非负两位小数充值缺口，非法数据返回空串。
 */
export function moneyShortfall(required, balance) {
  const charge = parseDecimal(required ?? 0);
  const wallet = parseDecimal(balance ?? 0);
  if (!charge || !wallet) return '';
  const scale = Math.max(charge.scale, wallet.scale);
  const difference = charge.units * BigInt(10) ** BigInt(scale - charge.scale)
    - wallet.units * BigInt(10) ** BigInt(scale - wallet.scale);
  return formatRatio(difference > BigInt(0) ? difference : BigInt(0), BigInt(10) ** BigInt(scale));
}

/**
 * 按原始十进制精度比较订单应付与余额，避免用舍入后的显示缺口决定是否需要充值。
 * 输入仅参与只读比较，不产生扣款或状态变更；缺失值沿用财务零值语义，非法数据不作大小推断。
 * @param {number|string|null} left 待比较的第一项金额。
 * @param {number|string|null} right 待比较的第二项金额。
 * @returns {number|null} 小于、等于、大于分别为-1、0、1，非法值为null。
 */
export function compareMoney(left, right) {
  const first = parseDecimal(left ?? 0);
  const second = parseDecimal(right ?? 0);
  if (!first || !second) return null;
  const scale = Math.max(first.scale, second.scale);
  const firstUnits = first.units * BigInt(10) ** BigInt(scale - first.scale);
  const secondUnits = second.units * BigInt(10) ** BigInt(scale - second.scale);
  return firstUnits > secondUnits ? 1 : firstUnits < secondUnits ? -1 : 0;
}

/**
 * 模拟后端按金额、数量及周期分母计算费用，避免mock先进行浮点乘除再格式化造成分位偏差。
 * 只负责无状态算术，调用者决定业务是否允许该数量或周期；分母缺失、零或负值返回空串。
 * @param {number|string} amount 单位金额。
 * @param {number|string} quantity 数量或剩余天数。
 * @param {number|string} divisor 周期天数等正分母，默认1。
 * @returns {string} 最终HALF_UP到分的金额文本，非法输入为空串。
 */
export function multiplyMoney(amount, quantity, divisor = 1) {
  const base = parseDecimal(amount);
  const count = parseDecimal(quantity);
  const period = parseDecimal(divisor);
  if (!base || !count || !period || period.units <= BigInt(0)) return '';
  return formatRatio(base.units * count.units * BigInt(10) ** BigInt(period.scale),
    period.units * BigInt(10) ** BigInt(base.scale + count.scale));
}

/**
 * 统一只读百分比展示，接口数值已是百分比单位，不再乘100；最多保留两位并省略末尾零。
 * 用于环比和政策比例文本，空值按零展示，不回写用户输入或改变后端比例计算精度。
 * @param {number|string|null} value 接口百分比数值。
 * @returns {string} 不带百分号的显示文本。
 */
export function formatPercentage(value) {
  return formatMoney(value ?? 0, '0.00').replace(/0+$/, '').replace(/\.$/, '');
}

/**
 * 按十进制公共倍率精确累加各险种金额，最终统一舍入到分；不会发生字符串拼接或二进制尾差。
 * 与后端BigDecimal先汇总再展示的顺序一致，不提前舍入分项；跳过缺失/非法项，全部缺失返回空串。
 * @param {Array<number|string|null>} values 同一业务合计的原始金额。
 * @returns {string} 两位小数合计或空字符串，不修改输入。
 */
export function sumMoney(values) {
  const decimals = values.map(parseDecimal).filter(Boolean);
  if (!decimals.length) return '';
  const scale = Math.max(...decimals.map(value => value.scale));
  const total = decimals.reduce((sum, value) => sum + value.units * BigInt(10) ** BigInt(scale - value.scale), BigInt(0));
  return formatRatio(total, BigInt(10) ** BigInt(scale));
}

/**
 * 预览上下游政策费用，沿用后端税前/税后公式：类型1除以106，其他类型除以100。
 * 全程采用整数分数计算，仅在最终展示时HALF_UP到分，避免1.005等舍入临界值被浮点误差截低。
 * @param {number|string} percentage 用户输入的百分比数值，10表示10%。
 * @param {number|string} amount 原始保费，单位元。
 * @param {number|string} type 计算方式，1表示扣除6%增值税后计算。
 * @returns {string} 两位小数预览；输入未就绪或非法时为短横线，不产生持久化变更。
 */
export function calculatePolicyAmount(percentage, amount, type) {
  const rate = parseDecimal(percentage);
  const premium = parseDecimal(amount);
  if (!rate || !premium) return '-';
  return formatRatio(rate.units * premium.units,
    BigInt(10) ** BigInt(rate.scale + premium.scale) * BigInt(Number(type) === 1 ? 106 : 100));
}
