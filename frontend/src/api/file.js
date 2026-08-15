import Message from "@/utils/message";
import axios from "./config"
import axiosLib from "axios";

const rootUrl = "/file";
const ossRequest = axiosLib.create({
  baseURL: "",
  timeout: 60000
});

/**
 * 通过车险后端中转上传普通文件，使用 multipart/form-data 保留原始文件内容和名称。
 * 该入口供不走 OSS 直传的兼容页面使用，返回系统文件记录供业务表单关联。
 */
export function upload(file) {
  const formData = new FormData();
  formData.append('file', file);
  return axios.post(rootUrl, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
}

/**
 * 把文件名、后缀、大小和 MIME 类型提交给后端，申请受权限控制且短时有效的 OSS PUT 地址。
 * 后端同时预创建文件信息，前端不得自行拼接对象存储路径。
 */
export function createOssUploadSign(fileMetadata) {
  return axios.post(`${rootUrl}/oss/sign`, null, {
    params: {
      fileName: fileMetadata.fileName,
      suffix: fileMetadata.suffix,
      size: fileMetadata.size,
      contentType: fileMetadata.mimeType
    }
  });
}

/**
 * 使用独立、无业务 baseURL 和鉴权拦截器的 Axios 实例把原始文件直接写入 OSS 签名地址。
 * Content-Type 必须与签名申请保持一致，缺失时使用通用二进制类型。
 */
export async function uploadToSignedUrl(signedUrl, file, contentType) {
  return ossRequest.put(signedUrl, file, {
    headers: {
      'Content-Type': contentType || 'application/octet-stream'
    }
  });
}

/**
 * 编排 OSS 直传完整流程：提取文件元数据、向后端申请签名、校验签名响应，再上传原始文件。
 * 任一阶段失败都会转换为页面可理解的中文异常；成功时返回后端文件信息和本地元数据，
 * 供工单或证件表单建立业务关联，而不是额外生成一条上传流水。
 */
export async function uploadToOss(file) {
  const metadata = extractFileMetadata(file);
  let signRes;

  try {
    signRes = await createOssUploadSign(metadata);
  } catch (error) {
    throw new Error('获取上传凭证失败，请重试');
  }

  const signData = signRes?.data;
  if (signData?.code !== 200 || !signData?.data?.signedUrl || !signData?.data?.fileInfo) {
    throw new Error(signData?.msg || '获取上传凭证失败，请重试');
  }

  try {
    await uploadToSignedUrl(
      signData.data.signedUrl,
      file,
      signData.data.contentType || metadata.mimeType
    );
  } catch (error) {
    throw new Error('上传到OSS失败，请重试');
  }

  return {
    ...signData.data,
    metadata
  };
}

/**

 * * 从浏览器 File 对象提取签名接口需要的元数据；无文件名、大小或类型时提供稳定兜底值。

 */
export function extractFileMetadata(file) {
  const originalName = file?.name || `upload_${Date.now()}`;
  const dotIndex = originalName.lastIndexOf('.');
  const suffix = dotIndex >= 0 ? originalName.substring(dotIndex + 1) : '';

  return {
    fileName: originalName,
    suffix,
    size: file?.size || 0,
    mimeType: file?.type || 'application/octet-stream'
  };
}

/**
 * 下载远程文件或已有 blob/data 地址，并统一确定浏览器保存文件名。
 * 远程地址先读取为 Blob 以获得一致的下载行为；失败时由消息组件提示，临时 Object URL 会及时释放。
 *
 * @param {string} url 文件地址（远程 URL 或 blob/data URL）。
 * @param {string} [name] 下载后的文件名，缺失时从 URL 提取。
 */
export function downloadByUrl(url, name) {
  const fileName = name || extractFileNameFromUrl(url);

  if (url.startsWith('blob:') || url.startsWith('data:')) {
    downloadBlob(url, fileName);
    return;
  }

  fetchRemoteFileAsBlob(url)
    .then(blob => {
      const blobUrl = URL.createObjectURL(blob);
      downloadBlob(blobUrl, fileName);
      URL.revokeObjectURL(blobUrl);
    })
    .catch(() => {
      Message.error('下载失败');
    });
}

/**
 * 通过临时隐藏链接触发浏览器保存 Blob 或 Data URL，并在点击后立即移除 DOM 节点。
 *
 * @param {string} blobUrl 浏览器可访问的 blob 或 data URL。
 * @param {string} fileName 用户最终看到的保存文件名。
 */
function downloadBlob(blobUrl, fileName) {
  try {
    const link = document.createElement('a');
    link.href = blobUrl;
    link.download = fileName; 
    link.style.display = 'none'; 
    document.body.appendChild(link);
    link.click(); 
    document.body.removeChild(link);
  } catch (error) {
    throw new Error('创建下载链接失败：' + error.message);
  }
}

/**
 * @param {string} url - 文件地址
 * @returns {string} 提取的文件名
 */
function extractFileNameFromUrl(url) {
  const pureUrl = url.split('?')[0].split('#')[0];
  const fileName = pureUrl.substring(pureUrl.lastIndexOf('/') + 1);
  return fileName || 'download_file_' + Date.now();
}

/**
 * 下载远程文件为 blob 对象（处理跨域）
 * @param {string} url - 远程文件 URL
 * @returns {Promise<Blob>} blob 对象
 */
async function fetchRemoteFileAsBlob(url) {
  const response = await fetch(url, {
    method: 'GET',
  });
  // 检查响应是否成功
  if (!response.ok) {
    throw new Error(`请求失败：${response.status} ${response.statusText}`);
  }
  return await response.blob();
}
