import axios, { EXCEL_MIME_TYPE } from "./config";
import { buildObjectParams } from "@/utils/params";

const rootUrl = "/upstream";
/**
 * * 按名称、状态和分页条件查询上游保险公司或渠道，供上游管理列表展示。
 */
export function selectUpstream(searchParams) {
  let url = rootUrl + buildObjectParams(searchParams);
  return axios.get(url);
}

/**

 * * 查询单个上游渠道的基础资料和业务配置，用于编辑页面回填。

 */
export function selectUpstreamById(id){
  let url = `${rootUrl}/${id}`;
  return axios.get(url);
}

/**

 * * 按当前筛选范围导出上游渠道 Excel，下载文件名和 Blob 释放由响应拦截器统一处理。

 */
export function getUpstreamExcel(searchParams){
  let url = `${rootUrl}/excel${buildObjectParams(searchParams)}`;
  return axios.get(url, {
    responseType: 'blob',
    headers: {
      'Accept': EXCEL_MIME_TYPE
    }
  });
}

/**

 * * 创建新的上游保险公司或渠道关系，并提交其业务区域、联系人和结算资料。

 */
export function insertUpstream(data) {
  return axios.post(rootUrl, data);
}

/**

 * * 保存上游渠道编辑表单，后端在当前企业数据边界内执行更新。

 */
export function updateUpstream(data) {
  return axios.put(rootUrl, data);
}

/**

 * * 删除指定上游渠道；若渠道仍被工单引用，具体失败原因由后端统一业务响应返回。

 */
export function deleteUpstream(id) {
  let url = `${rootUrl}?id=${id}`;
  return axios.delete(url);
}

/**

 * * 模糊查询保险公司类型的上游选项，供需要明确承保公司的表单远程检索。

 */
export function selectInusranceCompanyOptions(blurParam) {
  let url = `${rootUrl}/option/insuranceCompany?blurParam=${blurParam}`;
  return axios.get(url);
}

/**

 * * 模糊查询全部可用上游渠道的轻量选项，避免在工单页面加载完整渠道列表。

 */
export function selectUpstreamOption(blurParam){
  let url = `${rootUrl}/option?blurParam=${blurParam}`;
  return axios.get(url);
}
