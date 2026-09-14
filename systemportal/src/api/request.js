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

 * * 优先读取统一业务响应 code，缺失时回退 HTTP 状态，形成后续异常处理的统一数字口径。

 */
function responseStatus(payload, response) {
  return Number(payload?.code || response?.status || 0);
}

/**

 * * 优先采用后端业务消息；没有消息时按服务端错误或普通请求异常生成默认中文文案。

 */
function responseMessage(payload, status) {
  const message = String(payload?.msg || '').trim();
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

 * * 把统一响应中的非成功 code 转换为可被页面 catch 的 Error，同时保留业务 code、data 和原响应。

 */
function createBusinessError(payload, response) {
  const status = responseStatus(payload, response);
  const error = new Error(responseMessage(payload, status));
  error.code = payload.code;
  error.data = payload.data;
  error.response = response;
  return error;
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
  if (payload && typeof payload === 'object' && 'code' in payload) {
    if (Number(payload.code) === 401 && !isPublicRequest(response.config?.url)) {
      handleUnauthorized();
    }
    if (Number(payload.code) >= 400) {
      const businessError = createBusinessError(payload, response);
      if (!response.config?.skipErrorNotification) {
        notifyRequestError(Number(payload.code), businessError.message);
      }
      return Promise.reject(businessError);
    }
  }
  return payload;
}, error => {
  const payload = error.response?.data;
  const status = responseStatus(payload, error.response);
  if (status === 401 && !isPublicRequest(error.config?.url)) handleUnauthorized();

  if (status >= 400) {
    error.message = responseMessage(payload, status);
    error.code = payload?.code || status;
    if (!error.config?.skipErrorNotification) notifyRequestError(status, error.message);
  } else {
    error.message = '请求错误';
    if (!error.config?.skipErrorNotification) notifyError(error.message);
  }
  return Promise.reject(error);
});

export default request;
