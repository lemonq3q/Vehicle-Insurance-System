import axios, { EXCEL_MIME_TYPE } from "./config";
import { buildObjectParams } from "@/utils/params";

const rootUrl = "/workorder";
export function selectWorkorder(searchParams) {
  let url = rootUrl + buildObjectParams(searchParams);
  return axios.get(url);
}

export function selectRenew(searchParams) {
  let url = `${rootUrl}/renew${buildObjectParams(searchParams)}`;
  return axios.get(url);
}

export function selectRenewCount() {
  let url = `${rootUrl}/renew/count`;
  return axios.get(url);
}

export function disableRenewReminder(id) {
  return axios.put(`${rootUrl}/renew/${id}/disable-reminder`);
}

export function selectWorkorderById(id){
  let url = `${rootUrl}/${id}`;
  return axios.get(url);
}

export function getWorkorderExcel(searchParams){
  let url = `${rootUrl}/excel${buildObjectParams(searchParams)}`;
  return axios.get(url, {
    responseType: 'blob',
    headers: {
      'Accept': EXCEL_MIME_TYPE
    }
  });
}

export function insertWorkorder(data) {
  return axios.post(rootUrl, data);
}

export function updateWorkorderBaseInfo(data) {
  let url = `${rootUrl}/baseInfo`;
  return axios.put(url, data);
}

export function deleteWorkorder(id) {
  let url = `${rootUrl}?id=${id}`;
  return axios.delete(url);
}

export function acceptWorkorder(data) {
  let url = `${rootUrl}/accept`;
  return axios.put(url, data);
}

export function updateQuotation(data) {
  let url = `${rootUrl}/quotation`;
  return axios.put(url, data);
}

export function updateNoCascade(data) {
  let url = `${rootUrl}/noCascade`;
  return axios.put(url, data);
}

export function updateAcceptInsurance(data) {
  let url = `${rootUrl}/acceptInsurance`;
  return axios.put(url, data);
}
