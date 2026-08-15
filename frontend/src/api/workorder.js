import axios, { EXCEL_MIME_TYPE } from "./config";
import { buildObjectParams } from "@/utils/params";

const rootUrl = "/workorder";
/**
 * * 按工单号、客户、状态、负责人和时间范围分页查询工单主列表。
 */
export function selectWorkorder(searchParams) {
  let url = rootUrl + buildObjectParams(searchParams);
  return axios.get(url);
}

/**

 * * 查询进入续保周期的工单，供续保工作台按个人或全企业范围展示待跟进客户。

 */
export function selectRenew(searchParams) {
  let url = `${rootUrl}/renew${buildObjectParams(searchParams)}`;
  return axios.get(url);
}

/**

 * * 获取当前用户和全企业的续保待办数量，用于页头角标与通知中心汇总。

 */
export function selectRenewCount() {
  let url = `${rootUrl}/renew/count`;
  return axios.get(url);
}

/**

 * * 关闭指定工单的续保提醒，使其不再计入后续提醒列表，但不删除工单和保单资料。

 */
export function disableRenewReminder(id) {
  return axios.put(`${rootUrl}/renew/${id}/disable-reminder`);
}

/**

 * * 查询工单详情及其车辆、投保、文件和流程信息，为详情与编辑页面提供完整数据。

 */
export function selectWorkorderById(id){
  let url = `${rootUrl}/${id}`;
  return axios.get(url);
}

/**

 * * 按当前工单列表条件导出 Excel，响应交由请求配置中的文件下载流程处理。

 */
export function getWorkorderExcel(searchParams){
  let url = `${rootUrl}/excel${buildObjectParams(searchParams)}`;
  return axios.get(url, {
    responseType: 'blob',
    headers: {
      'Accept': EXCEL_MIME_TYPE
    }
  });
}

/**

 * * 提交新工单及其关联车辆、证件和投保资料；后端在同一业务流程中建立关联记录。

 */
export function insertWorkorder(data) {
  return axios.post(rootUrl, data);
}

/**

 * * 更新工单基础资料，不改变报价、承保等后续流程节点的专属字段。

 */
export function updateWorkorderBaseInfo(data) {
  let url = `${rootUrl}/baseInfo`;
  return axios.put(url, data);
}

/**

 * * 删除指定工单及后端定义的级联资料，页面在用户确认后调用并刷新列表。

 */
export function deleteWorkorder(id) {
  let url = `${rootUrl}?id=${id}`;
  return axios.delete(url);
}

/**

 * * 提交接单动作和负责人信息，使待接工单进入后续处理阶段。

 */
export function acceptWorkorder(data) {
  let url = `${rootUrl}/accept`;
  return axios.put(url, data);
}

/**

 * * 保存工单报价信息并推进报价节点，金额与险种明细由后端执行业务校验。

 */
export function updateQuotation(data) {
  let url = `${rootUrl}/quotation`;
  return axios.put(url, data);
}

/**

 * * 更新无需级联重建关联资料的工单字段，用于流程页面的小范围状态或备注调整。

 */
export function updateNoCascade(data) {
  let url = `${rootUrl}/noCascade`;
  return axios.put(url, data);
}

/**

 * * 保存承保结果、保单资料和相关状态，使工单完成承保阶段的数据沉淀。

 */
export function updateAcceptInsurance(data) {
  let url = `${rootUrl}/acceptInsurance`;
  return axios.put(url, data);
}
