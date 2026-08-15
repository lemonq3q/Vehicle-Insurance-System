/**
 * 将后端统一返回的秒级时间戳格式化为本地日期文本。
 * 非数字或无效日期返回空串，调用页面可直接用于表格展示而无需额外判空。
 */
export function formatSecondTimestamp(timestamp, format = 'YYYY-MM-DD HH:mm:ss') {
  // 1. 校验入参：必须是数字，且转换为毫秒级
  if (typeof timestamp !== 'number' || isNaN(timestamp)) {
    return '';
  }
  const msTimestamp = timestamp * 1000;
  const date = new Date(msTimestamp);

  // 2. 处理无效时间（如时间戳为0）
  if (date.toString() === 'Invalid Date') {
    return '';
  }

  // 3. 提取时间部分，补零（确保月份/日期/小时等是两位数）
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0'); // 月份从0开始，需+1
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');

  // 4. 替换格式占位符
  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds);
}

/**

 * * 把日期选择器产生的 Date 转换成后端接口使用的秒级时间戳；无效日期返回 null。

 */
export function convertDateToSecondTimestamp(date) {
  if (!(date instanceof Date) || isNaN(date.getTime())) return null;
  return Math.floor(date.getTime() / 1000);
}


/**


 * * 生成包含今天在内、向前回溯 365 天的完整日边界，供列表快捷筛选使用。


 */
export function getLastYearRange() {
  const end = new Date();
  // 设置结束时间为当天 23:59:59.999
  end.setHours(23, 59, 59, 999);
  
  const start = new Date();
  // 先往前推365天
  start.setTime(start.getTime() - 3600 * 1000 * 24 * 365);
  // 设置开始时间为当天 00:00:00.000
  start.setHours(0, 0, 0, 0);
  
  return [start, end];
}

/**

 * * 生成从昨天零点到今天 23:59:59.999 的查询区间，对应页面“最近一天”快捷条件。

 */
export function getLastDayRange() {
  const end = new Date();
  // 设置结束时间为当天 23:59:59.999
  end.setHours(23, 59, 59, 999);
  
  const start = new Date();
  start.setTime(start.getTime() - 3600 * 1000 * 24 * 1);
  // 设置开始时间为当天 00:00:00.000
  start.setHours(0, 0, 0, 0);
  
  return [start, end];
}

/**

 * * 生成包含今天在内、向前回溯 30 天的完整日区间，作为近一月的固定天数口径。

 */
export function getLastMonthRange(){
  const end = new Date();
  // 设置结束时间为当天 23:59:59.999
  end.setHours(23, 59, 59, 999);
  
  const start = new Date();
  start.setTime(start.getTime() - 3600 * 1000 * 24 * 30);
  // 设置开始时间为当天 00:00:00.000
  start.setHours(0, 0, 0, 0);
  
  return [start, end];
}

/**

 * * 生成包含今天在内、向前回溯 7 天的完整日区间，作为近一周快捷筛选条件。

 */
export function getLastWeekRange(){
  const end = new Date();
  // 设置结束时间为当天 23:59:59.999
  end.setHours(23, 59, 59, 999);
  
  const start = new Date();
  start.setTime(start.getTime() - 3600 * 1000 * 24 * 7);
  // 设置开始时间为当天 00:00:00.000
  start.setHours(0, 0, 0, 0);
  
  return [start, end];
}

/**

 * * 返回当前本地日期从零点到日末的完整边界，用于只查询今日产生的业务记录。

 */
export function getTodayRange() {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  
  const end = new Date();
  end.setHours(23, 59, 59, 999);
  
  return [start, end];
}
