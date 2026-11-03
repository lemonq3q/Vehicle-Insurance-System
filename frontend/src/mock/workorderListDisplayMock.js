/**
 * 工单列表展示联调样例沿用GET /workorder的统一分页响应及真实DTO字段。
 * 覆盖待报价空费用、成功报价零费用、姓名缺失回退邮箱和既有精确金额；
 * 仅用于开发验证，不接管真实请求，也不把样例数据写入页面或数据库。
 */
export const workorderListDisplayMock = {
  code: 200,
  msg: null,
  data: {
    total: 3,
    table: [
      { id: '101', status: 2, quotationTime: null, handleUserName: '小马科技', handleUserEmail: null,
        handleUserWechatNickname: '小马', handleUserPhone: 'wx_example', handleUserUsername: 'wx_example',
        handleUser: '小马科技\nwx_example', upstreamSumAmount: null, downstreamSumAmount: null },
      { id: '102', status: 4, quotationTime: 1793577600, handleUserName: '秦艺玲', handleUserEmail: null,
        handleUserWechatNickname: null, handleUserPhone: null, handleUserUsername: 'issuer_example',
        handleUser: '秦艺玲\nissuer_example', upstreamSumAmount: null, downstreamSumAmount: null },
      { id: '103', status: 8, quotationTime: 1793577600, handleUserName: ' ', handleUserEmail: 'issuer@example.com',
        handleUserWechatNickname: '微信处理人', handleUserPhone: null, handleUserUsername: 'wx_fallback',
        handleUser: 'wx_fallback', upstreamSumAmount: '128.50', downstreamSumAmount: '0.00' }
    ]
  }
};
