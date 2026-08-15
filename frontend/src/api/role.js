import axios from "./config";

const rootUrl = "/role";

/**

 * * 查询当前账号有权查看和分配的角色列表，供用户新增、编辑及审批页面生成角色选项。

 */
export function selectAllRole(){
  return axios.get(rootUrl);
}
