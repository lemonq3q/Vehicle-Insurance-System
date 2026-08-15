import Message from "./message";

// // 实现Luhn算法（模10算法）验证卡号有效性
// function validateCardNumber(cardNumber) {
//   cardNumber = cardNumber.replace(/\s+/g, '');

  
//   if (!/^\d+$/.test(cardNumber)) {
//     return false;
//   }
  
//   let sum = 0;
//   let shouldDouble = true; 
  
//   for (let i = cardNumber.length - 1; i >= 0; i--) {
//     let digit = parseInt(cardNumber.charAt(i));
    
//     if (shouldDouble) {
//       digit *= 2;
//       if (digit > 9) {
//         digit -= 9;
//       }
//     }
    
//     sum += digit;
//     shouldDouble = !shouldDouble;
//   }
//   return (sum % 10) === 0;
// }

// 验证卡号格式（长度13-19位纯数字）
/**
 * * 检查去除空格后的银行卡号是否为 13 至 19 位数字；当前业务只做格式校验，不执行 Luhn 校验。
 */
function isValidCardFormat(cardNumber) {
  const cardRegex = /^\d{13,19}$/;
  return cardRegex.test(cardNumber);
}

/**

 * * 校验可选银行卡字段：空值允许提交，非空值先移除输入空格，再按项目采用的长度规则验证。

 */
function isBankCard(cardNumber) {
  // 空数值不做判定
  if (typeof cardNumber !== 'string' || cardNumber.trim() === '') {
    return true;
  }
  
  // 移除所有空格后再进行校验
  const cleanCardNumber = cardNumber.replace(/\s+/g, '');
  // 暂时移除Luhn校验
  return isValidCardFormat(cleanCardNumber);
}

/**

 * * 验证大陆十八位身份证的行政区、出生日期文本和校验位基础格式，不负责判断日期真实性。

 */
function isValidFormat(id) {
  const regex = /^[1-9]\d{5}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9Xx]$/;
  return regex.test(id);
}



/**



 * * 从身份证第 7 至 14 位还原出生日期，并利用 Date 回卷结果排除不存在的年月日。



 */
function isValidDate(id) {
  const birthDate = id.substring(6, 14);
  const year = parseInt(birthDate.substring(0, 4), 10);
  const month = parseInt(birthDate.substring(4, 6), 10);
  const day = parseInt(birthDate.substring(6, 8), 10);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() + 1 === month && date.getDate() === day;
}

/**

 * * 按 GB 11643 的加权因子计算身份证末位校验码，支持末位大写或已标准化的 X。

 */
function isValidChecksum(id) {
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
  const checksumMap = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'];
  let sum = 0;
  for (let i = 0; i < 17; i++) {
    sum += parseInt(id[i], 10) * weights[i];
  }
  const mod = sum % 11;
  const checksum = checksumMap[mod];
  return checksum === id[17];
}

/**

 * * 组合身份证格式、真实日期和校验码三项规则；该字段允许为空，由表单的必填规则另行控制。

 */
function isValidIdNum(id) {
  // console.log(0)
  if (typeof id !== 'string' || id.trim() === '') {
    // console.log(1)
    return true;
  }
  // console.log(isValidFormat(id), isValidDate(id), isValidChecksum(id));
  return isValidFormat(id) && isValidDate(id) && isValidChecksum(id);
}

/**

 * * 校验可选的大陆手机号码，非空时必须为 1 开头的 11 位有效号段格式。

 */
export function judgePhoneNumber(phone){
  if (typeof phone !== 'string' || phone.trim() === '') {
    return true;
  }
  // 2. 手机号正则表达式（匹配11位中国大陆手机号）
  const phoneReg = /^1[3-9]\d{9}$/;
  
  // 4. 正则匹配
  return phoneReg.test(phone);
}

/**

 * * 限制账号类输入只能包含数字、英文字母和系统允许的特殊字符，空值交给必填规则处理。

 */
export function judgeLetterChar(str){
  // 空值不校验
  if (!str || typeof str !== 'string') return true;
  
  // 合法字符正则：数字+大小写字母+指定的所有特殊字符（需转义特殊元字符）
  const legalCharReg = /^[-0-9a-zA-Z_+=!@#$%?()^&*,.`~[\];:<>{}]*$/;
  
  // 如果字符串不匹配合法字符规则，说明包含非法字符
  return legalCharReg.test(str);
}

/**

 * * 校验名称和备注类输入，仅允许汉字、字母、数字及约定特殊字符，防止提交不可识别字符。

 */
export function judgeStrChar(str) {
  // 空值不校验
  if (!str || typeof str !== 'string') return true;
  
  // 合法字符正则：
  // \u4e00-\u9fa5 匹配全体汉字
  // -0-9a-zA-Z_+=!@#$%?()^&*,.`~[\];:<>{} 匹配原有指定字符（已转义特殊元字符）
  const legalCharReg = /^[\u4e00-\u9fa5-0-9a-zA-Z_+=!@#$%?()^&*,.`~[\];:<>{}]*$/;
  
  // 校验字符串是否仅包含合法字符
  return legalCharReg.test(str);
}

/**

 * * 校验可选邮箱地址的基本“本地部分@域名”结构，空值不在这里判为错误。

 */
export function judgeEmail(email) {
  if (!email) return true;
  if (email == '') return true;
  // 通用标准邮箱正则
  const reg = /^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$/;
  return reg.test(email.trim());
}

/**

 * * 将邮箱布尔校验适配为 Element Plus 表单需要的 Promise 校验器。

 */
export function vaildateEmail(rule, value){
  return new Promise((resolve, reject) => {
    if (judgeEmail(value)){
      resolve();
    }
    else{
      reject('邮箱格式错误');
    }
  });
}

/**

 * * 将手机号规则包装为异步表单校验器，并在失败时返回可直接展示的中文原因。

 */
export function validatePhoneNumber(rule, value) {
  return new Promise((resolve, reject) => {
    if (judgePhoneNumber(value)){
      resolve();
    }
    else{
      reject('手机号码格式错误');
    }
  });
}

/**

 * * 为账号、编码等非中文字段提供 Element Plus 非法字符校验器。

 */
export function validateStr(rule, value) {
  return new Promise((resolve, reject) => {
    if (judgeLetterChar(value)){
      resolve();
    }
    else{
      reject('包含非法字符');
    }
  });
}

/**

 * * 为姓名、机构名称和备注等允许中文的字段提供非法字符校验器。

 */
export function validateText(rule, value) {
  return new Promise((resolve, reject) => {
    if (judgeStrChar(value)){
      resolve();
    }
    else{
      reject('包含非法字符');
    }
  });
}

/**

 * * 对可选身份证字段执行完整格式、出生日期和校验位验证，空值直接通过。

 */
export function validateIdNum(rule, value) {
  return new Promise((resolve, reject) => {
    if(value == undefined || value == null || value == ''){
      resolve();
    }
    if (isValidIdNum(value)){
      resolve();
    }
    else{
      reject('身份证号格式错误');
    }
  });
}

/**

 * * 将银行卡格式规则包装成表单 Promise 校验器，失败时阻止当前表单提交。

 */
export function validateBankCard(rule, value) {
  return new Promise((resolve, reject) => {
    if (isBankCard(value)){
      resolve();
    }
    else{
      reject('银行卡号格式错误');
    }
  });
}

/**

 * * 判断字符串是否为普通整数或小数，不接受科学计数法和仅含空白的输入。

 */
export function isNumber(str) {
  if (!str || typeof str !== 'string') return false;
  const reg = /^-?(\d+|\d+\.\d+)$/;
  return reg.test(str.trim());
}

/**

 * * 判断金额输入能否转换为数值且不超过系统十位整数上限；空值与 NaN 均判为无效。

 */
export function isAmount(str){
  if(str === '' || str === null || str === undefined){
    return false;
  }
  let amount = Number(str);
  if(isNaN(amount)){
    return false;
  }
  if(amount > 9999999999){
    return false;
  }
  return true;
}

/**

 * * 将金额范围规则包装为 Element Plus 表单校验器，供保费、报价等金额字段复用。

 */
export function validateAmount(rule, value) {
  return new Promise((resolve, reject) => {
    if (isAmount(value)){
      resolve();
    }
    else{
      reject('不合规数值');
    }
  });
}

/**
 * 过滤上传列表中超过限制的原始文件，并在存在超限项时只提示一次。
 * 返回列表保持 Element Plus upload item 结构，页面可直接覆盖当前 file-list。
 */
export function validFileSize(uploadFiles, maxSize = 20 * 1024 * 1024){
  const validFiles = []; 
  let hasInvalidFile = false; 
  for (const item of uploadFiles) {
    const rawFile = item.raw;
    if (!rawFile || rawFile.size <= maxSize) {
      validFiles.push(item);
    } else {
      hasInvalidFile = true;
    }
  }
  if (hasInvalidFile) {
    Message.warning(`文件大小超出${~~(maxSize / 1024 / 1024)}MB限制`);
  }
  return validFiles;
}
