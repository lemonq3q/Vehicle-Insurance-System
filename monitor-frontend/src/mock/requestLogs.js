const row={id:1,log_id:'00000000-0000-0000-0000-000000000001',source_system:'insurance-backend',started_at:'2026-11-20 12:00:00',duration_ms:125,method:'GET',uri:'/example',http_status:500,business_code:'500',has_exception:1,enterprise_id:1,user_id:2};
/** 与真实只读接口一致的分页、详情示例，支持空列表与主要筛选场景。 */
export const requestLogMock={
  async page(query){const matches=(!query.hasException||query.hasException==='1')&&(!query.uri||query.uri===row.uri)&&(!query.sourceSystem||query.sourceSystem===row.source_system)&&(!query.businessCode||query.businessCode==='500')&&(!query.httpStatus||Number(query.httpStatus)===500)&&(!query.enterpriseId||Number(query.enterpriseId)===1)&&(!query.userId||Number(query.userId)===2)&&(!query.logId||query.logId===row.log_id)&&query.startDate<='2026-11-20'&&query.endDate>='2026-11-20';return {list:matches&&query.pageNo===1?[row]:[],total:matches?1:0,pageNo:query.pageNo,pageSize:query.pageSize};},
  async detail(){return {payload:JSON.stringify({logId:row.log_id,requestBody:null,responseBody:'{"code":500,"msg":"服务器异常，请稍后再试"}',businessInfo:'业务可按需追加任意文本',exceptionInfo:'示例异常堆栈',hasException:true})};}
};
