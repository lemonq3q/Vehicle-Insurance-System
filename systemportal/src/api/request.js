import axios from 'axios';
import Storage from '@/utils/storage';
import { notifyError, notifyWarning } from '@/utils/notification';
import { normalizeDateTimes } from '@/utils/dateTime';

const TOKEN_STORAGE_KEY = 'portalToken';
const TOKEN_EXPIRE_SECONDS = 60 * 60 * 24;
const REFRESHED_TOKEN_HEADER = 'new-token';
const PUBLIC_REQUESTS = [
  '/portal/auth/login',
  '/portal/auth/register',
  '/portal/auth/sms-code',
  '/portal/auth/forget-password',
  '/portal/sso/exchange',
  '/portal/finance/plans',
  '/portal/visitor-leads'
];

const request = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL || 'http://127.0.0.1:8081',
  timeout: 60000
});

let unauthorizedHandler = null;

/**

 * * 注册门户会话失效后的统一处理函数，通常由应用入口绑定到 Vuex 清理和登录页跳转。

 */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

/**

 * * 判断接口是否允许在没有门户 token 时访问，避免登录、注册、SSO 换票和官网套餐请求被错误拦截。

 */
function isPublicRequest(url = '') {
  return PUBLIC_REQUESTS.some(item => url.includes(item));
}

/**

 * * 清理过期门户令牌和用户缓存，再通知应用层重置运行时会话；该操作不直接依赖路由实例。

 */
function handleUnauthorized() {
  Storage.remove(TOKEN_STORAGE_KEY);
  Storage.remove('portalUser');
  if (unauthorizedHandler) unauthorizedHandler();
}

/**

 * * HTTP503优先返回服务不可用，其他错误优先读取失败业务码；响应体成功码不能掩盖HTTP失败。

 */
function responseStatus(payload, response) {
  if (response?.status === 503) return 503;
  const businessCode = Number(payload?.code);
  return businessCode >= 400 ? businessCode : Number(response?.status || businessCode || 0);
}

/**

 * * 优先采用后端业务消息；没有消息时按服务端错误或普通请求异常生成默认中文文案。

 */
function responseMessage(payload, status) {
  const message = String(payload?.msg || payload?.message || '').trim();
  if (message) return message;
  return status >= 500 ? '请求错误' : '请求异常';
}

/**

 * * 将 5xx 错误发布为错误通知、4xx 业务问题发布为警告，避免所有失败都使用同一严重级别。

 */
function notifyRequestError(status, message) {
  if (status >= 500) notifyError(message || '请求错误');
  else if (status >= 400) notifyWarning(message || '请求异常');
}

/**

 * * 将业务错误和HTTP错误转换为同一异常结构并统一通知，保留code、data和原响应。

 */
function rejectResponse(response, originalError) {
  const payload = normalizeDateTimes(response?.data);
  const status = responseStatus(payload, response);
  const error = originalError || new Error();
  error.message = status >= 400 ? responseMessage(payload, status) : '请求错误';
  if (status === 503 || response?.status === 503) error.message = '系统维护中，服务不可用';
  error.code = status;
  error.data = payload?.data;
  error.response = response;
  const config = response?.config || originalError?.config;
  if (status === 401 && !isPublicRequest(config?.url)) handleUnauthorized();
  /* 自动会话恢复的认证拒绝不弹通知；仍拒绝 Promise、清理失效会话，维护及业务错误照常提示。 */
  const silentAuthFailure = config?.silentAuthFailure && [400, 401, 403].includes(status);
  if (!config?.skipErrorNotification && !silentAuthFailure) {
    if (status >= 400) notifyRequestError(status, error.message);
    else notifyError(error.message);
  }
  return Promise.reject(error);
}

/**
 * 为非公开门户请求附加 token 和 Bearer 头，并滚动延长本地令牌有效期。
 * 公开接口不读取登录态，使官网和认证页面在无会话环境中仍能正常访问。
 */
request.interceptors.request.use(config => {
  if (isPublicRequest(config.url)) {
    /*
     * 匿名接口不携带任何历史认证信息。除不主动读取本地 token 外，同时清除请求实例或调用方
     * 可能预置的认证头，避免过期凭据让服务端把游客请求误判为一次失败的登录态校验。
     */
    if (config.headers) {
      delete config.headers.token;
      delete config.headers.Authorization;
    }
    return config;
  }

  const token = Storage.get(TOKEN_STORAGE_KEY);
  if (token) {
    Storage.set(TOKEN_STORAGE_KEY, token, TOKEN_EXPIRE_SECONDS);
    config.headers.token = token;
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}, error => Promise.reject(error));

/**
 * 统一处理门户响应：保存后端刷新的 token、递归规范日期格式、把业务失败转成 rejected Promise，
 * 并在 401 时清理会话。调用方可通过 skipErrorNotification 抑制公共页面的全局错误提示。
 */
request.interceptors.response.use(response => {
  const refreshedToken = response.headers?.[REFRESHED_TOKEN_HEADER]
    || response.headers?.get?.(REFRESHED_TOKEN_HEADER);
  if (refreshedToken) {
    Storage.set(TOKEN_STORAGE_KEY, refreshedToken, TOKEN_EXPIRE_SECONDS);
  }

  const payload = normalizeDateTimes(response.data);
  if (response.status >= 400) return rejectResponse(response);
  if (payload && typeof payload === 'object' && 'code' in payload) {
    if (Number(payload.code) >= 400) {
      return rejectResponse(response);
    }
  }
  return payload;
}, error => {
  return rejectResponse(error.response, error);
});

export default request;
