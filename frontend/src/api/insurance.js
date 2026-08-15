import axios from "./config";

const rootUrl = "/insurance";
/**
 * * 查询系统维护的全部险种基础资料，供工单报价和承保信息表单构建险种选项。
 */
export function selectAllInsurance(){
  let url = `${rootUrl}/all`;
  return axios.get(url);
}
