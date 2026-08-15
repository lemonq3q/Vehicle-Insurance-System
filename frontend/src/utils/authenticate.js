import { jsonStrToObj } from "./convert";

/**
 * 把后端返回的“一级菜单:二级菜单:操作”权限编码转换成菜单可见性集合。
 * 同一菜单可能对应多个操作权限，因此使用 Set 去重；返回的两个数组直接供 Vuex 登录模块和菜单组件使用。
 *
 * @param {string[]} perms 当前用户的权限编码列表。
 * @returns {{firstMenu: string[], secondMenu: string[]}} 去重后的一级、二级菜单标识。
 */
export function handlePermsToMenu(perms){
  let firstMenu = new Set();
  let secondMenu = new Set();
  for (let i=0 ; i<perms.length; i++){
    let splitPerm = perms[i].split(':');
    firstMenu.add(splitPerm[0]);
    secondMenu.add(splitPerm[1]);
  }
  let menuPerms = {
    firstMenu: Array.from(firstMenu),
    secondMenu: Array.from(secondMenu)
  }
  return menuPerms;
}

/**
 * 判断当前浏览器会话中的用户是否拥有指定操作权限。
 * 用户信息来自登录成功后写入的 localStorage；该方法用于按钮级展示控制，真正的数据权限仍由后端校验。
 *
 * @param {string} perm 需要检查的完整权限编码。
 * @returns {boolean} 用户权限列表是否包含该编码。
 */
export function isHasPerm(perm){
  let user = jsonStrToObj(localStorage.getItem("userInfo"));
  return user.perms.includes(perm);
}

/**
 * 从本地会话中读取并反序列化当前车险系统用户信息。
 * 页面通过该入口取得用户名、角色和权限，避免各组件重复处理 JSON 字符串转换。
 *
 * @returns {object|null} 登录用户信息；未建立会话时返回转换工具定义的空值。
 */
export function getUserInfo(){
  return jsonStrToObj(localStorage.getItem("userInfo"));
}
