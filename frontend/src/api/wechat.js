import request from '@/api/config';

/**
 * 车险端只发起已有账号微信登录或当前用户绑定；响应保留本项目 Axios 外壳。
 * challenge 关联本浏览器随机 verifier，注册能力仅由门户提供。
 */
export const startWechat = (binding, challenge) => request.post(binding ? '/auth/profile/wechat/start' : '/auth/wechat/start', { challenge }, { skipErrorNotification: true });
/**
 * 消费一次性的微信授权结果；绑定请求同时携带当前 JWT，防止跨用户写入。
 * 失败由回调页持续显示具体原因，避免再弹出重复通知；401 仍保留统一会话失效处理。
 */
export const completeWechat = (binding, data) => request.post(binding ? '/auth/profile/wechat/complete' : '/auth/wechat/complete', data, { skipErrorNotification: true });
