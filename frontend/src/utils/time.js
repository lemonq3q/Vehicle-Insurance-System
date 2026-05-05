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

export function convertDateToSecondTimestamp(date) {
  if (!(date instanceof Date) || isNaN(date.getTime())) return null;
  return Math.floor(date.getTime() / 1000);
}


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

export function getTodayRange() {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  
  const end = new Date();
  end.setHours(23, 59, 59, 999);
  
  return [start, end];
}
