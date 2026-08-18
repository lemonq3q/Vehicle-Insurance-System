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

const recentReminders = [
  {
    id: 110003,
    reminderType: 'WALLET_BALANCE_NEAR_SUSPENSION',
    reminderStage: 'NEAR_SUSPENSION',
    severity: 'CRITICAL',
    title: '账户余额接近套餐停止阈值',
    content: '当前余额为 ¥-82.00，已达到欠费预警阈值 ¥-80.00。余额低于 ¥-100.00 时套餐服务将暂停，请尽快充值。',
    occurredAt: '2026-07-14 10:30:00',
    revision: 2
  },
  {
    id: 110002,
    reminderType: 'SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW',
    reminderStage: '1D',
    severity: 'WARNING',
    title: '套餐将在1天后到期',
    content: '您的“专业版”套餐将于 2026-07-15 23:59 到期，当前未开启自动续费，请及时安排续费。',
    occurredAt: '2026-07-14 04:00:00',
    revision: 2
  },
  {
    id: 110001,
    reminderType: 'WORKORDER_QUOTA_NEAR_LIMIT',
    reminderStage: '90_PERCENT',
    severity: 'WARNING',
    title: '工单额度即将用尽',
    content: '本周期工单额度为 5000 单，已使用 4520 单（90.40%），剩余 480 单。',
    occurredAt: '2026-07-13 04:00:00',
    revision: 2
  }
];

const filterByTimeRange = (rows, params, field = 'createdAt') => {
  const start = params.startTime ? new Date(params.startTime.replace(' ', 'T')).getTime() : null;
  const end = params.endTime ? new Date(params.endTime.replace(' ', 'T')).getTime() : null;
  return rows.filter(item => {
    const value = new Date(String(item[field]).replace(' ', 'T')).getTime();
    return (!start || value >= start) && (!end || value <= end);
  });
};

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
    status: 0,
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

const memberChangeLogs = [
  {
    id: 1,
    enterpriseId: 20001,
    eventType: 'JOIN',
    operatorUserId: 10002,
    targetUserId: 10002,
    operatorNameSnapshot: '陈雨',
    targetNameSnapshot: '陈雨',
    beforeRoleCode: null,
    afterRoleCode: 'ISSUER',
    occurredAt: '2026-07-02 14:06:12',
    remark: '通过邀请码加入企业'
  }
];

/**
 * 模拟套餐席位变化后的成员启停策略。缩容时优先停用出单员、再停管理员且优先处理后加入者，始终保护拥有者；
 * 扩容时按拥有者、管理员、出单员顺序恢复，并在同角色内优先恢复早加入成员，使 mock 行为贴近后端席位协调规则。
 */
function synchronizeMemberSeats(userLimit) {
  const enterpriseMembers = members.filter(item => item.enterpriseId === currentEnterpriseId);
  const active = enterpriseMembers.filter(item => item.status === 1);
  if (active.length > userLimit) {
    const disablePriority = { ISSUER: 0, ADMIN: 1, OWNER: 2 };
    active
      .sort((left, right) => disablePriority[left.roleCode] - disablePriority[right.roleCode] || String(right.joinedAt).localeCompare(String(left.joinedAt)))
      .slice(0, active.length - userLimit)
      .forEach(item => {
        item.status = 0;
      });
    return;
  }
  const enablePriority = { OWNER: 0, ADMIN: 1, ISSUER: 2 };
  enterpriseMembers
    .filter(item => item.status === 0)
    .sort((left, right) => enablePriority[left.roleCode] - enablePriority[right.roleCode] || String(left.joinedAt).localeCompare(String(right.joinedAt)))
    .slice(0, userLimit - active.length)
    .forEach(item => {
      item.status = 1;
    });
}

const plans = [
  {
    id: 50001,
    code: 'STARTER_MONTH',
    name: '轻量版',
    description: '适合小团队起步，覆盖基础成员协作和车险工单处理。',
    billingPeriod: 'MONTH',
    durationDays: 30,
    userLimit: 5,
    workorderLimit: 1000,
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
    workorderLimit: 5000,
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
    workorderLimit: 10000,
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
  suspendReason: null,
  suspendedAt: null,
  resumedAt: null,
  userLimit: 30,
  workorderLimit: 5000,
  startAt: '2026-07-01 00:00:00',
  endAt: '2027-06-30 23:59:59',
  autoRenewEnabled: true,
  autoRenewPlanId: 50002,
  nextRenewAt: '2027-06-25 09:00:00',
  plan: plans[1]
};

/**
 * 模拟统一余额服务对有效套餐执行的欠费状态联动。
 * Mock 使用与后端默认配置相同的停止阈值 -100 元和恢复阈值 0 元，并采用严格小于/大于判断；
 * 未订阅或已到期套餐以及非欠费暂停都不会被余额变化改写。
 */
function reconcileMockSubscriptionAccess() {
  if (!subscription?.planId || parseDateTime(subscription.endAt) <= MOCK_NOW) return;
  if (subscription.status === 1 && Number(wallet.balanceAmount) < -100) {
    subscription.status = 3;
    subscription.suspendReason = 'ARREARS';
    subscription.suspendedAt = formatDateTime(MOCK_NOW);
    subscription.resumedAt = null;
  } else if (subscription.status === 3
      && subscription.suspendReason === 'ARREARS'
      && Number(wallet.balanceAmount) > 0) {
    subscription.status = 1;
    subscription.suspendReason = null;
    subscription.resumedAt = formatDateTime(MOCK_NOW);
  }
}

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
  },
  {
    id: 80003,
    orderNo: 'SO20260714A2B3C',
    orderType: 'AUTO_RENEW',
    enterpriseId: 20001,
    buyerUserId: 10001,
    planId: 50002,
    planName: '专业版',
    buyUserLimit: 30,
    buyDurationDays: 365,
    priceAmount: 2999,
    payableAmount: 2999,
    paidAmount: 0,
    payType: 'BALANCE',
    autoRenew: true,
    status: 6,
    failureReason: '企业余额不足',
    paidAt: null,
    createdAt: '2026-07-14 04:00:00'
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
      5: '已关闭',
      6: '自动续费失败'
  },
  recharge: {
    1: '待支付',
    2: '已支付',
    3: '已取消',
    4: '支付失败'
  }
};

/**
 * 对 mock 内存数据做 JSON 深拷贝，避免页面修改响应对象时直接污染模拟数据库。
 */
function clone(data) {
  return JSON.parse(JSON.stringify(data));
}

/**
 * 构造与真实后端统一响应外壳一致的成功 Promise，并隔离返回数据引用。
 */
function ok(data, msg = '操作成功') {
  return Promise.resolve({ code: 200, msg, data: clone(data) });
}

/**
 * 构造业务失败响应；mock 仍以 HTTP 成功返回，由请求拦截器依据业务 code 转换为异常。
 */
function fail(msg, code = 400) {
  return Promise.resolve({ code, msg, data: null });
}

/**
 * 按统一 pageNum/pageSize 契约截取内存列表，并返回与后端 TableData 对齐的 total 和 table 字段。
 */
function paginate(source, query = {}) {
  const pageNum = Number(query.pageNum || 1);
  const pageSize = Number(query.pageSize || 10);
  const start = (pageNum - 1) * pageSize;
  return {
    total: source.length,
    table: source.slice(start, start + pageSize)
  };
}

/**
 * 根据当前企业上下文 ID 从模拟企业库中读取企业，不存在时返回空值。
 */
function getCurrentEnterprise() {
  return enterprises.find(item => item.id === currentEnterpriseId) || null;
}

/**
 * 在当前企业内匹配登录用户的成员记录，用于角色权限和成员状态模拟。
 */
function getCurrentMember() {
  return members.find(item => item.enterpriseId === currentEnterpriseId && item.userId === currentUser.id) || null;
}

/**
 * 组合登录响应及上下文接口使用的用户、企业集合、当前企业和当前成员快照。
 */
function context() {
  return {
    user: currentUser,
    enterprises,
    currentEnterpriseId,
    currentEnterprise: getCurrentEnterprise(),
    currentMember: getCurrentMember()
  };
}

/**
 * 使用业务前缀和当前毫秒时间生成测试订单号，便于区分充值、套餐和流水记录。
 */
function createOrderNo(prefix) {
  return `${prefix}${new Date().getTime()}`;
}

/**
 * 将后端常用的空格分隔日期时间转换为浏览器可解析的本地 Date。
 */
function parseDateTime(value) {
  return new Date(String(value).replace(' ', 'T'));
}

/**
 * 将 Date 统一格式化为接口使用的 yyyy-MM-dd HH:mm:ss 本地时间字符串。
 */
function formatDateTime(value) {
  const date = new Date(value);
  const pad = number => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/**
 * 在给定时间副本上增加自然日，用于推导套餐预计到期时间而不修改调用方对象。
 */
function addDays(value, days) {
  const date = new Date(value);
  date.setDate(date.getDate() + Number(days));
  return date;
}

/**
 * 将 mock 金额按人民币两位小数收敛，防止 JavaScript 浮点误差进入余额和订单断言。
 */
function roundMoney(value) {
  return Number(Number(value || 0).toFixed(2));
}

/**
 * 模拟后端套餐订单试算：识别购买、续订或改订类型，计算剩余套餐抵扣、最低改订周期、应付或退款金额，
 * 同时校验周期和成员席位。页面只消费这里返回的统一试算结构，以便切换真实接口时不改变展示逻辑。
 */
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
  const workorderOverageCount = 0;
  const workorderOverageAmount = 0;
  const differenceAmount = roundMoney(priceAmount - creditAmount + workorderOverageAmount);
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
    workorderOverageCount,
    workorderOverageAmount,
    workorderOverageUnitPrice: 0.2,
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

/**
 * 将企业角色代码转换为 mock 页面使用的中文名称。
 */
export function getRoleName(roleCode) {
  return roleNames[roleCode] || roleCode || '-';
}

/**
 * 按订单类别选择状态字典并返回状态文案，未知值显示占位符。
 */
export function getStatusName(type, status) {
  return statusNames[type]?.[status] || '-';
}

/**
 * 作为门户 mock 后端的统一路由分发器，依据 URL 与 HTTP 方法执行认证、企业成员、邀请、套餐、充值和流水逻辑。
 * 所有分支直接操作本文件的内存数据集并返回与真实接口相同的响应外壳，确保前端联调契约具有可替换性。
 */
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
  if (url === '/portal/sso/authorize' && method === 'POST') {
    const member = getCurrentMember();
    const enterprise = getCurrentEnterprise();
    if (Number(currentUser.status) !== 1) return fail('当前用户账号未启用', 403);
    if (!member || Number(member.status) !== 1) return fail('当前企业成员状态不可用', 403);
    if (!enterprise || Number(enterprise.status) !== 1) return fail('当前企业不可用', 403);
    if (!subscription?.planId || Number(subscription.status) !== 1 || parseDateTime(subscription.endAt) <= MOCK_NOW) {
      const message = subscription?.status === 3 && subscription?.suspendReason === 'ARREARS'
        ? '企业套餐因余额欠费已暂停，请先充值后再进入车险系统'
        : '企业当前没有正常生效的套餐，暂时无法进入车险系统';
      return fail(message, 403);
    }
    return ok({
      redirectUrl: `${process.env.VUE_APP_INSURANCE_FRONTEND_URL || 'http://localhost:8888'}/sso/callback?code=mock-insurance-sso-code`,
      expiresIn: 60
    }, '授权成功');
  }
  if (url === '/portal/reminders/recent' && method === 'GET') {
    const severityOrder = { CRITICAL: 3, WARNING: 2, NOTICE: 1 };
    const monthAgo = new Date(MOCK_NOW);
    monthAgo.setMonth(monthAgo.getMonth() - 1);
    return ok(recentReminders
      .filter(item => parseDateTime(item.occurredAt) >= monthAgo)
      .sort((left, right) => severityOrder[right.severity] - severityOrder[left.severity]
        || String(right.occurredAt).localeCompare(String(left.occurredAt))));
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
    const ownerMember = {
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
    };
    members.push(ownerMember);
    memberChangeLogs.unshift({
      id: Date.now(),
      enterpriseId: enterprise.id,
      eventType: 'JOIN',
      operatorUserId: currentUser.id,
      targetUserId: currentUser.id,
      operatorNameSnapshot: currentUser.realName,
      targetNameSnapshot: currentUser.realName,
      beforeRoleCode: null,
      afterRoleCode: 'OWNER',
      occurredAt: ownerMember.joinedAt,
      remark: '创建企业并加入'
    });
    return ok(enterprise, '企业创建成功');
  }
  if (url === '/portal/enterprise/current' && method === 'PUT') {
    const enterprise = getCurrentEnterprise();
    Object.assign(enterprise, data);
    return ok(enterprise, '企业信息已更新');
  }
  if (url === '/portal/enterprise/join-by-invite' && method === 'POST') {
    const invite = inviteCodes.find(item => item.code === data.code && item.status === 1);
    if (!invite) return fail('邀请码不存在或已失效', 404);
    currentEnterpriseId = invite.enterpriseId;
    const member = {
      id: Date.now(),
      enterpriseId: invite.enterpriseId,
      userId: currentUser.id,
      username: currentUser.username,
      realName: currentUser.realName,
      phone: currentUser.phone,
      roleCode: 'ISSUER',
      status: 0,
      joinedByInviteId: invite.id,
      joinedAt: formatDateTime(MOCK_NOW)
    };
    members = members.filter(item => !(item.enterpriseId === invite.enterpriseId && item.userId === currentUser.id));
    members.push(member);
    invite.usedCount += 1;
    memberChangeLogs.unshift({
      id: Date.now(),
      enterpriseId: invite.enterpriseId,
      eventType: 'JOIN',
      operatorUserId: currentUser.id,
      targetUserId: currentUser.id,
      operatorNameSnapshot: currentUser.realName,
      targetNameSnapshot: currentUser.realName,
      beforeRoleCode: null,
      afterRoleCode: 'ISSUER',
      occurredAt: member.joinedAt,
      remark: '通过邀请码加入企业'
    });
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
    if (params.status !== undefined && params.status !== '') {
      filtered = filtered.filter(item => item.status === Number(params.status));
    }
    return ok(paginate(filtered, params));
  }
  if (url === '/portal/enterprise/members/role' && method === 'PUT') {
    const member = members.find(item => item.id === data.memberId);
    if (member && member.roleCode !== 'OWNER' && member.roleCode !== data.roleCode) {
      memberChangeLogs.unshift({
        id: Date.now(),
        enterpriseId: currentEnterpriseId,
        eventType: 'ROLE_CHANGE',
        operatorUserId: currentUser.id,
        targetUserId: member.userId,
        operatorNameSnapshot: currentUser.realName,
        targetNameSnapshot: member.realName,
        beforeRoleCode: member.roleCode,
        afterRoleCode: data.roleCode,
        occurredAt: formatDateTime(MOCK_NOW),
        remark: '修改企业成员角色'
      });
      member.roleCode = data.roleCode;
    }
    return ok(member, '成员角色已更新');
  }
  if (url === '/portal/enterprise/members/status' && method === 'PUT') {
    const member = members.find(item => item.id === Number(data.memberId) && item.enterpriseId === currentEnterpriseId);
    const targetStatus = Number(data.status);
    if (!member) return fail('成员不存在', 404);
    if (member.roleCode === 'OWNER') return fail('企业拥有者不能被停用', 400);
    if (![0, 1].includes(targetStatus)) return fail('成员状态参数不正确', 400);

    if (targetStatus === 1 && member.status !== 1) {
      const activeMemberCount = members.filter(item => item.enterpriseId === currentEnterpriseId && item.status === 1).length;
      const userLimit = Number(subscription?.userLimit || 0);
      if (userLimit === 0) return fail('企业当前未开通任何套餐，暂时无法启用成员', 409);
      if (userLimit > 0 && activeMemberCount >= userLimit) {
        return Promise.resolve({
          code: 409,
          msg: `当前套餐最多启用 ${userLimit} 名成员，请先升级套餐或停用其他成员`,
          data: { activeMemberCount, userLimit }
        });
      }
    }

    member.status = targetStatus;
    return ok(member, targetStatus === 1 ? '成员已启用' : '成员已停用');
  }
  if (url === '/portal/enterprise/owner-transfer' && method === 'POST') {
    const oldOwner = getCurrentMember();
    const newOwner = members.find(item => item.id === data.toMemberId);
    if (oldOwner && newOwner) {
      memberChangeLogs.unshift({
        id: Date.now(),
        enterpriseId: currentEnterpriseId,
        eventType: 'OWNER_TRANSFER',
        operatorUserId: oldOwner.userId,
        targetUserId: newOwner.userId,
        operatorNameSnapshot: oldOwner.realName,
        targetNameSnapshot: newOwner.realName,
        beforeRoleCode: newOwner.roleCode,
        afterRoleCode: 'OWNER',
        occurredAt: '2026-07-07 19:00:00',
        remark: '门户主动转让企业拥有者'
      });
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
  if (url === '/portal/enterprise/members/remove' && method === 'POST') {
    const member = members.find(item => item.id === Number(data.memberId) && item.enterpriseId === currentEnterpriseId);
    if (!member) return fail('企业成员不存在', 404);
    if (member.roleCode === 'OWNER') return fail('企业拥有者不能被移出企业', 400);
    if (member.userId === currentUser.id) return fail('不能将自己踢出企业，请使用退出企业功能', 400);
    memberChangeLogs.unshift({
      id: Date.now(),
      enterpriseId: currentEnterpriseId,
      eventType: 'KICK',
      operatorUserId: currentUser.id,
      targetUserId: member.userId,
      operatorNameSnapshot: currentUser.realName,
      targetNameSnapshot: member.realName,
      beforeRoleCode: member.roleCode,
      afterRoleCode: null,
      occurredAt: formatDateTime(MOCK_NOW),
      remark: '移出企业成员'
    });
    members = members.filter(item => item.id !== member.id);
    return ok(true, '成员已移出企业');
  }
  if (url === '/portal/enterprise/member-change-logs' && method === 'GET') {
    let filtered = memberChangeLogs.filter(item => item.enterpriseId === currentEnterpriseId);
    if (params.eventType) filtered = filtered.filter(item => item.eventType === params.eventType);
    return ok(paginate(filtered, params));
  }
  if (url === '/portal/enterprise/members/exit' && method === 'POST') {
    const member = getCurrentMember();
    if (member) {
      memberChangeLogs.unshift({
        id: Date.now(),
        enterpriseId: currentEnterpriseId,
        eventType: 'EXIT',
        operatorUserId: currentUser.id,
        targetUserId: currentUser.id,
        operatorNameSnapshot: currentUser.realName,
        targetNameSnapshot: currentUser.realName,
        beforeRoleCode: member.roleCode,
        afterRoleCode: null,
        occurredAt: formatDateTime(MOCK_NOW),
        remark: '成员主动退出企业'
      });
      members = members.filter(item => item.id !== member.id);
    }
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
    filtered = filterByTimeRange(filtered, params);
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
  if (/^\/portal\/finance\/recharge-orders\/\d+$/.test(url) && method === 'GET') {
    const order = rechargeOrders.find(item => item.id === Number(url.split('/').pop()));
    return order ? ok(order) : fail('充值订单不存在', 404);
  }
  if (url === '/portal/finance/recharge-orders/complete' && method === 'POST') {
    const order = rechargeOrders.find(item => item.id === Number(data.rechargeOrderId));
    if (!order) return fail('充值订单不存在', 404);
    if (order.status !== 2) {
      const balanceBefore = Number(wallet.balanceAmount || 0);
      wallet.balanceAmount = roundMoney(balanceBefore + Number(order.amount || 0));
      reconcileMockSubscriptionAccess();
      order.status = 2;
      order.paidAt = formatDateTime(MOCK_NOW);
      transactions.unshift({
        id: Date.now(),
        enterpriseId: currentEnterpriseId,
        walletId: wallet.id,
        userId: currentUser.id,
        transactionNo: createOrderNo('TX'),
        direction: 'IN',
        transactionType: 'RECHARGE',
        amount: Number(order.amount || 0),
        balanceBefore,
        balanceAfter: wallet.balanceAmount,
        relatedRechargeOrderId: order.id,
        remark: `余额充值 ${order.rechargeNo}`,
        createdAt: formatDateTime(MOCK_NOW)
      });
    }
    return ok({ ...order, balanceAmount: wallet.balanceAmount }, '模拟支付成功，余额已到账');
  }
  if (/^\/portal\/finance\/recharge-orders\/\d+\/cancel$/.test(url) && method === 'POST') {
    const orderId = Number(url.split('/').slice(-2)[0]);
    const order = rechargeOrders.find(item => item.id === orderId);
    if (!order) return fail('充值订单不存在', 404);
    if (order.status === 3) return ok(order, '充值订单已取消');
    if (order.status !== 1) return fail('只有待支付订单可以取消', 409);
    order.status = 3;
    return ok(order, '充值订单已取消');
  }
  if (url === '/portal/finance/subscription-orders' && method === 'GET') {
    let filtered = subscriptionOrders;
    if (params.orderNo) filtered = filtered.filter(item => item.orderNo.includes(params.orderNo));
    if (params.orderType) filtered = filtered.filter(item => item.orderType === params.orderType);
    filtered = filterByTimeRange(filtered, params);
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
      buyWorkorderLimit: plan.workorderLimit,
      buyDurationDays: plan.durationDays * preview.periodCount,
      periodCount: preview.periodCount,
      amount: preview.payableAmount,
      priceAmount: preview.priceAmount,
      discountAmount: 0,
      creditAmount: preview.creditAmount,
      workorderOverageCount: preview.workorderOverageCount,
      workorderOverageAmount: preview.workorderOverageAmount,
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
        remark: preview.workorderOverageAmount > 0
          ? `改订${plan.name}（含超额工单费 ¥${preview.workorderOverageAmount}，${preview.workorderOverageCount} 单）`
          : isRefund ? `改订${plan.name}退回剩余价值` : `${preview.orderType === 'CHANGE_PLAN' ? '改订' : '订阅'}${plan.name}`,
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
      suspendReason: null,
      suspendedAt: null,
      resumedAt: subscription?.resumedAt || null,
      userLimit: plan.userLimit,
      workorderLimit: plan.workorderLimit,
      startAt: preview.orderType === 'RENEW' ? subscription.startAt : preview.startAt,
      endAt: preview.endAt,
      autoRenewEnabled: Boolean(data.autoRenew),
      autoRenewPlanId: plan.id,
      nextRenewAt: data.autoRenew ? formatDateTime(addDays(parseDateTime(preview.endAt), -5)) : null,
      lastRenewOrderId: preview.orderType === 'RENEW' ? order.id : subscription?.lastRenewOrderId || null,
      plan
    };
    synchronizeMemberSeats(plan.userLimit);
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
    filtered = filterByTimeRange(filtered, params);
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
