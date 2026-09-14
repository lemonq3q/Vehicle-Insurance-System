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
 * 解开监控后端统一响应外壳，只把业务 data 交给页面；非 200 业务码转换为包含 code 和 data 的异常，
 * 网络层异常则补充稳定提示文案，供页面反馈 mixin 统一展示。
 */
request.interceptors.response.use(response => {
  /* 文件下载返回 Blob，不经过统一 JSON 外壳；由页面根据响应头保存为本地文件。 */
  if (typeof Blob !== 'undefined' && response.data instanceof Blob) return response.data;
  const payload = response.data;
  const refreshedToken = response.headers?.['new-token'] || response.headers?.get?.('new-token');
  if (refreshedToken) updateToken(refreshedToken);
  if (Number(payload?.code) === 401 && unauthorizedHandler) unauthorizedHandler();
  if (Number(payload?.code) !== 200) return Promise.reject(Object.assign(new Error(payload?.msg || payload?.message || '请求失败'), { code: payload?.code, data: payload?.data }));
  return payload.data;
}, error => {
  if (Number(error.response?.status) === 401 && unauthorizedHandler) unauthorizedHandler();
  const message = error.response?.data?.msg || error.response?.data?.message || error.message || '网络连接失败，请稍后重试';
  return Promise.reject(Object.assign(error, { message }));
});

export default request;
