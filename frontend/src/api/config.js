import axios from 'axios';
import Message from '@/utils/message';
import router from '@/router';
import Storage from '@/utils/storage';

export const EXCEL_MIME_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const REFRESHED_TOKEN_HEADER = 'new-token';

const notInterceptUrls = [
  '/auth/login',
  '/auth/register',
  '/auth/code',
  '/auth/forget'
];

// axios.defaults.baseURL = 'http://localhost:8080';
axios.defaults.baseURL = 'http://47.100.210.159:8080';
// axios.defaults.baseURL = 'https://47.97.126.52:443/api';
// axios.defaults.withCredentials = true;
axios.defaults.timeout = 60000; // 全局60秒超时

axios.interceptors.request.use(function (config) {
  const isIgnoreUrl = notInterceptUrls.some(item => config.url.includes(item));
  // 登陆相关接口不拦截
  if (isIgnoreUrl) {
    return config;
  }
  let token = Storage.get("token");
  if (token) {
    // 刷新token时间
    Storage.set('token', token, 60 * 60 * 24);
    config.headers.token = token;
  }
  // 标记：如果是Excel请求，记录config中（方便响应拦截器识别）
  if (config.responseType === 'blob' || config.headers.Accept === EXCEL_MIME_TYPE) {
    config.isExcelRequest = true; // 自定义标记，识别Excel请求
  }
  return config;
}, function (error) {
  Message.error("请求出错！");
  return Promise.reject(error);
});

axios.interceptors.response.use(function (response) {
  const isIgnoreUrl = notInterceptUrls.some(item => response.config.url.includes(item));
  if (isIgnoreUrl) {
    return response;
  }

  const refreshedToken = response.headers[REFRESHED_TOKEN_HEADER];
  if (refreshedToken) {
    Storage.set('token', refreshedToken, 60 * 60 * 24);
  }

  const isExcelResponse = 
    response.config.isExcelRequest || 
    response.headers['content-type']?.includes(EXCEL_MIME_TYPE);

  if (isExcelResponse) {
    handleExcelDownload(response);
    return Promise.resolve();
  }

  // 先判断response.data是否为JSON，避免非JSON响应报错
  if (response.data && typeof response.data === 'object' && 'code' in response.data) {
    if (response.data.code === 401) {
      localStorage.removeItem("token");
      localStorage.removeItem('userInfo');
      router.push('/login');
    }
    else if (response.data.code >= 400) {
      Message.warning(response.data.msg);
    }
  }
  return response;
}, function (error) {
  // 异常拦截：区分Excel请求的异常
  const isExcelRequest = error.config?.isExcelRequest;
  if (isExcelRequest) {
    Message.error("Excel下载异常");
  } else {
    Message.error("请求出错！");
  }
  return Promise.reject(error);
});

function handleExcelDownload(response) {
  try {
    // 解析文件名
    const contentDisposition = response.headers['content-disposition'];
    let fileName = '导出数据.xlsx';
    if (contentDisposition) {
      const fileNameMatch = contentDisposition.match(/filename\*=utf-8''(.*)/);
      if (fileNameMatch && fileNameMatch[1]) {
        fileName = decodeURIComponent(fileNameMatch[1]);
      }
    }

    // 创建Blob并触发下载
    const blob = new Blob([response.data], { type: EXCEL_MIME_TYPE });
    const downloadUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();

    // 清理资源
    document.body.removeChild(link);
    window.URL.revokeObjectURL(downloadUrl);
  } catch (e) {
    console.error('Excel下载异常：', e);
  }
}

export default axios;
