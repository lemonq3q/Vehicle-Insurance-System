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
function isValidCardFormat(cardNumber) {
  const cardRegex = /^\d{13,19}$/;
  return cardRegex.test(cardNumber);
}

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

function isValidFormat(id) {
  const regex = /^[1-9]\d{5}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9Xx]$/;
  return regex.test(id);
}



function isValidDate(id) {
  const birthDate = id.substring(6, 14);
  const year = parseInt(birthDate.substring(0, 4), 10);
  const month = parseInt(birthDate.substring(4, 6), 10);
  const day = parseInt(birthDate.substring(6, 8), 10);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() + 1 === month && date.getDate() === day;
}

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

function isValidIdNum(id) {
  // console.log(0)
  if (typeof id !== 'string' || id.trim() === '') {
    // console.log(1)
    return true;
  }
  // console.log(isValidFormat(id), isValidDate(id), isValidChecksum(id));
  return isValidFormat(id) && isValidDate(id) && isValidChecksum(id);
}

export function judgePhoneNumber(phone){
  if (typeof phone !== 'string' || phone.trim() === '') {
    return true;
  }
  // 2. 手机号正则表达式（匹配11位中国大陆手机号）
  const phoneReg = /^1[3-9]\d{9}$/;
  
  // 4. 正则匹配
  return phoneReg.test(phone);
}

export function judgeLetterChar(str){
  // 空值不校验
  if (!str || typeof str !== 'string') return true;
  
  // 合法字符正则：数字+大小写字母+指定的所有特殊字符（需转义特殊元字符）
  const legalCharReg = /^[-0-9a-zA-Z_+=!@#$%?()^&*,.`~[\];:<>{}]*$/;
  
  // 如果字符串不匹配合法字符规则，说明包含非法字符
  return legalCharReg.test(str);
}

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

export function judgeEmail(email) {
  if (!email) return true;
  if (email == '') return true;
  // 通用标准邮箱正则
  const reg = /^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$/;
  return reg.test(email.trim());
}

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

export function isNumber(str) {
  if (!str || typeof str !== 'string') return false;
  const reg = /^-?(\d+|\d+\.\d+)$/;
  return reg.test(str.trim());
}

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