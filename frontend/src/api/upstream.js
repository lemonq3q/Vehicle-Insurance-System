import axios, { EXCEL_MIME_TYPE } from "./config";
import { buildObjectParams } from "@/utils/params";

const rootUrl = "/upstream";
export function selectUpstream(searchParams) {
  let url = rootUrl + buildObjectParams(searchParams);
  return axios.get(url);
}

export function selectUpstreamById(id){
  let url = `${rootUrl}/${id}`;
  return axios.get(url);
}

export function getUpstreamExcel(searchParams){
  let url = `${rootUrl}/excel${buildObjectParams(searchParams)}`;
  return axios.get(url, {
    responseType: 'blob',
    headers: {
      'Accept': EXCEL_MIME_TYPE
    }
  });
}

export function insertUpstream(data) {
  return axios.post(rootUrl, data);
}

export function updateUpstream(data) {
  return axios.put(rootUrl, data);
}

export function deleteUpstream(id) {
  let url = `${rootUrl}?id=${id}`;
  return axios.delete(url);
}

export function selectInusranceCompanyOptions(blurParam) {
  let url = `${rootUrl}/option/insuranceCompany?blurParam=${blurParam}`;
  return axios.get(url);
}

export function selectUpstreamOption(blurParam){
  let url = `${rootUrl}/option?blurParam=${blurParam}`;
  return axios.get(url);
}