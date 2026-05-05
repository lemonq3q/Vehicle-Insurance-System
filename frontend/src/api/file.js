import Message from "@/utils/message";
import axios from "./config"

const rootUrl = "/file";
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
 * @param {string} url - 文件地址（远程 URL 或 blob/data URL）
 * @param {string} [name] - 下载后的文件名（可选）
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
 * 核心下载方法：通过 a 标签下载 blob URL
 * @param {string} blobUrl - blob 或 data URL
 * @param {string} fileName - 文件名
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