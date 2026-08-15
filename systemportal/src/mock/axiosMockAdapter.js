import { mockRequest } from '@/mock/portalMock';

/**
 * 将 Axios 请求体还原为 mock 路由可使用的对象。非字符串值直接保留，无法解析的字符串也原样返回，
 * 使上传或特殊请求不会因适配器强制 JSON 解析而中断。
 */
function parseRequestData(data) {
  if (!data) return {};
  if (typeof data !== 'string') return data;
  try {
    return JSON.parse(data);
  } catch (error) {
    return data;
  }
}

/**
 * 把 Axios 配置转换为本地 mockRequest 调用，并将业务响应重新包装为标准 Axios 响应结构，
 * 从而让页面、请求拦截器和真实后端联调共用同一套 API 封装。
 */
export default async function mockAxiosAdapter(config) {
  const data = await mockRequest({
    url: config.url,
    method: String(config.method || 'GET').toUpperCase(),
    data: parseRequestData(config.data),
    params: config.params || {}
  });

  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: { 'content-type': 'application/json' },
    config,
    request: null
  };
}
