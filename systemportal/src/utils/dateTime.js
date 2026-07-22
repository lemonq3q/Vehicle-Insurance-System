const DATE_TIME_PATTERN = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?$/;

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
