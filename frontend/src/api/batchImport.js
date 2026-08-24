import axios from './config';

const rootUrl = '/merchant/batch-import';

/**
 * 获取后端配置文件中维护的上游和下游官方模板 OSS 地址。
 */
export function getBatchImportTemplates() {
  return axios.get(`${rootUrl}/templates`);
}

/**
 * 一次上传完成模板结构校验、逐行业务校验和正式批量写入；模板不合格时后端直接返回警告且不写库。
 * @param {'upstream'|'downstream'} businessType 导入业务类型
 * @param {File} file 用户选择的原始 xlsx 文件
 */
export function submitBatchImport(businessType, file) {
  return axios.post(`${rootUrl}/${businessType}`, buildFormData(file), multipartConfig());
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
