import axios from "./config";

const rootUrl = "/ocr";
/**
 * 把已上传的证件文件信息和识别类型提交给后端 OCR 服务。
 * 返回的结构化字段由工单录入页面选择性回填，原始图片仍按文件模块的生命周期管理。
 */
export function imgRecognition(systemFile, type) {
  let url = `${rootUrl}/${type}`
  return axios.post(url, systemFile);
}
