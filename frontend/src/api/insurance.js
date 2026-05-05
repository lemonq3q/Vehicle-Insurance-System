import axios from "./config";

const rootUrl = "/insurance";
export function selectAllInsurance(){
  let url = `${rootUrl}/all`;
  return axios.get(url);
}