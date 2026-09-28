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
 * 统一处理两种错误传输方式：业务code>=400或HTTP失败均拒绝Promise并保留code、data和response。
 * 401清理会话，4xx警告、5xx错误；业务错误不再作为成功响应进入页面后续流程。
 * 网络故障没有HTTP状态，按错误提示；调用方仍可在catch中处理自己的加载状态。
 * HTTP503优先按服务不可用处理并固定维护文案，避免错误body导致误清理登录态。
 */
function rejectResponse(response, originalError) {
  const payload = response?.data;
  const businessCode = Number(payload?.code);
  const code = response?.status === 503 ? 503 : businessCode >= 400 ? businessCode : Number(response?.status || 0);
  const error = originalError || new Error();
  error.message = payload?.msg || payload?.message || (code >= 500 || code === 0 ? '请求错误' : '请求异常');
  if (code === 503 || response?.status === 503) error.message = '系统维护中，服务不可用';
  Object.assign(error, { code, data: payload?.data, response });
  if (code === 401) {
    Storage.remove('token');
    Storage.remove('userInfo');
    router.push('/login');
  } else if (code >= 500 || code === 0) {
    Message.error(error.message);
  } else {
    Message.warning(error.message);
  }
  return Promise.reject(error);
}

/**
 * 成功响应继续保持Axios原始响应和Excel下载契约；先检查JSON业务错误，再判断是否需要下载，
 * 避免导出请求返回错误JSON时被误当成Excel保存；刷新令牌仍使用原有滚动会话规则。
 */
axios.interceptors.response.use(function (response) {
  const refreshedToken = response.headers[REFRESHED_TOKEN_HEADER];
  if (refreshedToken) Storage.set('token', refreshedToken, 60 * 60 * 24);
  if (Number(response.data?.code) >= 400 || response.status >= 400) return rejectResponse(response);
  const isExcelResponse = response.config.isExcelRequest
    || response.headers['content-type']?.includes(EXCEL_MIME_TYPE);
  if (isExcelResponse) {
    handleExcelDownload(response);
    return Promise.resolve();
  }
  return response;
}, function (error) {
  return rejectResponse(error.response, error);
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
