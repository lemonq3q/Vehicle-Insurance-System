const pad = value => String(value).padStart(2, '0');

export const formatDate = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

export const defaultMonthRange = () => {
  const end = new Date();
  const start = new Date(end);
  start.setMonth(start.getMonth() - 1);
  return `${formatDate(start)} - ${formatDate(end)}`;
};

export const rangeParams = value => {
  const normalized = String(value || '').replace(' 至 ', ' - ');
  const [startTime, endTime] = normalized.split(' - ');
  return {
    startTime: startTime ? `${startTime} 00:00:00` : '',
    endTime: endTime ? `${endTime} 23:59:59` : ''
  };
};
