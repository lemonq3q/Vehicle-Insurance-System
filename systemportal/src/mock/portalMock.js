const currentUser = {
  id: 10001,
  username: 'linxf',
  phone: '13800000001',
  realName: '林晓峰',
  idNum: '330106199001011234',
  avatarFileId: null,
  status: 1,
  lastLoginTime: '2026-07-07 09:12:30'
};

let currentEnterpriseId = 20001;
const smsCodes = new Map();
const MOCK_NOW = new Date('2026-07-14T12:00:00');

const enterprises = [
  {
    id: 20001,
    name: '杭州小马车险服务有限公司',
    code: 'ENT-HZ-202607',
    ownerUserId: 10001,
    contactName: '林晓峰',
    contactPhone: '13800000001',
    status: 1,
    source: 1,
    createdAt: '2026-07-01 10:20:00'
  }
];

let members = [
  {
    id: 30001,
    enterpriseId: 20001,
    userId: 10001,
    username: 'linxf',
    realName: '林晓峰',
    phone: '13800000001',
    roleCode: 'OWNER',
    status: 1,
    joinedByInviteId: null,
    joinedAt: '2026-07-01 10:20:00'
  },
  {
    id: 30002,
    enterpriseId: 20001,
    userId: 10002,
    username: 'chenyu',
    realName: '陈雨',
    phone: '13800000002',
    roleCode: 'ADMIN',
    status: 1,
    joinedByInviteId: 40001,
    joinedAt: '2026-07-02 14:06:12'
  },
  {
    id: 30003,
    enterpriseId: 20001,
    userId: 10003,
    username: 'wangmin',
    realName: '王敏',
    phone: '13800000003',
    roleCode: 'ISSUER',
    status: 1,
    joinedByInviteId: 40001,
    joinedAt: '2026-07-03 08:48:30'
  },
  {
    id: 30004,
    enterpriseId: 20001,
    userId: 10004,
    username: 'zhouxuan',
    realName: '周旋',
    phone: '13800000004',
    roleCode: 'ISSUER',
    status: 1,
    joinedByInviteId: 40002,
    joinedAt: '2026-07-05 17:21:05'
  }
];

let inviteCodes = [
  {
    id: 40001,
    enterpriseId: 20001,
    code: 'XMEB-7K29Q',
    defaultRoleCode: 'ISSUER',
    maxUseCount: 5,
    usedCount: 2,
    expiresAt: '2026-08-01 23:59:59',
    status: 1,
    createdBy: 10001,
    createdAt: '2026-07-01 11:00:00'
  },
  {
    id: 40002,
    enterpriseId: 20001,
    code: 'XMEB-VIP88',
    defaultRoleCode: 'ISSUER',
    maxUseCount: 10,
    usedCount: 1,
    expiresAt: '2026-07-20 23:59:59',
    status: 1,
    createdBy: 10002,
    createdAt: '2026-07-03 09:30:00'
  }
];

const plans = [
  {
    id: 50001,
    code: 'STARTER_MONTH',
    name: '轻量版',
    description: '适合小团队起步，覆盖基础成员协作和车险工单处理。',
    billingPeriod: 'MONTH',
    durationDays: 30,
    userLimit: 5,
    price: 299,
    originalPrice: 399,
    status: 1,
    sortNo: 1
  },
  {
    id: 50002,
    code: 'PRO_YEAR',
    name: '专业版',
    description: '适合稳定经营团队，支持更多成员、续保跟进和财务对账。',
    billingPeriod: 'YEAR',
    durationDays: 365,
    userLimit: 30,
    price: 2999,
    originalPrice: 3999,
    status: 1,
    sortNo: 2
  },
  {
    id: 50003,
    code: 'ENTERPRISE_YEAR',
    name: '企业版',
    description: '适合多网点企业，提供更高成员上限和专属服务支持。',
    billingPeriod: 'YEAR',
    durationDays: 365,
    userLimit: 100,
    price: 8999,
    originalPrice: 10999,
    status: 1,
    sortNo: 3
  }
];

let wallet = {
  id: 60001,
  enterpriseId: 20001,
  balanceAmount: 12680.5,
  frozenAmount: 0,
  currency: 'CNY',
  status: 1,
  updatedAt: '2026-07-07 09:40:00'
};

let subscription = {
  id: 70001,
  enterpriseId: 20001,
  planId: 50002,
  orderId: 80001,
  status: 1,
  userLimit: 30,
  startAt: '2026-07-01 00:00:00',
  endAt: '2027-06-30 23:59:59',
  autoRenewEnabled: true,
  autoRenewPlanId: 50002,
  nextRenewAt: '2027-06-25 09:00:00',
  plan: plans[1]
};

let rechargeOrders = [
  {
    id: 90001,
    rechargeNo: 'RC202607010001',
    enterpriseId: 20001,
    userId: 10001,
    amount: 10000,
    payChannel: 'BANK',
    payTradeNo: 'BANK202607010998',
    status: 2,
    paidAt: '2026-07-01 12:10:00',
    createdAt: '2026-07-01 12:01:00'
  },
  {
    id: 90002,
    rechargeNo: 'RC202607050003',
    enterpriseId: 20001,
    userId: 10002,
    amount: 5000,
    payChannel: 'ALIPAY',
    payTradeNo: 'ALI202607050221',
    status: 2,
    paidAt: '2026-07-05 16:28:00',
    createdAt: '2026-07-05 16:21:00'
  }
];

let subscriptionOrders = [
  {
    id: 80001,
    orderNo: 'SO202607010001',
    orderType: 'BUY',
    enterpriseId: 20001,
    buyerUserId: 10001,
    planId: 50002,
    planName: '专业版',
    buyUserLimit: 30,
    buyDurationDays: 365,
    payableAmount: 2999,
    paidAmount: 2999,
    payType: 'BALANCE',
    autoRenew: true,
    status: 2,
    paidAt: '2026-07-01 12:20:00',
    createdAt: '2026-07-01 12:18:00'
  },
  {
    id: 80002,
    orderNo: 'SO202607060002',
    orderType: 'CHANGE_PLAN',
    enterpriseId: 20001,
    buyerUserId: 10001,
    planId: 50003,
    planName: '企业版',
    buyUserLimit: 100,
    buyDurationDays: 365,
    payableAmount: 5200,
    paidAmount: 0,
    payType: 'BALANCE',
    autoRenew: false,
    status: 1,
    paidAt: null,
    createdAt: '2026-07-06 10:18:00'
  }
];

let transactions = [
  {
    id: 100001,
    transactionNo: 'WT202607010001',
    enterpriseId: 20001,
    walletId: 60001,
    userId: 10001,
    direction: 'IN',
    transactionType: 'RECHARGE',
    amount: 10000,
    balanceBefore: 0,
    balanceAfter: 10000,
    relatedRechargeOrderId: 90001,
    remark: '企业余额充值',
    createdAt: '2026-07-01 12:10:00'
  },
  {
    id: 100002,
    transactionNo: 'WT202607010002',
    enterpriseId: 20001,
    walletId: 60001,
    userId: 10001,
    direction: 'OUT',
    transactionType: 'BUY_PLAN',
    amount: 2999,
    balanceBefore: 10000,
    balanceAfter: 7001,
    relatedOrderId: 80001,
    remark: '购买专业版套餐',
    createdAt: '2026-07-01 12:20:00'
  },
  {
    id: 100003,
    transactionNo: 'WT202607050001',
    enterpriseId: 20001,
    walletId: 60001,
    userId: 10002,
    direction: 'IN',
    transactionType: 'RECHARGE',
    amount: 5000,
    balanceBefore: 7680.5,
    balanceAfter: 12680.5,
    relatedRechargeOrderId: 90002,
    remark: '企业余额充值',
    createdAt: '2026-07-05 16:28:00'
  }
];

const roleNames = {
  OWNER: '企业拥有者',
  ADMIN: '管理员',
  ISSUER: '出单员'
};

const statusNames = {
  order: {
    1: '待支付',
    2: '已支付',
    3: '已取消',
    4: '已退款',
    5: '已关闭'
  },
  recharge: {
    1: '待支付',
    2: '已支付',
    3: '已取消',
    4: '支付失败'
  }
};

function clone(data) {
  return JSON.parse(JSON.stringify(data));
}

function ok(data, msg = '操作成功') {
  return Promise.resolve({ code: 200, msg, data: clone(data) });
}

function fail(msg, code = 400) {
  return Promise.resolve({ code, msg, data: null });
}

function paginate(source, query = {}) {
  const pageNum = Number(query.pageNum || 1);
  const pageSize = Number(query.pageSize || 10);
  const start = (pageNum - 1) * pageSize;
  return {
    total: source.length,
    table: source.slice(start, start + pageSize)
  };
}

function getCurrentEnterprise() {
  return enterprises.find(item => item.id === currentEnterpriseId) || null;
}

function getCurrentMember() {
  return members.find(item => item.enterpriseId === currentEnterpriseId && item.userId === currentUser.id) || null;
}

function context() {
  return {
    user: currentUser,
    enterprises,
    currentEnterpriseId,
    currentEnterprise: getCurrentEnterprise(),
    currentMember: getCurrentMember()
  };
}

function createOrderNo(prefix) {
  return `${prefix}${new Date().getTime()}`;
}

function parseDateTime(value) {
  return new Date(String(value).replace(' ', 'T'));
}

function formatDateTime(value) {
  const date = new Date(value);
  const pad = number => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function addDays(value, days) {
  const date = new Date(value);
  date.setDate(date.getDate() + Number(days));
  return date;
}

function roundMoney(value) {
  return Number(Number(value || 0).toFixed(2));
}

function calculateSubscriptionOrder(planId, periodCount) {
  const plan = plans.find(item => item.id === Number(planId));
  if (!plan) return null;

  const count = Number(periodCount);
  const validPeriodCount = Number.isInteger(count) && count > 0;
  const activeSubscription = subscription && subscription.status === 1 && parseDateTime(subscription.endAt) > MOCK_NOW
    ? subscription
    : null;
  const currentPlan = activeSubscription?.plan || null;
  const remainingDays = activeSubscription
    ? Math.max(0, (parseDateTime(activeSubscription.endAt) - MOCK_NOW) / 86400000)
    : 0;
  const orderType = !activeSubscription ? 'BUY' : activeSubscription.planId === plan.id ? 'RENEW' : 'CHANGE_PLAN';
  const minimumPeriodCount = orderType === 'CHANGE_PLAN'
    ? Math.max(1, Math.ceil(remainingDays / plan.durationDays))
    : 1;
  const priceAmount = validPeriodCount ? roundMoney(plan.price * count) : 0;
  const remainingPeriodCount = currentPlan ? remainingDays / currentPlan.durationDays : 0;
  const creditAmount = orderType === 'CHANGE_PLAN'
    ? roundMoney(remainingPeriodCount * currentPlan.price)
    : 0;
  const differenceAmount = roundMoney(priceAmount - creditAmount);
  const payableAmount = Math.max(0, differenceAmount);
  const refundAmount = Math.max(0, roundMoney(-differenceAmount));
  const memberCount = members.filter(item => item.enterpriseId === currentEnterpriseId && item.status === 1).length;
  const periodEligible = validPeriodCount && count >= minimumPeriodCount;
  const memberEligible = memberCount <= plan.userLimit;
  const effectiveStart = orderType === 'RENEW' ? parseDateTime(activeSubscription.endAt) : MOCK_NOW;
  const effectiveEnd = addDays(effectiveStart, plan.durationDays * (validPeriodCount ? count : minimumPeriodCount));
  let validationMessage = '';
  if (!validPeriodCount) validationMessage = '订阅周期必须是大于 0 的整数';
  else if (!periodEligible) validationMessage = `改订周期不能少于 ${minimumPeriodCount} 个周期`;
  else if (!memberEligible) validationMessage = `当前企业有 ${memberCount} 名成员，超过该套餐 ${plan.userLimit} 人的成员上限`;

  return {
    plan,
    currentPlan,
    orderType,
    periodCount: count,
    minimumPeriodCount,
    remainingDays: Number(remainingDays.toFixed(2)),
    remainingPeriodCount: Number(remainingPeriodCount.toFixed(2)),
    priceAmount,
    creditAmount,
    payableAmount,
    refundAmount,
    balanceAmount: wallet.balanceAmount,
    shortfallAmount: Math.max(0, roundMoney(payableAmount - wallet.balanceAmount)),
    startAt: formatDateTime(effectiveStart),
    endAt: formatDateTime(effectiveEnd),
    memberCount,
    eligible: periodEligible && memberEligible,
    validationMessage
  };
}

export function getRoleName(roleCode) {
  return roleNames[roleCode] || roleCode || '-';
}

export function getStatusName(type, status) {
  return statusNames[type]?.[status] || '-';
}

export function mockRequest({ url, method = 'GET', data = {}, params = {} }) {
  if (url === '/portal/auth/login' && method === 'POST') {
    return ok({ token: 'mock-portal-token', ...context() }, '登录成功');
  }
  if (url === '/portal/auth/sms-code' && method === 'POST') {
    if (!/^1[3-9]\d{9}$/.test(data.phone || '')) {
      return fail('请输入正确的 11 位手机号');
    }
    if (!['REGISTER', 'RESET_PASSWORD'].includes(data.scene)) {
      return fail('短信验证码场景不正确');
    }
    smsCodes.set(`${data.scene}:${data.phone}`, '123456');
    return ok({ expiresInSeconds: 300, retryAfterSeconds: 60 }, '验证码已发送，mock 验证码为 123456');
  }
  if (url === '/portal/auth/register' && method === 'POST') {
    if (smsCodes.get(`REGISTER:${data.phone}`) !== data.smsCode) {
      return fail('短信验证码错误或已失效');
    }
    smsCodes.delete(`REGISTER:${data.phone}`);
    return ok({
      ...currentUser,
      id: 10099,
      username: data.phone,
      phone: data.phone,
      realName: data.realName
    }, '注册成功');
  }
  if (url === '/portal/auth/forget-password' && method === 'POST') {
    if (smsCodes.get(`RESET_PASSWORD:${data.phone}`) !== data.smsCode) {
      return fail('短信验证码错误或已失效');
    }
    smsCodes.delete(`RESET_PASSWORD:${data.phone}`);
    return ok(true, '密码已重置');
  }
  if (url === '/portal/account/context') {
    return ok(context());
  }
  if (url === '/portal/enterprise/current') {
    return ok({
      enterprise: getCurrentEnterprise(),
      member: getCurrentMember(),
      wallet,
      subscription
    });
  }
  if (url === '/portal/enterprise' && method === 'POST') {
    const enterprise = {
      id: 20099,
      ownerUserId: currentUser.id,
      status: 1,
      source: 1,
      createdAt: '2026-07-07 19:00:00',
      ...data,
      code: data.code || 'ENT-NEW-202607'
    };
    enterprises.push(enterprise);
    currentEnterpriseId = enterprise.id;
    members.push({
      id: 30999,
      enterpriseId: enterprise.id,
      userId: currentUser.id,
      username: currentUser.username,
      realName: currentUser.realName,
      phone: currentUser.phone,
      roleCode: 'OWNER',
      status: 1,
      joinedByInviteId: null,
      joinedAt: '2026-07-07 19:00:00'
    });
    return ok(enterprise, '企业创建成功');
  }
  if (url === '/portal/enterprise/current' && method === 'PUT') {
    const enterprise = getCurrentEnterprise();
    Object.assign(enterprise, data);
    return ok(enterprise, '企业信息已更新');
  }
  if (url === '/portal/enterprise/join-by-invite' && method === 'POST') {
    return ok({ enterpriseId: currentEnterpriseId, roleCode: 'ISSUER' }, '已加入企业');
  }
  if (url === '/portal/enterprise/invite-codes' && method === 'GET') {
    return ok(paginate(inviteCodes, params));
  }
  if (url === '/portal/enterprise/invite-codes' && method === 'POST') {
    const invite = {
      id: Date.now(),
      enterpriseId: currentEnterpriseId,
      code: `XMEB-${Math.random().toString(36).slice(2, 7).toUpperCase()}`,
      defaultRoleCode: 'ISSUER',
      usedCount: 0,
      status: 1,
      createdBy: currentUser.id,
      createdAt: '2026-07-07 19:00:00',
      ...data
    };
    inviteCodes.unshift(invite);
    return ok(invite, '邀请码已创建');
  }
  if (url === '/portal/enterprise/invite-codes' && method === 'DELETE') {
    inviteCodes = inviteCodes.filter(item => item.id !== data.id);
    return ok(true, '邀请码已删除');
  }
  if (url === '/portal/enterprise/members' && method === 'GET') {
    let filtered = members.filter(item => item.enterpriseId === currentEnterpriseId);
    if (params.keyword) {
      filtered = filtered.filter(item => `${item.realName}${item.phone}${item.username}`.includes(params.keyword));
    }
    if (params.roleCode) {
      filtered = filtered.filter(item => item.roleCode === params.roleCode);
    }
    return ok(paginate(filtered, params));
  }
  if (url === '/portal/enterprise/members/role' && method === 'PUT') {
    const member = members.find(item => item.id === data.memberId);
    if (member && member.roleCode !== 'OWNER') member.roleCode = data.roleCode;
    return ok(member, '成员角色已更新');
  }
  if (url === '/portal/enterprise/owner-transfer' && method === 'POST') {
    const oldOwner = getCurrentMember();
    const newOwner = members.find(item => item.id === data.toMemberId);
    if (oldOwner && newOwner) {
      oldOwner.roleCode = 'ADMIN';
      newOwner.roleCode = 'OWNER';
      getCurrentEnterprise().ownerUserId = newOwner.userId;
    }
    return ok({
      enterpriseId: currentEnterpriseId,
      fromUserId: oldOwner?.userId,
      toUserId: newOwner?.userId,
      transferredAt: '2026-07-07 19:00:00'
    }, '企业拥有者已转让');
  }
  if (url === '/portal/enterprise/members/exit' && method === 'POST') {
    currentEnterpriseId = null;
    return ok(true, '已退出企业');
  }
  if (url === '/portal/finance/overview') {
    return ok({ wallet, subscription, currentMemberCount: members.length });
  }
  if (url === '/portal/finance/plans') {
    return ok(plans);
  }
  if (url === '/portal/finance/recharge-orders' && method === 'GET') {
    let filtered = rechargeOrders;
    if (params.rechargeNo) filtered = filtered.filter(item => item.rechargeNo.includes(params.rechargeNo));
    if (params.status) filtered = filtered.filter(item => item.status === Number(params.status));
    return ok(paginate(filtered, params));
  }
  if (url === '/portal/finance/recharge-orders' && method === 'POST') {
    const order = {
      id: Date.now(),
      rechargeNo: createOrderNo('RC'),
      enterpriseId: currentEnterpriseId,
      userId: currentUser.id,
      payTradeNo: '',
      status: 1,
      paidAt: null,
      createdAt: '2026-07-07 19:00:00',
      ...data
    };
    rechargeOrders.unshift(order);
    return ok(order, '充值订单已创建');
  }
  if (url === '/portal/finance/subscription-orders' && method === 'GET') {
    let filtered = subscriptionOrders;
    if (params.orderNo) filtered = filtered.filter(item => item.orderNo.includes(params.orderNo));
    if (params.orderType) filtered = filtered.filter(item => item.orderType === params.orderType);
    return ok(paginate(filtered, params));
  }
  if (url === '/portal/finance/subscription-orders/preview' && method === 'POST') {
    const preview = calculateSubscriptionOrder(data.planId, data.periodCount);
    return preview ? ok(preview) : fail('套餐不存在', 404);
  }
  if (url === '/portal/finance/subscription-orders' && method === 'POST') {
    const preview = calculateSubscriptionOrder(data.planId, data.periodCount);
    if (!preview) return fail('套餐不存在', 404);
    if (!preview.eligible) return fail(preview.validationMessage, 422);
    if (preview.payableAmount > wallet.balanceAmount) {
      return Promise.resolve({
        code: 409,
        msg: '企业余额不足，请先充值',
        data: clone(preview)
      });
    }

    const plan = preview.plan;
    const balanceBefore = wallet.balanceAmount;
    const balanceChange = preview.refundAmount > 0 ? preview.refundAmount : -preview.payableAmount;
    wallet.balanceAmount = roundMoney(balanceBefore + balanceChange);
    wallet.updatedAt = formatDateTime(MOCK_NOW);

    const order = {
      id: Date.now(),
      orderNo: createOrderNo('SO'),
      orderType: preview.orderType,
      enterpriseId: currentEnterpriseId,
      buyerUserId: currentUser.id,
      planId: plan.id,
      planName: plan.name,
      planSnapshot: clone(plan),
      buyUserLimit: plan.userLimit,
      buyDurationDays: plan.durationDays * preview.periodCount,
      periodCount: preview.periodCount,
      amount: preview.payableAmount,
      priceAmount: preview.priceAmount,
      discountAmount: 0,
      creditAmount: preview.creditAmount,
      payableAmount: preview.payableAmount,
      refundAmount: preview.refundAmount,
      paidAmount: preview.payableAmount,
      payType: 'BALANCE',
      autoRenew: Boolean(data.autoRenew),
      originalSubscriptionId: preview.orderType === 'CHANGE_PLAN' ? subscription?.id : null,
      oldPlanId: preview.orderType === 'CHANGE_PLAN' ? subscription?.planId : null,
      newPlanId: preview.orderType === 'CHANGE_PLAN' ? plan.id : null,
      status: 2,
      paidAt: formatDateTime(MOCK_NOW),
      createdAt: formatDateTime(MOCK_NOW)
    };

    if (preview.payableAmount > 0 || preview.refundAmount > 0) {
      const isRefund = preview.refundAmount > 0;
      const transaction = {
        id: Date.now() + 1,
        transactionNo: createOrderNo('WT'),
        enterpriseId: currentEnterpriseId,
        walletId: wallet.id,
        userId: currentUser.id,
        direction: isRefund ? 'IN' : 'OUT',
        transactionType: preview.orderType === 'BUY' ? 'BUY_PLAN' : preview.orderType === 'RENEW' ? 'RENEW_PLAN' : 'CHANGE_PLAN',
        amount: isRefund ? preview.refundAmount : preview.payableAmount,
        balanceBefore,
        balanceAfter: wallet.balanceAmount,
        relatedOrderId: order.id,
        relatedSubscriptionId: subscription?.id || null,
        remark: isRefund ? `改订${plan.name}退回剩余价值` : `${preview.orderType === 'CHANGE_PLAN' ? '改订' : '订阅'}${plan.name}`,
        createdAt: formatDateTime(MOCK_NOW)
      };
      transactions.unshift(transaction);
      order.walletTransactionId = transaction.id;
    }

    subscriptionOrders.unshift(order);
    subscription = {
      ...subscription,
      id: subscription?.id || Date.now() + 2,
      enterpriseId: currentEnterpriseId,
      planId: plan.id,
      orderId: order.id,
      status: 1,
      userLimit: plan.userLimit,
      startAt: preview.orderType === 'RENEW' ? subscription.startAt : preview.startAt,
      endAt: preview.endAt,
      autoRenewEnabled: Boolean(data.autoRenew),
      autoRenewPlanId: plan.id,
      nextRenewAt: data.autoRenew ? formatDateTime(addDays(parseDateTime(preview.endAt), -5)) : null,
      lastRenewOrderId: preview.orderType === 'RENEW' ? order.id : subscription?.lastRenewOrderId || null,
      plan
    };
    return ok(order, preview.refundAmount > 0 ? '套餐改订成功，差额已退回企业余额' : '订阅订单已支付，套餐已生效');
  }
  if (url === '/portal/finance/subscription/auto-renew' && method === 'PUT') {
    subscription.autoRenewEnabled = Boolean(data.autoRenewEnabled);
    return ok(subscription, '自动续费设置已更新');
  }
  if (url === '/portal/finance/wallet-transactions' && method === 'GET') {
    let filtered = transactions;
    if (params.transactionNo) filtered = filtered.filter(item => item.transactionNo.includes(params.transactionNo));
    if (params.direction) filtered = filtered.filter(item => item.direction === params.direction);
    if (params.transactionType) filtered = filtered.filter(item => item.transactionType === params.transactionType);
    return ok(paginate(filtered, params));
  }
  if (url === '/portal/user/profile' && method === 'GET') {
    return ok(currentUser);
  }
  if (url === '/portal/user/profile' && method === 'PUT') {
    Object.assign(currentUser, data);
    return ok(currentUser, '个人信息已更新');
  }
  return Promise.resolve({ code: 404, msg: 'mock 接口不存在', data: null });
}
