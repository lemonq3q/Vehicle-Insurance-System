import axios from "./config";

const rootUrl = "/ocr";
export function imgRecognition(systemFile, type) {
  let url = `${rootUrl}/${type}`
  return axios.post(url, systemFile);
}
