import request from './request';
import { requestLogMock } from '@/mock/requestLogs';
/**
 * 请求日志复用已包含监控前缀的统一请求层，地址仅填写模块内路径，避免重复 /monitor。
 * 显式环境开关仅用于独立前端联调；真实请求的失败通知由统一请求层负责。
 */
export const requestLogApi = {
  page: params => process.env.VUE_APP_REQUEST_LOG_MOCK === 'true' ? requestLogMock.page(params) : request.get('/request-logs', { params }),
  detail: id => process.env.VUE_APP_REQUEST_LOG_MOCK === 'true' ? requestLogMock.detail(id) : request.get(`/request-logs/${encodeURIComponent(id)}`),
};
