import axios, { EXCEL_MIME_TYPE } from "./config";
import { buildObjectParams } from "@/utils/params";

const rootUrl = "/user";
export function selectUser(searchParams) {
  let url = rootUrl + buildObjectParams(searchParams);
  return axios.get(url);
}

export function selectSystemUser(searchParams){
  let url = rootUrl + '/system' + buildObjectParams(searchParams);
  return axios.get(url);
}

export function selectNotApprovalUser(searchParams){
  let url = rootUrl + '/approval/not' + buildObjectParams(searchParams);
  return axios.get(url);
}

export function selectUserById(id){
  let url = `${rootUrl}/${id}`;
  return axios.get(url);
}

export function getUserExcel(searchParams){
  let url = `${rootUrl}/excel${buildObjectParams(searchParams)}`;
  return axios.get(url, {
    responseType: 'blob',
    headers: {
      'Accept': EXCEL_MIME_TYPE
    }
  });
}

export function insertUser(data) {
  return axios.post(rootUrl, data);
}

export function updateUser(data) {
  return axios.put(rootUrl, data);
}

export function deleteUser(id) {
  let url = `${rootUrl}?id=${id}`;
  return axios.delete(url);
}

export function selectUserOptionByMerchantId(merchantId){
  let url = `${rootUrl}/option/merchantId?id=${merchantId}`;
  return axios.get(url);
}

export function selectUserOption(blurParam){
  let url = `${rootUrl}/option?blurParam=${blurParam}`;
  return axios.get(url);
}

export function updatePassword(user){
  let url = `${rootUrl}/password`;
  return axios.put(url, user);
}

export function approvalUser(id){
  let url = `${rootUrl}/approval?id=${id}`;
  return axios.put(url);
}

export function selectPersonalUser(){
  let url = `${rootUrl}/personal`;
  return axios.get(url);
}

export function selectSystemUserOptions(blurParam){
  let url = `${rootUrl}/option/system?blurParam=${blurParam}`;
  return axios.get(url);
}