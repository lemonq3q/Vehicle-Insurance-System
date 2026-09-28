import axios from 'axios';
import { getToken, updateToken } from '@/auth/session';

const request = axios.create({ baseURL: process.env.VUE_APP_API_BASE_URL || '/api/monitor', timeout: 30000 });

let unauthorizedHandler = null;

/** 注册会话失效处理器，使请求模块无需直接依赖路由实例。 */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

/**
 * 为监控接口附加本地登录令牌和前端生成的请求追踪 ID；追踪 ID 可将浏览器操作与后端日志关联。
 */
request.interceptors.request.use(config => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  config.headers['X-Request-Id'] = `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return config;
});

/**
 * 将HTTP失败与响应体业务失败统一为code、data、response完整的异常，交给现有页面反馈机制。
 * HTTP非2xx不能被响应体code=200掩盖；503优先固定维护提示，其余业务错误码优先，401执行会话失效回调。
 */
function rejectResponse(response, originalError) {
  const payload = response?.data;
  const businessCode = Number(payload?.code);
  const code = response?.status === 503 ? 503 : businessCode >= 400 ? businessCode : Number(response?.status || 0);
  const error = originalError || new Error();
  error.message = payload?.msg || payload?.message || (code >= 500 ? '请求错误' : '请求异常');
  if (code === 503 || response?.status === 503) error.message = '系统维护中，服务不可用';
  if (!response) error.message = originalError?.message || '网络连接失败，请稍后重试';
  Object.assign(error, { code, data: payload?.data, response });
  if (code === 401 && unauthorizedHandler) unauthorizedHandler();
  return Promise.reject(error);
}

/**
 * 正常请求保持原data及Blob契约；两类失败共用同一异常处理器，避免页面按传输方式产生不同反馈。
 */
request.interceptors.response.use(response => {
  /* 文件下载返回 Blob，不经过统一 JSON 外壳；由页面根据响应头保存为本地文件。 */
  if (typeof Blob !== 'undefined' && response.data instanceof Blob) return response.data;
  const payload = response.data;
  const refreshedToken = response.headers?.['new-token'] || response.headers?.get?.('new-token');
  if (refreshedToken) updateToken(refreshedToken);
  if (Number(payload?.code) !== 200 || response.status >= 400) return rejectResponse(response);
  return payload.data;
}, error => {
  return rejectResponse(error.response, error);
});

export default request;
