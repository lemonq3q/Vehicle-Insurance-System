import axios, { EXCEL_MIME_TYPE } from "./config";
import { buildObjectParams } from "@/utils/params";

const rootUrl = "/downstream";
export function selectDownstream(searchParams) {
  let url = rootUrl + buildObjectParams(searchParams);
  return axios.get(url);
}

export function selectDownstreamById(id){
  let url = `${rootUrl}/${id}`;
  return axios.get(url);
}

export function getDownstreamExcel(searchParams){
  let url = `${rootUrl}/excel${buildObjectParams(searchParams)}`;
  return axios.get(url, {
    responseType: 'blob',
    headers: {
      'Accept': EXCEL_MIME_TYPE
    }
  });
}

export function insertDownstream(data) {
  return axios.post(rootUrl, data);
}

export function updateDownstream(data) {
  return axios.put(rootUrl, data);
}

export function deleteDownstream(id) {
  let url = `${rootUrl}?id=${id}`;
  return axios.delete(url);
}

export function selectDownstreamOption(blurParam){
  let url = `${rootUrl}/option?blurParam=${blurParam}`;
  return axios.get(url);
}