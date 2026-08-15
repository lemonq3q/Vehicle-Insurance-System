import axios from "./config";

const rootUrl = "/merchant";

/**

 * * 查询指定下游机构获准经营的区域，用于编辑用户或工单时限制可选业务地域。

 */
export function selectAreaByMerhcantId(id) {
  let url = `${rootUrl}/area/byMerchantId?id=${id}`;
  return axios.get(url);
}

/**

 * * 按业务区域查询可出单的保险公司，供工单录入页联动上游公司选项。

 */
export function selectInsuranceCompanyByAreaCode(areaCode){
  let url = `${rootUrl}/insurance/area?areaCode=${areaCode}`;
  return axios.get(url);
}
