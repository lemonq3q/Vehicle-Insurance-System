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
  '/auth/forget',
  '/auth/sso/exchange'
];

axios.defaults.baseURL = process.env.VUE_APP_API_BASE_URL || 'http://localhost:8080';
// axios.defaults.withCredentials = true;
axios.defaults.timeout = 60000; // 全局60秒超时

/**
 * 在普通业务请求发出前补充车险系统登录令牌，并延长本地令牌的有效时间。
 * 登录、注册、验证码和 SSO 换票接口尚未建立车险会话，因此必须跳过鉴权；Excel 请求则额外打标，
 * 让响应拦截器能够把二进制响应作为文件下载，而不是按统一 JSON 结果处理。
 */
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

/**
 * 统一处理车险后端响应：接收后端滚动刷新后的令牌、执行 Excel 下载，并识别统一响应中的业务错误。
 * 401 会清除已失效的本地会话并回到登录页；其他业务错误只展示提示，原始响应仍返回给调用页面，
 * 以便页面依据接口数据继续处理自己的加载态和交互状态。
 */
axios.interceptors.response.use(function (response) {
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
    const status = Number(error.response?.status || 0);
    const message = error.response?.data?.msg
      || (status >= 500 ? "请求错误" : "请求异常");
    if (status >= 500 || status === 0) {
      Message.error(message);
    } else {
      Message.warning(message);
    }
  }
  return Promise.reject(error);
});

/**
 * 将后端 Excel 响应转换为浏览器下载任务。
 * 文件名优先读取 Content-Disposition 中的 UTF-8 名称，缺失时使用“导出数据.xlsx”；下载完成后立即
 * 移除临时链接并释放 Object URL，避免频繁导出时持续占用浏览器内存。
 *
 * @param {import('axios').AxiosResponse<Blob>} response 标记为 Excel 请求的 Axios 响应。
 */
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
