import { dailyUsage, enterprises, finance, members, plans, platformUsers, promotionTargets, visitorLeads } from './database';

/**
 * 构造与监控后端一致的成功响应外壳，并附加可追踪的模拟请求编号。
 */
const ok = data => ({ code: 200, message: 'success', data, requestId: `mock-${Date.now()}` });
/**
 * 将模拟业务失败转换为带业务码的 Promise 异常，使请求层错误处理与真实接口一致。
 */
const fail = (code, message) => Promise.reject(Object.assign(new Error(message), { code }));
/**
 * 按监控接口的 pageNo/pageSize 契约截取内存列表并返回分页元数据。
 */
const page = (list, pageNo = 1, pageSize = 10) => ({ list: list.slice((pageNo - 1) * pageSize, pageNo * pageSize), pageNo, pageSize, total: list.length });
/**
 * 将 Axios 序列化请求体还原为业务对象，空请求体转换为空对象。
 */
const parseBody = data => typeof data === 'string' ? JSON.parse(data || '{}') : (data || {});
/**
 * 模拟短暂网络延迟并把业务外壳包装成 Axios Adapter 响应。
 */
const delay = value => new Promise(resolve => setTimeout(() => resolve({ data: value, status: 200, headers: {}, config: {} }), 180));
let mockMonitorPassword = 'Monitor@123';

/**
 * 从每日用量数据库中选取指定企业和最近天数，按公共日期轴补齐每家企业缺失日期的零值数据，
 * 从而保证多企业趋势对比的序列长度和横轴严格对齐。
 */
function usageFor(ids, range = '30d') {
  const rangeDays = { '7d': 7, '15d': 15, '30d': 30, '3m': 90, '6m': 180, '1y': 365 };
  const days = rangeDays[range] || 30;
  const selected = dailyUsage.filter(item => ids.includes(item.enterpriseId)).slice(-days * ids.length);
  const dates = [...new Set(selected.map(item => item.statDate))];
  return { range, interval: 'DAY', labels: dates, enterprises: ids.map(id => ({ enterpriseId: id, enterpriseName: enterprises.find(item => item.id === id)?.name, points: dates.map(label => selected.find(item => item.enterpriseId === id && item.statDate === label) || { label, workorderCount: 0, requestCount: 0, ocrCount: 0 }).map(point => ({ ...point, label: point.label || point.statDate })) })) };
}

/** 为监控仪表盘 mock 生成与后端 DAY/WEEK/MONTH 自动粒度一致的连续趋势点。 */
function dashboardTrend(metric, range) {
  const rules = { '7d': [7, 'DAY'], '15d': [15, 'DAY'], '30d': [30, 'DAY'], '3m': [14, 'WEEK'], '6m': [6, 'MONTH'], '1y': [12, 'MONTH'] };
  const [count, interval] = rules[range] || rules['30d'];
  const points = Array.from({ length: count }, (_, index) => {
    const date = new Date('2026-07-25T00:00:00');
    if (interval === 'MONTH') date.setMonth(date.getMonth() - (count - index - 1));
    else date.setDate(date.getDate() - (count - index - 1) * (interval === 'WEEK' ? 7 : 1));
    const label = interval === 'MONTH' ? date.toISOString().slice(0, 7) : date.toISOString().slice(0, 10);
    return { label, value: metric === 'request' ? 22800 + index * 930 + (index % 3) * 1450 : 2860 + index * 145 + (index % 4) * 210 };
  });
  return { range, interval, points };
}

/** 将 mock 当前值与上月值转换为真实仪表盘接口采用的统一环比结构。 */
function dashboardMetric(current, previous) {
  const direction = previous === 0 ? (current === 0 ? 'FLAT' : 'NEW') : current > previous ? 'UP' : current < previous ? 'DOWN' : 'FLAT';
  return { current, previous, comparison: { direction, rate: previous === 0 && current !== 0 ? null : Number((Math.abs(current - previous) * 100 / (previous || 1)).toFixed(1)) } };
}

/**
 * 监控前端的模拟后端路由分发器。它覆盖仪表盘、企业用量与财务、套餐配置和后台账号管理，
 * 并直接修改内存数据库以模拟后续查询可见的创建、调账、订阅及状态变化。
 */
export async function mockAdapter(config) {
  const method = (config.method || 'get').toUpperCase();
  const url = config.url.replace(/^\/api\/monitor/, '');
  const params = config.params || {};
  const body = parseBody(config.data);
  let result;

  if (method === 'POST' && url === '/auth/login') {
    const user = platformUsers.find(item => item.username === body.username && item.status === 1);
    if (!user || body.password !== mockMonitorPassword) return fail(400, '手机号或密码错误');
    result = ok({ token: 'mock-monitor-token', user });
  } else if (method === 'GET' && url === '/auth/me') {
    if (!config.headers?.Authorization) return fail(401, '登录状态已失效');
    result = ok(platformUsers.find(item => item.current) || platformUsers[0]);
  } else if (method === 'POST' && url === '/auth/logout') {
    result = ok(null);
  } else if (method === 'PUT' && url === '/auth/profile') {
    const user = platformUsers.find(item => item.current) || platformUsers[0];
    Object.assign(user, { realName: body.realName, email: body.email });
    result = ok(user);
  } else if (method === 'PUT' && url === '/auth/password') {
    if (body.currentPassword !== mockMonitorPassword) return fail(400, '当前密码不正确');
    if (!body.newPassword || body.newPassword.length < 8) return fail(400, '新密码至少需要 8 位');
    if (body.newPassword !== body.confirmPassword) return fail(400, '两次输入的新密码不一致');
    mockMonitorPassword = body.newPassword;
    result = ok(null);
  } else if (method === 'GET' && url === '/dashboard/summary') {
    result = ok({ enterprise: dashboardMetric(26, 23), workorder: dashboardMetric(2860, 2520), recharge: dashboardMetric(428600, 396000), request: dashboardMetric(826420, 751300), ocr: dashboardMetric(98640, 91220), updatedAt: '2026-07-25T18:18:00' });
  } else if (method === 'GET' && url === '/dashboard/recharge-trend') {
    const points = Array.from({ length: 12 }, (_, index) => { const date = new Date(2025, 7 + index, 1); return { label: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`, value: 246000 + index * 15800 + (index % 3) * 32000 }; });
    result = ok({ interval: 'MONTH', points });
  } else if (method === 'GET' && url === '/dashboard/usage-trend') {
    result = ok(dashboardTrend(params.metric, params.range || '30d'));
  } else if (method === 'GET' && url === '/dashboard/enterprise-ranking') {
    const top = Number(params.top || 5);
    const items = enterprises.map((item, index) => ({ enterpriseId: item.id, enterpriseName: item.name, requestCount: 98620 - index * 11240 })).sort((a, b) => b.requestCount - a.requestCount).slice(0, top);
    result = ok({ range: params.range || '30d', top, items });
  } else if (method === 'GET' && url === '/visitor-leads') {
    let rows = [...visitorLeads];
    if (params.leadNo) rows = rows.filter(item => item.leadNo === String(params.leadNo).trim().toUpperCase());
    result = ok(page(rows, Number(params.pageNo || 1), Number(params.pageSize || 10)));
  } else if (method === 'GET' && url === '/promotion-targets') {
    let rows = [...promotionTargets];
    if (params.keyword) rows = rows.filter(item => `${item.name}${item.phone}${item.email}`.includes(params.keyword));
    if (params.sourceType) rows = rows.filter(item => item.sourceType === params.sourceType);
    if (params.status !== '' && params.status !== undefined) rows = rows.filter(item => item.status === Number(params.status));
    if (params.channel === 'PHONE') rows = rows.filter(item => item.phone);
    if (params.channel === 'EMAIL') rows = rows.filter(item => item.email);
    result = ok(page(rows, Number(params.pageNo || 1), Number(params.pageSize || 10)));
  } else if (method === 'POST' && url === '/promotions/import-preview') {
    const rows = promotionTargets.slice(0, 18).map(({ name, phone, email, remark }, index) => ({ rowNumber: index + 2, name, phone, email, remark }));
    result = ok({ batchNo: null, totalRows: rows.length, successRows: rows.length, failureRows: 0, failures: [], list: rows });
  } else if (method === 'POST' && url === '/promotions/preview') {
    const count = body.selectionMode === 'IDS' ? (body.targetIds || []).length : promotionTargets.filter(item => item.status === 1 && (body.channel === 'PHONE' ? item.phone : item.email)).length;
    result = ok({ matchedCount: count, deliverableCount: count, limit: 6000, sendCount: Math.min(count, 6000), truncated: count > 6000 });
  } else if (method === 'POST' && url === '/promotions/send') {
    const count = (body.targetIds || []).length + (body.importedTargets || []).length;
    result = ok({ batchNo: `MOCK${Date.now()}`, channel: body.channel, requestedCount: count, sentCount: Math.min(count, 6000), successCount: Math.min(count, 6000), failureCount: 0, truncated: count > 6000, mock: true });
  } else if (method === 'GET' && url === '/enterprises') {
    let rows = [...enterprises];
    if (params.keyword) rows = rows.filter(item => `${item.name}${item.code}`.includes(params.keyword));
    if (params.status !== '' && params.status !== undefined) rows = rows.filter(item => item.status === Number(params.status));
    if (params.planId) rows = rows.filter(item => item.planId === Number(params.planId));
    if (params.expireDays) { const limit = new Date('2026-07-25'); limit.setDate(limit.getDate() + Number(params.expireDays)); rows = rows.filter(item => item.subscriptionEndDate && new Date(item.subscriptionEndDate) <= limit); }
    rows = rows.map(item => ({ ...item, todayUsage: dailyUsage.filter(row => row.enterpriseId === item.id).at(-1) }));
    result = ok(page(rows, Number(params.pageNo || 1), Number(params.pageSize || 10)));
  } else if (method === 'GET' && url === '/enterprises/options') {
    const keyword = String(params.keyword || '').trim();
    result = ok(keyword ? enterprises.filter(item => `${item.name}${item.code}`.includes(keyword)).map(({ id, name, code }) => ({ id, name, code })) : []);
  } else if (method === 'GET' && url === '/enterprises/usage-top') {
    const field = params.metric === 'workorder' ? 'workorderCount' : params.metric === 'ocr' ? 'ocrCount' : 'requestCount';
    const top = Number(params.top || 5);
    const usage = usageFor(enterprises.map(item => item.id), params.range || '30d');
    result = ok(usage.enterprises.map(item => ({ id: item.enterpriseId, name: item.enterpriseName, code: enterprises.find(row => row.id === item.enterpriseId).code, metricValue: item.points.reduce((sum, point) => sum + point[field], 0) })).sort((a, b) => b.metricValue - a.metricValue).slice(0, top));
  }
  else if (method === 'GET' && /^\/enterprises\/\d+$/.test(url)) result = ok(enterprises.find(item => item.id === Number(url.split('/').at(-1))) || null);
  else if (method === 'GET' && /^\/enterprises\/\d+\/usage$/.test(url)) result = ok(usageFor([Number(url.split('/')[2])], Number(params.days || 14)).enterprises[0]);
  else if (method === 'GET' && url === '/enterprises/usage-comparison') result = ok(usageFor(String(params.enterpriseIds || '').split(',').map(Number), params.range || '30d'));
  else if (method === 'GET' && /^\/enterprises\/\d+\/members$/.test(url)) {
    const enterpriseId = Number(url.split('/')[2]); let rows = members.filter(item => item.enterpriseId === enterpriseId);
    if (params.keyword) rows = rows.filter(item => `${item.realName}${item.username}${item.phone}`.includes(params.keyword));
    if (params.roleName) rows = rows.filter(item => item.roleName === params.roleName);
    if (params.status !== '' && params.status !== undefined) rows = rows.filter(item => item.status === Number(params.status));
    result = ok(page(rows, Number(params.pageNo || 1), Number(params.pageSize || 10)));
  } else if (method === 'GET' && /^\/enterprises\/\d+\/finance-summary$/.test(url)) result = ok({ balance: enterprises.find(item => item.id === Number(url.split('/')[2]))?.balance || 0, totalRecharge: 86000, totalSubscriptionExpense: 42800, monthTransactionCount: 18 });
  else if (method === 'GET' && /^\/enterprises\/\d+\/(recharge-orders|subscription-orders|wallet-transactions)$/.test(url)) {
    const enterpriseId = Number(url.split('/')[2]); const type = url.split('/')[3]; const source = type === 'recharge-orders' ? finance.recharges : type === 'subscription-orders' ? finance.subscriptions : finance.transactions;
    let rows = source.filter(item => item.enterpriseId === enterpriseId);
    if (params.businessNo) rows = rows.filter(item => String(item.orderNo || item.transactionNo || '').includes(params.businessNo));
    if (params.startDate) rows = rows.filter(item => String(item.createdAt || '').slice(0, 10) >= params.startDate);
    if (params.endDate) rows = rows.filter(item => String(item.createdAt || '').slice(0, 10) <= params.endDate);
    result = ok(page(rows, Number(params.pageNo || 1), Number(params.pageSize || 10)));
  } else if (method === 'POST' && /^\/enterprises\/\d+\/balance-adjustments$/.test(url)) { const item = enterprises.find(row => row.id === Number(url.split('/')[2])); item.balance += Number(body.amount); result = ok({ balance: item.balance }); }
  else if (method === 'PUT' && /^\/enterprises\/\d+\/subscription$/.test(url)) { const item = enterprises.find(row => row.id === Number(url.split('/')[2])); const plan = plans.find(row => row.id === Number(body.planId)); Object.assign(item, { planId: plan.id, planName: plan.name, memberLimit: plan.memberLimit, subscriptionEndDate: body.endDate }); result = ok(item); }
  else if (method === 'DELETE' && /^\/enterprises\/\d+\/subscription$/.test(url)) { const item = enterprises.find(row => row.id === Number(url.split('/')[2])); Object.assign(item, { planId: null, planName: null, subscriptionEndDate: null }); result = ok(null); }
  else if (method === 'GET' && url === '/plans') result = ok([...plans].sort((a,b) => a.sortOrder-b.sortOrder));
  else if (method === 'POST' && url === '/plans') { const item = { ...body, id: Math.max(...plans.map(row => row.id)) + 1, updatedAt: '2026-07-25 18:18' }; plans.push(item); result = ok(item); }
  else if (method === 'GET' && /^\/plans\/\d+$/.test(url)) result = ok(plans.find(item => item.id === Number(url.split('/').at(-1))) || null);
  else if (method === 'PUT' && /^\/plans\/\d+$/.test(url)) { const item = plans.find(row => row.id === Number(url.split('/').at(-1))); Object.assign(item, body, { updatedAt: '2026-07-25 18:18' }); result = ok(item); }
  else if (method === 'PATCH' && /^\/plans\/\d+\/status$/.test(url)) { const item = plans.find(row => row.id === Number(url.split('/')[2])); item.status = Number(body.status); result = ok(item); }
  else if (method === 'GET' && url === '/users') { let rows = [...platformUsers]; if (params.keyword) rows = rows.filter(item => `${item.realName}${item.username}${item.email}`.includes(params.keyword)); if (params.roleCode) rows = rows.filter(item => item.roleCode === params.roleCode); if (params.status !== '' && params.status !== undefined) rows = rows.filter(item => item.status === Number(params.status)); result = ok(page(rows, Number(params.pageNo || 1), Number(params.pageSize || 10))); }
  else if (method === 'POST' && url === '/users') { if (!/^1\d{10}$/.test(body.username)) return fail(400, '请输入有效的登录手机号'); if (platformUsers.some(item => item.username === body.username)) return fail(40901, '登录手机号已存在'); const item = { ...body, id: Math.max(...platformUsers.map(row => row.id)) + 1, roleName: body.roleCode === 'ADMIN' ? '管理员' : '售后客服', status: 1, lastLoginAt: null, createdByName: '林嘉诚', createdAt: '2026-07-25 18:18' }; platformUsers.push(item); result = ok({ user: item, initialPassword: 'Xm@123456' }); }
  else if (method === 'GET' && /^\/users\/\d+$/.test(url)) result = ok(platformUsers.find(item => item.id === Number(url.split('/').at(-1))) || null);
  else if (method === 'PUT' && /^\/users\/\d+$/.test(url)) { if (!/^1\d{10}$/.test(body.username)) return fail(400, '请输入有效的登录手机号'); if (platformUsers.some(item => item.username === body.username && item.id !== Number(url.split('/').at(-1)))) return fail(40901, '登录手机号已存在'); const item = platformUsers.find(row => row.id === Number(url.split('/').at(-1))); Object.assign(item, body, { roleName: body.roleCode === 'ADMIN' ? '管理员' : '售后客服' }); result = ok(item); }
  else if (method === 'PATCH' && /^\/users\/\d+\/status$/.test(url)) { const item = platformUsers.find(row => row.id === Number(url.split('/')[2])); if (item.current && Number(body.status) === 0) return fail(40903, '不能停用当前登录账号'); item.status = Number(body.status); result = ok(item); }
  else if (method === 'POST' && /^\/users\/\d+\/reset-password$/.test(url)) result = ok({ initialPassword: 'Xm@654321' });
  else if (method === 'DELETE' && /^\/users\/\d+$/.test(url)) { const index = platformUsers.findIndex(row => row.id === Number(url.split('/').at(-1))); if (platformUsers[index]?.current) return fail(40902, '不能删除当前登录账号'); platformUsers.splice(index, 1); result = ok(null); }
  else return fail(40400, `Mock 未实现：${method} ${url}`);
  return delay(result);
}
