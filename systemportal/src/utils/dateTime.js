const DATE_TIME_PATTERN = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?$/;

/**
 * 递归遍历接口响应，把 ISO 日期时间字符串规范为“YYYY-MM-DD HH:mm:ss”。
 * 数组和对象原结构保持不变，普通字符串及其他类型原样返回，便于所有页面直接展示后端时间。
 */
export function normalizeDateTimes(value) {
  if (typeof value === 'string') {
    const match = value.match(DATE_TIME_PATTERN);
    return match ? `${match[1]} ${match[2]}` : value;
  }
  if (Array.isArray(value)) return value.map(normalizeDateTimes);
  if (value && typeof value === 'object') {
    Object.keys(value).forEach(key => {
      value[key] = normalizeDateTimes(value[key]);
    });
  }
  return value;
}
