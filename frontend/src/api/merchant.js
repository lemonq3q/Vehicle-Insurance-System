import axios from "./config";

const rootUrl = "/merchant";

export function selectAreaByMerhcantId(id) {
  let url = `${rootUrl}/area/byMerchantId?id=${id}`;
  return axios.get(url);
}

export function selectInsuranceCompanyByAreaCode(areaCode){
  let url = `${rootUrl}/insurance/area?areaCode=${areaCode}`;
  return axios.get(url);
}