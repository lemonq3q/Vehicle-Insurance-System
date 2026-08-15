import axios, { EXCEL_MIME_TYPE } from "./config";
import { buildObjectParams } from "@/utils/params";

const rootUrl = "/downstream";
/**
 * * 按页面筛选条件分页查询下游合作机构，结果用于下游机构管理列表。
 */
export function selectDownstream(searchParams) {
  let url = rootUrl + buildObjectParams(searchParams);
  return axios.get(url);
}

/**

 * * 查询指定下游机构的基础资料、区域和结算配置，为编辑页面提供完整回填数据。

 */
export function selectDownstreamById(id){
  let url = `${rootUrl}/${id}`;
  return axios.get(url);
}

/**

 * * 按当前列表筛选条件导出下游机构 Excel；二进制响应由全局拦截器识别并触发下载。

 */
export function getDownstreamExcel(searchParams){
  let url = `${rootUrl}/excel${buildObjectParams(searchParams)}`;
  return axios.get(url, {
    responseType: 'blob',
    headers: {
      'Accept': EXCEL_MIME_TYPE
    }
  });
}

/**

 * * 新增下游合作机构及其业务属性，后端负责当前企业范围、编码唯一性和必填信息校验。

 */
export function insertDownstream(data) {
  return axios.post(rootUrl, data);
}

/**

 * * 保存下游机构编辑结果，包括名称、联系人、经营区域等可维护资料。

 */
export function updateDownstream(data) {
  return axios.put(rootUrl, data);
}

/**

 * * 删除指定下游机构；存在关联工单或用户时由后端业务约束决定是否允许删除。

 */
export function deleteDownstream(id) {
  let url = `${rootUrl}?id=${id}`;
  return axios.delete(url);
}

/**

 * * 按用户输入关键字模糊查询轻量下游选项，供工单和人员表单的远程下拉框使用。

 */
export function selectDownstreamOption(blurParam){
  let url = `${rootUrl}/option?blurParam=${blurParam}`;
  return axios.get(url);
}
