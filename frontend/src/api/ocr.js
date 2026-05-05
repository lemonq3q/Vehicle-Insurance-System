import axios from "./config";

const rootUrl = "/ocr";
export function imgRecognition(file, type) {
  let url = `${rootUrl}/${type}`
  const formData = new FormData();
  formData.append('file', file);
  return axios.post(url, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
}