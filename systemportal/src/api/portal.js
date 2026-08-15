import request from '@/api/request';

/**

 * 使用门户请求实例发送带查询参数的 GET 请求，并直接返回已规范化的业务响应。

 */
const get = (url, params) => request.get(url, { params });
/**
 * 使用门户请求实例发送 JSON POST 请求。
 */
const post = (url, data) => request.post(url, data);
/**
 * 使用门户请求实例发送 JSON PUT 请求。
 */
const put = (url, data) => request.put(url, data);
/**
 * 将删除条件放入 Axios delete 的 data 配置中发送。
 */
const del = (url, data) => request.delete(url, { data });

/**

 * 使用账号密码或验证码登录门户并获取 token 与账号上下文。

 */
export const login = data => post('/portal/auth/login', data);
/**
 * 为注册或找回密码手机号发送一次性短信验证码。
 */
export const sendSmsCode = data => post('/portal/auth/sms-code', data);
/**
 * 提交门户账号注册资料并建立用户。
 */
export const register = data => post('/portal/auth/register', data);
/**
 * 使用验证码和新密码完成门户密码找回。
 */
export const forgetPassword = data => post('/portal/auth/forget-password', data);
/**
 * 查询当前用户、企业列表、当前企业和成员角色上下文。
 */
export const getAccountContext = () => get('/portal/account/context');
/**
 * 申请从门户进入车险系统的一次性 SSO 跳转地址。
 */
export const createInsuranceAuthorization = () => post('/portal/sso/authorize', {});
/**
 * 使用车险系统返回的一次性 code 恢复门户会话。
 */
export const exchangePortalSsoCode = code => post('/portal/sso/exchange', { code });

/**

 * 查询当前企业及其订阅、钱包等门户概览资料。

 */
export const getEnterpriseCurrent = () => get('/portal/enterprise/current');
/**
 * 创建新企业并将当前账号设为拥有者。
 */
export const createEnterprise = data => post('/portal/enterprise', data);
/**
 * 更新当前企业允许维护的名称、联系及经营资料。
 */
export const updateEnterprise = data => put('/portal/enterprise/current', data);
/**
 * 使用有效邀请码加入目标企业。
 */
export const joinEnterpriseByInvite = data => post('/portal/enterprise/join-by-invite', data);
/**
 * 分页查询当前企业的邀请码及使用状态。
 */
export const getInviteCodes = params => get('/portal/enterprise/invite-codes', params);
/**
 * 批量创建指定角色和有效期的企业邀请码。
 */
export const createInviteCode = data => post('/portal/enterprise/invite-codes', data);
/**
 * 删除尚可撤销的企业邀请码。
 */
export const deleteInviteCode = data => del('/portal/enterprise/invite-codes', data);
/**
 * 分页查询当前企业成员及角色状态。
 */
export const getMembers = params => get('/portal/enterprise/members', params);
/**
 * 调整企业成员角色，所有者角色转移使用独立接口。
 */
export const updateMemberRole = data => put('/portal/enterprise/members/role', data);
/**
 * 启用或停用企业成员的门户使用权。
 */
export const updateMemberStatus = data => put('/portal/enterprise/members/status', data);
/**
 * 将指定非所有者成员移出当前企业。
 */
export const removeEnterpriseMember = data => post('/portal/enterprise/members/remove', data);
/**
 * 将企业所有权转移给符合条件的目标成员。
 */
export const transferOwner = data => post('/portal/enterprise/owner-transfer', data);
/**
 * 分页查询成员角色、状态和进出企业的变更日志。
 */
export const getMemberChangeLogs = params => get('/portal/enterprise/member-change-logs', params);
/**
 * 由当前非所有者成员主动退出企业。
 */
export const exitEnterprise = () => post('/portal/enterprise/members/exit', {});

/**

 * 查询当前企业钱包、订阅和成员占用等财务概览。

 */
export const getFinanceOverview = () => get('/portal/finance/overview');
/**
 * 查询门户可购买的有效套餐及其成员、工单额度。
 */
export const getPlans = () => get('/portal/finance/plans');
/**
 * 官网公开读取套餐；失败时静默使用页面备用套餐，不发布全局错误通知。
 */
export const getMarketingPlans = () => request.get('/portal/finance/plans', {
  skipErrorNotification: true
});
/**
 * 创建指定金额和支付渠道的余额充值订单。
 */
export const createRechargeOrder = data => post('/portal/finance/recharge-orders', data);
/**
 * 查询单个充值订单及其支付状态。
 */
export const getRechargeOrder = id => get(`/portal/finance/recharge-orders/${id}`);
/**
 * 模拟或确认充值支付完成，并将金额记入企业钱包。
 */
export const completeRechargeOrder = data => post('/portal/finance/recharge-orders/complete', data);
/**
 * 取消仍处于待支付状态的充值订单。
 */
export const cancelRechargeOrder = id => post(`/portal/finance/recharge-orders/${id}/cancel`, {});
/**
 * 按时间、渠道和状态分页查询充值订单。
 */
export const getRechargeOrders = params => get('/portal/finance/recharge-orders', params);
/**
 * 试算套餐订阅或变更费用，包括余额、周期折算和超额工单费用。
 */
export const getSubscriptionOrderPreview = data => post('/portal/finance/subscription-orders/preview', data);
/**
 * 创建并支付订阅订单，余额不足时返回待充值上下文。
 */
export const createSubscriptionOrder = data => post('/portal/finance/subscription-orders', data);
/**
 * 开启或关闭当前有效订阅的自动续费。
 */
export const updateAutoRenew = data => put('/portal/finance/subscription/auto-renew', data);
/**
 * 按类型、状态和时间分页查询订阅订单。
 */
export const getSubscriptionOrders = params => get('/portal/finance/subscription-orders', params);
/**
 * 分页查询企业钱包的充值、订阅、退款和超额计费流水。
 */
export const getWalletTransactions = params => get('/portal/finance/wallet-transactions', params);

/**

 * 查询当前门户用户的个人资料。

 */
export const getProfile = () => get('/portal/user/profile');
/**
 * 更新当前用户允许修改的个人资料字段。
 */
export const updateProfile = data => put('/portal/user/profile', data);
