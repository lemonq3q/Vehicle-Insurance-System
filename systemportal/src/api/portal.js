import request from '@/api/request';

const get = (url, params) => request.get(url, { params });
const post = (url, data) => request.post(url, data);
const put = (url, data) => request.put(url, data);
const del = (url, data) => request.delete(url, { data });

export const login = data => post('/portal/auth/login', data);
export const sendSmsCode = data => post('/portal/auth/sms-code', data);
export const register = data => post('/portal/auth/register', data);
export const forgetPassword = data => post('/portal/auth/forget-password', data);
export const getAccountContext = () => get('/portal/account/context');

export const getEnterpriseCurrent = () => get('/portal/enterprise/current');
export const createEnterprise = data => post('/portal/enterprise', data);
export const updateEnterprise = data => put('/portal/enterprise/current', data);
export const joinEnterpriseByInvite = data => post('/portal/enterprise/join-by-invite', data);
export const getInviteCodes = params => get('/portal/enterprise/invite-codes', params);
export const createInviteCode = data => post('/portal/enterprise/invite-codes', data);
export const deleteInviteCode = data => del('/portal/enterprise/invite-codes', data);
export const getMembers = params => get('/portal/enterprise/members', params);
export const updateMemberRole = data => put('/portal/enterprise/members/role', data);
export const updateMemberStatus = data => put('/portal/enterprise/members/status', data);
export const transferOwner = data => post('/portal/enterprise/owner-transfer', data);
export const getOwnerTransferLogs = params => get('/portal/enterprise/owner-transfer-logs', params);
export const exitEnterprise = () => post('/portal/enterprise/members/exit', {});

export const getFinanceOverview = () => get('/portal/finance/overview');
export const getPlans = () => get('/portal/finance/plans');
export const createRechargeOrder = data => post('/portal/finance/recharge-orders', data);
export const getRechargeOrders = params => get('/portal/finance/recharge-orders', params);
export const getSubscriptionOrderPreview = data => post('/portal/finance/subscription-orders/preview', data);
export const createSubscriptionOrder = data => post('/portal/finance/subscription-orders', data);
export const updateAutoRenew = data => put('/portal/finance/subscription/auto-renew', data);
export const getSubscriptionOrders = params => get('/portal/finance/subscription-orders', params);
export const getWalletTransactions = params => get('/portal/finance/wallet-transactions', params);

export const getProfile = () => get('/portal/user/profile');
export const updateProfile = data => put('/portal/user/profile', data);
