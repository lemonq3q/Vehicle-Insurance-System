import axios from "./config";
import { buildObjectParams } from "@/utils/params";

const rootUrl = "/auth";
/**
 * * 使用登录页提交的账号凭据建立车险系统会话；成功响应中的令牌和用户资料由登录页写入本地状态。
 */
export function login(user){
  return axios.post(`${rootUrl}/login`, user);
}

/**

 * * 提交注册页填写的用户资料创建待审核账号，后续是否允许进入系统由后台审批状态决定。

 */
export function register(user){
  return axios.post(`${rootUrl}/register`, user);
}

/**

 * * 通知后端注销当前令牌对应的会话；调用页面随后负责清理 Vuex 和浏览器中的本地身份信息。

 */
export function logout(){
  let url = `${rootUrl}/logout`;
  return axios.get(url);
}

/**

 * * 将邮箱、验证码和新密码编码为查询参数，完成忘记密码流程中的身份校验与密码重置。

 */
export function forgetPassword(data){
  let url = `${rootUrl}/forget${buildObjectParams(data)}`;
  return axios.get(url);
}

/**

 * * 为注册或找回密码流程向指定邮箱申请一次性验证码，返回发送结果和后端定义的冷却状态。

 */
export function getSmsCode(email){
  let url = `${rootUrl}/code?email=${email}`;
  return axios.get(url);
}

/**
 * 使用 SaaS 门户签发的一次性 SSO code 换取车险系统令牌和用户上下文。
 * code 只能在回调页消费一次，失败时不得沿用浏览器中可能残留的旧车险会话。
 */
export function exchangeSsoCode(code) {
  return axios.post(`${rootUrl}/sso/exchange`, { code });
}

/**

 * * 基于当前车险登录态申请返回 SaaS 门户的一次性授权地址，使门户能够自动恢复同一用户会话。

 */
export function createPortalAuthorization() {
  return axios.post(`${rootUrl}/sso/portal-authorize`, {});
}
