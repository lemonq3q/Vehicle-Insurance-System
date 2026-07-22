import axios from 'axios';
import Storage from '@/utils/storage';
import { notifyError, notifyWarning } from '@/utils/notification';
import { normalizeDateTimes } from '@/utils/dateTime';

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
  baseURL: process.env.VUE_APP_API_BASE_URL || 'http://127.0.0.1:8081',
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

function responseStatus(payload, response) {
  return Number(payload?.code || response?.status || 0);
}

function responseMessage(payload, status) {
  const message = String(payload?.msg || '').trim();
  if (message) return message;
  return status >= 500 ? '请求错误' : '请求异常';
}

function notifyRequestError(status, message) {
  if (status >= 500) notifyError(message || '请求错误');
  else if (status >= 400) notifyWarning(message || '请求异常');
}

function createBusinessError(payload, response) {
  const status = responseStatus(payload, response);
  const error = new Error(responseMessage(payload, status));
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

  const payload = normalizeDateTimes(response.data);
  if (payload && typeof payload === 'object' && 'code' in payload) {
    if (Number(payload.code) === 401) handleUnauthorized();
    if (Number(payload.code) >= 400) {
      const businessError = createBusinessError(payload, response);
      notifyRequestError(Number(payload.code), businessError.message);
      return Promise.reject(businessError);
    }
  }
  return payload;
}, error => {
  const payload = error.response?.data;
  const status = responseStatus(payload, error.response);
  if (status === 401) handleUnauthorized();

  if (status >= 400) {
    error.message = responseMessage(payload, status);
    error.code = payload?.code || status;
    notifyRequestError(status, error.message);
  } else {
    error.message = '请求错误';
    notifyError(error.message);
  }
  return Promise.reject(error);
});

export default request;
