import axios from 'axios';
import { mockAdapter } from '@/mock/adapter';

const request = axios.create({ baseURL: process.env.VUE_APP_API_BASE_URL || '/api/monitor', timeout: 30000 });
if (process.env.VUE_APP_USE_MOCK !== 'false') request.defaults.adapter = mockAdapter;

request.interceptors.request.use(config => {
  const token = localStorage.getItem('monitorToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  config.headers['X-Request-Id'] = `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return config;
});

request.interceptors.response.use(response => {
  const payload = response.data;
  if (Number(payload?.code) !== 200) return Promise.reject(Object.assign(new Error(payload?.message || '请求失败'), { code: payload?.code, data: payload?.data }));
  return payload.data;
}, error => Promise.reject(Object.assign(error, { message: error.message || '网络连接失败，请稍后重试' })));

export default request;
