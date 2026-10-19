/**
 * 离线微信接口契约示例，只供页面联调；不生成真实二维码，不加入生产请求拦截器。
 * 登录完成样例必须由调用端传入现有登录结果，以保持门户/车险各自 JWT 响应结构。
 */
export const wechatMock = {
  start: { code: 200, msg: '操作成功', data: { appId: 'wx_mock', state: 'mock_state', redirectUri: 'http://localhost/wechat/callback', expiresInSeconds: 300 } },
  bound: { code: 200, msg: '微信绑定成功', data: { wechatBound: true } },
  unavailable: { code: 503, msg: '微信登录尚未配置，请使用邮箱登录' },
  serviceFailure: { code: 502, msg: '微信授权服务暂不可用，请重试（排查编号：0123456789abcdef0123456789abcdef）', data: { traceId: '0123456789abcdef0123456789abcdef' } },
  occupied: { code: 409, msg: '该微信已关联其他账号' },
  notFound: { code: 404, msg: '账号不存在，请先前往企业门户注册或绑定微信' },
  expired: { code: 400, msg: '微信授权已过期或已使用，请重新扫码' },
  cancelled: { code: 400, msg: '已取消微信授权，请重新扫码' }
};
