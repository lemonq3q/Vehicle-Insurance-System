const row = {
  id: 1,
  log_id: "00000000-0000-0000-0000-000000000001",
  identity_type: "TENANT_USER",
  source_system: "InsuranceSystem",
  started_at: "2026-11-20 12:00:00",
  duration_ms: 125,
  method: "GET",
  uri: "/example",
  http_status: 500,
  business_code: "500",
  has_exception: 1,
  enterprise_id: 1,
  user_id: 2,
};
/** 与真实接口相同的秒级时间、分类、级联及耗时筛选，供无后端联调。 */
export const requestLogMock = {
  async enterprises(keyword) {
    return keyword && "示例企业".includes(keyword)
      ? [{ id: 1, name: "示例企业" }]
      : [];
  },
  async users(enterpriseId, keyword) {
    return Number(enterpriseId) === 1 &&
      keyword &&
      "示例用户 user@example.com".includes(keyword)
      ? [{ id: 2, realName: "示例用户", email: "user@example.com" }]
      : [];
  },
  async page(query) {
    const present = (v) => v !== "" && v !== undefined && v !== null;
    const matches =
      (!present(query.hasException) || Number(query.hasException) === 1) &&
      (!query.uri || row.uri.includes(query.uri)) &&
      (!query.sourceSystem || query.sourceSystem === row.source_system) &&
      (!query.businessCodeClass || Number(query.businessCodeClass) === 500) &&
      (!query.httpStatusClass || Number(query.httpStatusClass) === 500) &&
      (!query.httpStatus || Number(query.httpStatus) === row.http_status) &&
      (!query.enterpriseId || Number(query.enterpriseId) === 1) &&
      (!query.userId || Number(query.userId) === 2) &&
      (!query.logId || query.logId === row.log_id) &&
      (!query.startTime || query.startTime <= row.started_at) &&
      (!query.endTime || query.endTime >= row.started_at) &&
      (!present(query.minDurationMs) || 125 >= Number(query.minDurationMs)) &&
      (!present(query.maxDurationMs) || 125 <= Number(query.maxDurationMs));
    return {
      list: matches && query.pageNo === 1 ? [row] : [],
      total: matches ? 1 : 0,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    };
  },
  async detail() {
    return {
      payload: JSON.stringify({
        logId: row.log_id,
        requestBody: null,
        responseBody: '{"code":500,"msg":"服务器异常，请稍后再试"}',
        businessInfo: "业务自由文本",
        exceptionInfo: "示例异常堆栈",
        hasException: true,
      }),
    };
  },
};
