/**
 * 批量导入前端联调样例，与后端 BatchTemplateValidationResult 和 BatchImportResult 保持一致。
 * 当前页面默认调用真实 API；需要离线联调时可由请求适配器直接返回这些对象。
 */
export const templateValidationMock = {
  code: 200,
  data: { valid: true, fileName: '车险系统_上游批量导入模板.xlsx', message: '模板结构校验通过，可以继续导入' }
};

export const batchImportResultMock = {
  code: 200,
  msg: '导入完成',
  data: {
    totalRows: 3,
    successRows: 2,
    failureRows: 1,
    failures: [{
      sheetName: '上游机构',
      rowNumber: 7,
      rowData: { 机构名称: '示例机构', 所在地区: '广州' },
      reason: '当前企业已存在同名机构'
    }]
  }
};
