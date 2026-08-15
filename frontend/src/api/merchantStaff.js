import axios from './config';
import { buildObjectParams } from '@/utils/params';

const rootUrl = '/merchant-staff';

/**

 * * 按姓名、状态和分页条件查询当前企业的机构员工列表，供员工管理页面展示。

 */
export function selectMerchantStaff(searchParams) {
  return axios.get(rootUrl + buildObjectParams(searchParams));
}

/**

 * * 读取单个机构员工的资料和角色信息，用于打开编辑弹窗时回填表单。

 */
export function selectMerchantStaffById(id) {
  return axios.get(`${rootUrl}/${id}`);
}

/**

 * * 查询当前企业可分配给机构员工的角色集合，避免前端硬编码角色编号。

 */
export function selectMerchantStaffRoles() {
  return axios.get(`${rootUrl}/roles`);
}

/**

 * * 创建机构员工并提交其账号、联系方式和角色关系；后端负责企业边界及账号唯一性校验。

 */
export function insertMerchantStaff(data) {
  return axios.post(rootUrl, data);
}

/**

 * * 保存员工编辑表单中的基础资料、启用状态和角色调整，更新范围限定在当前企业内。

 */
export function updateMerchantStaff(data) {
  return axios.put(rootUrl, data);
}

/**

 * * 删除指定机构员工；调用页面在后端确认不存在受约束业务关系后刷新员工列表。

 */
export function deleteMerchantStaff(id) {
  return axios.delete(`${rootUrl}?id=${id}`);
}
