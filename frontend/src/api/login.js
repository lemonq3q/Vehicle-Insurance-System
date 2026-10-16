import axios from "./config";

const rootUrl = "/auth";
/**
 * 使用登录页提交的账号凭据建立车险系统会话；成功响应中的令牌和用户资料由登录页写入本地状态。
 */
export function login(user){
  return axios.post(`${rootUrl}/login`, user);
}

/**
 * 通知后端注销当前令牌对应的会话；调用页面随后负责清理 Vuex 和浏览器中的本地身份信息。
 */
export function logout(){
  let url = `${rootUrl}/logout`;
  return axios.get(url);
}

/**
 * 通过 JSON POST 提交邮箱、验证码和新密码，完成忘记密码流程中的身份校验与密码重置。
 */
export function forgetPassword(data){
  return axios.post(`${rootUrl}/forget-password`, data);
}

/**
 * 为找回密码流程向指定邮箱申请一次性验证码，返回发送结果和后端定义的冷却状态。
 */
export function sendEmailCode(data){
  return axios.post(`${rootUrl}/email-code`, data);
}

/**
 * 使用 SaaS 门户签发的一次性 SSO code 换取车险系统令牌和用户上下文。
 * code 只能在回调页消费一次，失败时不得沿用浏览器中可能残留的旧车险会话。
 */
export function exchangeSsoCode(code) {
  return axios.post(`${rootUrl}/sso/exchange`, { code }, { silentAuthFailure: true });
}

/**
 * 基于当前车险登录态申请返回 SaaS 门户的一次性授权地址，使门户能够自动恢复同一用户会话。
 */
export function createPortalAuthorization() {
  return axios.post(`${rootUrl}/sso/portal-authorize`, {});
}

/**
 * 查询当前登录用户的共享账号资料，响应不含密码。
 */
export const getEmailProfile = () => axios.get(`${rootUrl}/profile`);
/**
 * 从服务端会话确定本人，仅更新普通资料，不修改邮箱身份或角色。
 */
export const updateEmailProfile = data => axios.put(`${rootUrl}/profile`, data);
/**
 * 为当前无邮箱账号发送绑定验证码，验证码只可由该用户消费。
 */
export const sendBindingEmailCode = data => axios.post(`${rootUrl}/profile/email-code`, data);
/**
 * 完成邮箱验证并设置登录密码，保留原用户 ID 和企业成员关系。
 */
export const bindEmail = data => axios.post(`${rootUrl}/profile/bind-email`, data);
