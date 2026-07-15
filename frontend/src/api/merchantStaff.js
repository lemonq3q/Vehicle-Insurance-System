import axios from './config';
import { buildObjectParams } from '@/utils/params';

const rootUrl = '/merchant-staff';

export function selectMerchantStaff(searchParams) {
  return axios.get(rootUrl + buildObjectParams(searchParams));
}

export function selectMerchantStaffById(id) {
  return axios.get(`${rootUrl}/${id}`);
}

export function selectMerchantStaffRoles() {
  return axios.get(`${rootUrl}/roles`);
}

export function insertMerchantStaff(data) {
  return axios.post(rootUrl, data);
}

export function updateMerchantStaff(data) {
  return axios.put(rootUrl, data);
}

export function deleteMerchantStaff(id) {
  return axios.delete(`${rootUrl}?id=${id}`);
}
