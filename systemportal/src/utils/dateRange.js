/**
 * * 将月日数值补齐为两位，供门户日期查询参数保持稳定格式。
 */
const pad = value => String(value).padStart(2, '0');

/**

 * * 将 Date 转换为本地年月日文本，不引入 UTC 时区偏移。

 */
export const formatDate = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

/**

 * * 生成从当前日期向前一个自然月的默认筛选文本，供订单和流水页面初始化日期组件。

 */
export const defaultMonthRange = () => {
  const end = new Date();
  const start = new Date(end);
  start.setMonth(start.getMonth() - 1);
  return `${formatDate(start)} - ${formatDate(end)}`;
};

/**

 * * 将日期组件的区间文本转换为后端查询使用的日初和日末时间，兼容“至”和短横线两种分隔符。

 */
export const rangeParams = value => {
  const normalized = String(value || '').replace(' 至 ', ' - ');
  const [startTime, endTime] = normalized.split(' - ');
  return {
    startTime: startTime ? `${startTime} 00:00:00` : '',
    endTime: endTime ? `${endTime} 23:59:59` : ''
  };
};
