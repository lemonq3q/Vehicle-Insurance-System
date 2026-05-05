import axios from "./config";

const rootUrl = "/role";

export function selectAllRole(){
  return axios.get(rootUrl);
}