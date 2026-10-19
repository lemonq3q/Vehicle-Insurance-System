import request from '@/api/request';

/**
 * 微信网站授权入口沿用门户请求实例；登录可匿名，绑定必须使用当前 JWT。
 * challenge 是本浏览器保存的随机 verifier 的 SHA-256，防止他人替换扫码结果。
 */
export const startWechat = (binding, challenge) => request.post(binding ? '/portal/user/profile/wechat/start' : '/portal/auth/wechat/start', { challenge }, { skipErrorNotification: true });
/**
 * 在原浏览器完成一次性 code/state 校验；绑定不接受用户 ID，登录返回既有 JWT 上下文。
 */
export const completeWechat = (binding, data) => request.post(binding ? '/portal/user/profile/wechat/complete' : '/portal/auth/wechat/complete', data, { loading: { scope: 'auth-callback' } });
