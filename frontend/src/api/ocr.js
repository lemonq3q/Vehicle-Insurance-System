import axios from "./config";
import { mockPolicyRecognition, mockPolicyRecognitionStatus } from '@/mock/policyOcrMock';

const rootUrl = "/ocr";
/**
 * 把已上传的证件文件信息和识别类型提交给后端 OCR 服务。
 * 返回的结构化字段由工单录入页面选择性回填，原始图片仍按文件模块的生命周期管理。
 */
export function imgRecognition(systemFile, type) {
  let url = `${rootUrl}/${type}`
  // 证件识别与上传共用局部失败恢复，由上传组件唯一提示。
  return axios.post(url, systemFile, { skipErrorNotification: true });
}

/**
 * 为已经直传 OSS 的承保附件提交后台识别任务，立即获取任务 ID。
 * mock 模式复用提交与轮询契约，避免等待模型期间保持长连接。
 * @param {string|number} workorderId 当前编辑的工单 ID。
 * @param {string|number} fileId 后端登记的系统文件 ID。
 * @returns {Promise<object>} 包含 jobId 的统一响应。
 */
export function policyRecognition(workorderId, fileId) {
  if (process.env.VUE_APP_WORKORDER_POLICY_MOCK === 'true') return mockPolicyRecognition(workorderId, fileId);
  return axios.post(`${rootUrl}/policy`, { workorderId, fileId }, {
    skipErrorNotification: true
  });
}

/**
 * 查询已提交的承保 OCR 任务，成功和失败都通过同一状态接口返回。
 * 每次请求独立结束，由页面按一秒间隔轮询，不重复上传文件或识别。
 * @param {string} jobId 提交接口返回的任务标识。
 * @returns {Promise<object>} 包含 status、索引和最终 result 的统一响应。
 */
export function policyRecognitionStatus(jobId) {
  if (process.env.VUE_APP_WORKORDER_POLICY_MOCK === 'true') return mockPolicyRecognitionStatus(jobId);
  return axios.get(`${rootUrl}/policy/tasks/${encodeURIComponent(jobId)}`, { skipErrorNotification: true });
}
