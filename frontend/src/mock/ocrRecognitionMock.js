/**
 * 合格证 OCR 成功响应联调样例，沿用识别接口的统一响应外壳和 recognitionData 字段。
 * fileInfo 未提供时编辑器使用原上传记录；可在请求适配层切换，不写入页面业务逻辑。
 */
export const certificateRecognitionMock = {
  code: 200,
  data: {
    recognitionData: { brandModel: '识别车型', vehicleCode: 'TEST0000000000001', curbWeight: 8050 }
  }
};

/**
 * OCR 响应前的表单初值：车型已有内容，应出现待应用提示；空车架号应自动填入。
 * 整备质量为0时保留原值并展示候选，用于检查数值零不被误判为空。
 */
export const certificateFormBeforeRecognitionMock = {
  vehicleCertificate: { brandModel: '原车型', vehicleCode: '', curbWeight: 0 }
};

/**
 * 身份证长识别文本用于检查候选省略、悬停全文和两列布局稳定性。
 * 识别服务原始字段仍为name/idNum，编辑器通过既有映射写入ownerName/ownerIdNum。
 */
export const idCardLongRecognitionMock = {
  code: 200,
  data: { recognitionData: { name: '用于复核长文本省略展示的识别姓名', idNum: '370702200406192614' } }
};
