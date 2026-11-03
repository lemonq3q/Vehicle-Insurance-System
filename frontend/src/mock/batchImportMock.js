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

/** 工单与上下游复用同一导入结果外壳，含处理人多条件匹配失败示例。 */
export const workorderBatchImportMock = {
  code: 200, msg: '导入完成', data: {
    totalRows: 2, successRows: 1, failureRows: 1,
    failures: [{ sheetName: '工单', rowNumber: 6,
      rowData: { 提交店铺: '甲店', 处理人姓名: '赵六' },
      reason: '本企业没有同时满足所填姓名、登录邮箱及证件号的有效处理人' }]
  }
};

/** 1万条按非空业务行计数；超限在任何保存之前返回业务失败。 */
export const batchImportRowLimitMock = { code: 400, msg: '单次最多导入10000条工单' };

/**
 * 101条失败明细用于联调20/50/100条分页、末页不足一页及切换页大小回到首页。
 * 沿用真实导入响应结构，页码与页大小仅由前端维护，不传入导入接口。
 */
export const batchImportPaginatedResultMock = {
  code: 200,
  msg: '导入完成',
  data: {
    totalRows: 101,
    successRows: 0,
    failureRows: 101,
    failures: Array.from({ length: 101 }, (_, index) => ({
      sheetName: '工单',
      rowNumber: index + 5,
      rowData: { 提交店铺: `示例店铺${index + 1}` },
      reason: '本企业不存在对应名称的有效下游'
    }))
  }
};

/**
 * 身份条件全空的行级错误，与处理人至少填写一项的新导入规则保持一致。
 * 可用于确认失败分页和原因提示，不在页面中模拟已成功保存工单。
 */
export const workorderMissingHandlerMock = {
  code: 200,
  msg: '导入完成',
  data: {
    totalRows: 1, successRows: 0, failureRows: 1,
    failures: [{ sheetName: '工单', rowNumber: 5, rowData: { 提交店铺: '示例车商' },
      reason: '处理人姓名、登录邮箱、证件号至少填写一项' }]
  }
};
