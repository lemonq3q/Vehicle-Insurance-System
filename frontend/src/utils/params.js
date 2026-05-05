/**
 * 将数据对象拼接为 GET 请求的 URL 查询参数
 * @param {Object} data - 要拼接的参数对象
 * @param {boolean} useBracket - 数组参数是否使用 [] 后缀（如 perms[]=1&perms[]=2），默认 false
 * @returns {string} 拼接后的查询参数串（带?开头，无参数则返回空字符串）
 */
export function buildObjectParams(data, useBracket = false) {
  // 空对象直接返回空字符串
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    return '';
  }

  const params = [];

  // 遍历对象的每个键值对
  for (const [key, value] of Object.entries(data)) {
    // 过滤 null/undefined 空值（可选，根据业务需求调整）
    if (value === null || value === undefined || value == '') {
      continue;
    }

    // 处理数组类型的值
    if (Array.isArray(value)) {
      // 过滤数组中的空元素，只保留基础类型
      const validItems = value.filter(item => ['string', 'number', 'boolean'].includes(typeof item));
      validItems.forEach(item => {
        const paramKey = useBracket ? `${key}[]` : key;
        // 编码参数名和值，避免特殊字符
        const encodedKey = encodeURIComponent(paramKey);
        const encodedValue = encodeURIComponent(item);
        params.push(`${encodedKey}=${encodedValue}`);
      });
    } 
    else {
      // 处理普通类型的值（字符串/数字/布尔）
      if (['string', 'number', 'boolean'].includes(typeof value)) {
        const encodedKey = encodeURIComponent(key);
        const encodedValue = encodeURIComponent(value);
        params.push(`${encodedKey}=${encodedValue}`);
      }
    }
  }

  // 拼接成 ?key1=val1&key2=val2 格式，无参数则返回空
  return params.length > 0 ? `?${params.join('&')}` : '';
}

export function buildArrayParams(array, key) {
  const params = [];
  const validItems = array.filter(item => ['string', 'number', 'boolean'].includes(typeof item));
  validItems.forEach(item => {
    // 编码参数名和值，避免特殊字符
    const encodedKey = encodeURIComponent(key);
    const encodedValue = encodeURIComponent(item);
    params.push(`${encodedKey}=${encodedValue}`);
  });
  return params.length > 0 ? `?${params.join('&')}` : '';
}