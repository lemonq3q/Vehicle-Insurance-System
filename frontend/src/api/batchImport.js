import axios, { EXCEL_MIME_TYPE } from './config';

/** 三种业务共用单次导入上限，服务端仍独立校验，防止绕过页面限制。 */
export const MAX_IMPORT_ROWS = 10000;
export const MAX_IMPORT_FILE_BYTES = 20 * 1024 * 1024;
const rootUrl = '/merchant/batch-import';

/**
 * 一次上传完成模板结构校验、逐行业务校验和正式批量写入；模板不合格时后端直接返回警告且不写库。
 * @param {'upstream'|'downstream'|'workorder'} businessType 导入业务类型
 * @param {File} file 用户选择的原始 xlsx 文件
 */
export function submitBatchImport(businessType, file) {
  if (businessType === 'workorder') {
    return axios.post('/workorder/batch-import', buildFormData(file), multipartConfig());
  }
  return axios.post(`${rootUrl}/${businessType}`, buildFormData(file), multipartConfig());
}

/**
 * 下载与当前版本校验契约配套的模板；走现有鉴权与文件响应拦截器，避免 OSS 旧模板滞后。
 * @param {'upstream'|'downstream'|'workorder'} businessType 要导入的业务。
 * @returns {Promise} xlsx 文件响应，统一拦截器完成保存。
 */
export function downloadBatchImportTemplate(businessType) {
  return axios.get(`/import-templates/${businessType}`, {
    responseType: 'blob', headers: { Accept: EXCEL_MIME_TYPE }
  });
}

/** 将文件包装为后端 @RequestParam("file") 对应的 multipart 请求体。 */
function buildFormData(file) {
  const formData = new FormData();
  formData.append('file', file);
  return formData;
}

/** 独立生成请求配置，避免两个阶段共享可变配置对象。 */
function multipartConfig() {
  return { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 };
}
