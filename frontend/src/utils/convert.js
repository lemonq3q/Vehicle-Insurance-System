export function objToJsonStr(obj) {
  try {
    return JSON.stringify(obj, null);
  } catch (error) {
    return null;
  }
}


export function jsonStrToObj(jsonStr) {
  try {
    return JSON.parse(jsonStr);
  } catch (error) {
    return null;
  }
}

const options1 = [
  {
    label: "0",
    value: "0"
  },
  {
    label: "300",
    value: "300"
  },
  {
    label: "500",
    value: "500"
  },
  {
    label: "1000",
    value: "1000"
  },
  {
    label: "2000",
    value: "2000"
  }
];

export function getOptionsJson(){
  return objToJsonStr(options1);
}

export function transNumStrToNum(str){
  if (str == '' || str == undefined || str == null){
    return null;
  }
  return Number(str);
}

/**
 * 提取对象中的所有非空属性，剔除空属性后返回新对象
 * @param {Object} source - 源对象（待筛选的对象）
 * @param {boolean} [deep=false] - 是否深度筛选（处理嵌套对象）
 * @returns {Object} 仅包含非空属性的新对象
 */
export function extractNonEmptyProps(source, deep = false) {
    // 初始化返回的新对象
    const result = {};

    // 校验入参：非对象类型直接返回空对象
    if (typeof source !== 'object' || source === null) {
        return result;
    }

    // 定义“非空”判断函数（规则可按需调整）
    function isNotEmpty(value) {
        // 排除 null/undefined
        if (value === null || value === undefined) return false;
        // // 排除空字符串（含全空格）
        // if (typeof value === 'string' && value.trim() === '') return false;
        // // 排除空数组
        // if (Array.isArray(value) && value.length === 0) return false;
        // 排除空对象（不含任何可枚举属性）
        if (typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length === 0) return false;
        // 其他情况视为非空（数字0、布尔false等保留）
        return true;
    }

    // 遍历源对象的所有自身可枚举属性
    for (const key in source) {
        // 修复：使用 Object.prototype.hasOwnProperty.call 替代直接调用
        if (Object.prototype.hasOwnProperty.call(source, key)) {
            const value = source[key];

            // 非空判断
            if (isNotEmpty(value)) {
                // 深度筛选：如果值是嵌套对象，递归提取其非空属性
                if (deep && typeof value === 'object' && !Array.isArray(value)) {
                    const nestedNonEmpty = extractNonEmptyProps(value, true);
                    // 仅当嵌套对象提取后非空时，才添加到结果中
                    if (Object.keys(nestedNonEmpty).length > 0) {
                        result[key] = nestedNonEmpty;
                    }
                } else {
                    // 普通属性：直接添加到结果中
                    result[key] = value;
                }
            }
        }
    }

    return result;
}