import axios, { EXCEL_MIME_TYPE } from "./config";
import { buildObjectParams } from "@/utils/params";

const rootUrl = "/user";
/**
 * * 按姓名、机构、角色和状态等条件分页查询业务用户，供普通用户管理列表展示。
 */
export function selectUser(searchParams) {
  let url = rootUrl + buildObjectParams(searchParams);
  return axios.get(url);
}

/**

 * * 查询系统级用户列表，与机构业务用户分开管理，供管理员页面使用。

 */
export function selectSystemUser(searchParams){
  let url = rootUrl + '/system' + buildObjectParams(searchParams);
  return axios.get(url);
}

/**

 * * 查询尚未完成后台审批的注册用户，供审批页面逐项确认是否允许进入系统。

 */
export function selectNotApprovalUser(searchParams){
  let url = rootUrl + '/approval/not' + buildObjectParams(searchParams);
  return axios.get(url);
}

/**

 * * 读取单个用户的基础资料、所属机构和角色权限，为详情或编辑表单提供数据。

 */
export function selectUserById(id){
  let url = `${rootUrl}/${id}`;
  return axios.get(url);
}

/**

 * * 按当前用户列表筛选条件导出 Excel，文件响应由全局 Axios 拦截器统一下载。

 */
export function getUserExcel(searchParams){
  let url = `${rootUrl}/excel${buildObjectParams(searchParams)}`;
  return axios.get(url, {
    responseType: 'blob',
    headers: {
      'Accept': EXCEL_MIME_TYPE
    }
  });
}

/**

 * * 创建业务用户并提交其所属机构、角色和联系方式，后端负责账号唯一性与企业边界校验。

 */
export function insertUser(data) {
  return axios.post(rootUrl, data);
}

/**

 * * 保存用户资料、状态及角色调整，不在前端直接推断权限变更结果。

 */
export function updateUser(data) {
  return axios.put(rootUrl, data);
}

/**

 * * 删除指定用户；存在负责工单等业务关系时由后端返回是否允许删除及具体原因。

 */
export function deleteUser(id) {
  let url = `${rootUrl}?id=${id}`;
  return axios.delete(url);
}

/**

 * * 查询指定下游机构下的轻量用户选项，供工单分派等表单限定候选人员范围。

 */
export function selectUserOptionByMerchantId(merchantId){
  let url = `${rootUrl}/option/merchantId?id=${merchantId}`;
  return axios.get(url);
}

/**

 * * 按输入关键字模糊查询当前企业可选用户，供远程搜索下拉框使用。

 */
export function selectUserOption(blurParam){
  let url = `${rootUrl}/option?blurParam=${blurParam}`;
  return axios.get(url);
}

/**

 * * 提交当前用户或管理员发起的密码更新数据，具体旧密码校验和加密由后端完成。

 */
export function updatePassword(user){
  let url = `${rootUrl}/password`;
  return axios.put(url, user);
}

/**

 * * 审批指定待审核账号，使其进入可正常登录的用户状态。

 */
export function approvalUser(id){
  let url = `${rootUrl}/approval?id=${id}`;
  return axios.put(url);
}

/**

 * * 查询当前登录用户的个人资料，用于个人中心展示并避免接受任意用户 ID 越权读取。

 */
export function selectPersonalUser(){
  let url = `${rootUrl}/personal`;
  return axios.get(url);
}

/**

 * * 模糊查询系统用户轻量选项，供需要选择系统负责人或管理员的页面使用。

 */
export function selectSystemUserOptions(blurParam){
  let url = `${rootUrl}/option/system?blurParam=${blurParam}`;
  return axios.get(url);
}
