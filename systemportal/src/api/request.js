import axios from 'axios';
import Storage from '@/utils/storage';

const TOKEN_STORAGE_KEY = 'portalToken';
const TOKEN_EXPIRE_SECONDS = 60 * 60 * 24;
const REFRESHED_TOKEN_HEADER = 'new-token';
const AUTH_WHITE_LIST = [
  '/portal/auth/login',
  '/portal/auth/register',
  '/portal/auth/sms-code',
  '/portal/auth/forget-password'
];

const request = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL || '',
  timeout: 60000
});

let unauthorizedHandler = null;

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

function isAuthRequest(url = '') {
  return AUTH_WHITE_LIST.some(item => url.includes(item));
}

function handleUnauthorized() {
  Storage.remove(TOKEN_STORAGE_KEY);
  Storage.remove('portalUser');
  if (unauthorizedHandler) unauthorizedHandler();
}

function createBusinessError(payload, response) {
  const error = new Error(payload.msg || '请求失败');
  error.code = payload.code;
  error.data = payload.data;
  error.response = response;
  return error;
}

request.interceptors.request.use(config => {
  if (isAuthRequest(config.url)) return config;

  const token = Storage.get(TOKEN_STORAGE_KEY);
  if (token) {
    Storage.set(TOKEN_STORAGE_KEY, token, TOKEN_EXPIRE_SECONDS);
    config.headers.token = token;
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}, error => Promise.reject(error));

request.interceptors.response.use(response => {
  const refreshedToken = response.headers?.[REFRESHED_TOKEN_HEADER]
    || response.headers?.get?.(REFRESHED_TOKEN_HEADER);
  if (refreshedToken) {
    Storage.set(TOKEN_STORAGE_KEY, refreshedToken, TOKEN_EXPIRE_SECONDS);
  }

  const payload = response.data;
  if (payload && typeof payload === 'object' && 'code' in payload) {
    if (Number(payload.code) === 401) handleUnauthorized();
    if (Number(payload.code) >= 400) {
      return Promise.reject(createBusinessError(payload, response));
    }
  }
  return payload;
}, error => {
  if (Number(error.response?.status) === 401) handleUnauthorized();

  const responseMessage = error.response?.data?.msg;
  if (responseMessage) {
    error.message = responseMessage;
  } else if (error.code === 'ECONNABORTED') {
    error.message = '请求超时，请稍后重试';
  } else if (!error.message) {
    error.message = '网络请求失败，请稍后重试';
  }
  return Promise.reject(error);
});

export default request;
