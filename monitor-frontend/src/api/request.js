import axios from 'axios';
import { mockAdapter } from '@/mock/adapter';

const request = axios.create({ baseURL: process.env.VUE_APP_API_BASE_URL || '/api/monitor', timeout: 30000 });
if (process.env.VUE_APP_USE_MOCK !== 'false') request.defaults.adapter = mockAdapter;

/**
 * 为监控接口附加本地登录令牌和前端生成的请求追踪 ID；追踪 ID 可将浏览器操作与后端日志关联。
 */
request.interceptors.request.use(config => {
  const token = localStorage.getItem('monitorToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  config.headers['X-Request-Id'] = `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return config;
});

/**
 * 解开监控后端统一响应外壳，只把业务 data 交给页面；非 200 业务码转换为包含 code 和 data 的异常，
 * 网络层异常则补充稳定提示文案，供页面反馈 mixin 统一展示。
 */
request.interceptors.response.use(response => {
  const payload = response.data;
  if (Number(payload?.code) !== 200) return Promise.reject(Object.assign(new Error(payload?.message || '请求失败'), { code: payload?.code, data: payload?.data }));
  return payload.data;
}, error => Promise.reject(Object.assign(error, { message: error.message || '网络连接失败，请稍后重试' })));

export default request;
