import { jsonStrToObj } from "./convert";

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

export function isHasPerm(perm){
  let user = jsonStrToObj(localStorage.getItem("userInfo"));
  return user.perms.includes(perm);
}

export function getUserInfo(){
  return jsonStrToObj(localStorage.getItem("userInfo"));
}