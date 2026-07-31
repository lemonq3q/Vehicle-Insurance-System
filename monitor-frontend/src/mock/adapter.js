import { dailyUsage, enterprises, finance, members, plans, platformUsers } from './database';

const ok = data => ({ code: 200, message: 'success', data, requestId: `mock-${Date.now()}` });
const fail = (code, message) => Promise.reject(Object.assign(new Error(message), { code }));
const page = (list, pageNo = 1, pageSize = 10) => ({ list: list.slice((pageNo - 1) * pageSize, pageNo * pageSize), pageNo, pageSize, total: list.length });
const parseBody = data => typeof data === 'string' ? JSON.parse(data || '{}') : (data || {});
const delay = value => new Promise(resolve => setTimeout(() => resolve({ data: value, status: 200, headers: {}, config: {} }), 180));

function usageFor(ids, days = 14) {
  const selected = dailyUsage.filter(item => ids.includes(item.enterpriseId)).slice(-days * ids.length);
  const dates = [...new Set(selected.map(item => item.statDate))];
  return { dates, enterprises: ids.map(id => ({ enterpriseId: id, enterpriseName: enterprises.find(item => item.id === id)?.name, points: dates.map(date => selected.find(item => item.enterpriseId === id && item.statDate === date) || { statDate: date, workorderCount: 0, requestCount: 0, ocrCount: 0 }) })) };
}

export async function mockAdapter(config) {
  const method = (config.method || 'get').toUpperCase();
  const url = config.url.replace(/^\/api\/monitor/, '');
  const params = config.params || {};
  const body = parseBody(config.data);
  let result;

  if (method === 'GET' && url === '/dashboard') {
    const system = usageFor(enterprises.map(item => item.id), Number(params.days || 14));
    const totals = system.dates.map(date => dailyUsage.filter(item => item.statDate === date).reduce((sum, item) => ({ statDate: date, workorderCount: sum.workorderCount + item.workorderCount, requestCount: sum.requestCount + item.requestCount, ocrCount: sum.ocrCount + item.ocrCount }), { workorderCount: 0, requestCount: 0, ocrCount: 0 }));
    const latest = totals.at(-1);
    result = ok({ enterpriseCount: enterprises.length, userCount: 1842, monthRechargeAmount: 428600, today: latest, trends: totals, ranking: enterprises.map(item => ({ enterpriseId: item.id, enterpriseName: item.name, requestCount: dailyUsage.filter(row => row.enterpriseId === item.id).at(-1).requestCount })).sort((a,b) => b.requestCount-a.requestCount).slice(0,5), updatedAt: '2026-07-25 18:18' });
  } else if (method === 'GET' && url === '/enterprises') {
    let rows = [...enterprises];
    if (params.keyword) rows = rows.filter(item => `${item.name}${item.code}`.includes(params.keyword));
    if (params.status !== '' && params.status !== undefined) rows = rows.filter(item => item.status === Number(params.status));
    if (params.planId) rows = rows.filter(item => item.planId === Number(params.planId));
    if (params.expireDays) { const limit = new Date('2026-07-25'); limit.setDate(limit.getDate() + Number(params.expireDays)); rows = rows.filter(item => item.subscriptionEndDate && new Date(item.subscriptionEndDate) <= limit); }
    rows = rows.map(item => ({ ...item, todayUsage: dailyUsage.filter(row => row.enterpriseId === item.id).at(-1) }));
    result = ok(page(rows, Number(params.pageNo || 1), Number(params.pageSize || 10)));
  } else if (method === 'GET' && url === '/enterprises/options') result = ok(enterprises.map(({ id, name, code }) => ({ id, name, code })));
  else if (method === 'GET' && /^\/enterprises\/\d+$/.test(url)) result = ok(enterprises.find(item => item.id === Number(url.split('/').at(-1))) || null);
  else if (method === 'GET' && /^\/enterprises\/\d+\/usage$/.test(url)) result = ok(usageFor([Number(url.split('/')[2])], Number(params.days || 14)).enterprises[0]);
  else if (method === 'GET' && url === '/enterprises/usage-comparison') result = ok(usageFor(String(params.enterpriseIds || '').split(',').map(Number), Number(params.days || 30)));
  else if (method === 'GET' && /^\/enterprises\/\d+\/members$/.test(url)) {
    const enterpriseId = Number(url.split('/')[2]); let rows = members.filter(item => item.enterpriseId === enterpriseId);
    if (params.keyword) rows = rows.filter(item => `${item.realName}${item.username}${item.phone}`.includes(params.keyword));
    if (params.roleName) rows = rows.filter(item => item.roleName === params.roleName);
    if (params.status !== '' && params.status !== undefined) rows = rows.filter(item => item.status === Number(params.status));
    result = ok(page(rows, Number(params.pageNo || 1), Number(params.pageSize || 10)));
  } else if (method === 'GET' && /^\/enterprises\/\d+\/finance-summary$/.test(url)) result = ok({ balance: enterprises.find(item => item.id === Number(url.split('/')[2]))?.balance || 0, totalRecharge: 86000, totalSubscriptionExpense: 42800, monthTransactionCount: 18 });
  else if (method === 'GET' && /^\/enterprises\/\d+\/(recharge-orders|subscription-orders|wallet-transactions)$/.test(url)) {
    const enterpriseId = Number(url.split('/')[2]); const type = url.split('/')[3]; const source = type === 'recharge-orders' ? finance.recharges : type === 'subscription-orders' ? finance.subscriptions : finance.transactions;
    result = ok(page(source.filter(item => item.enterpriseId === enterpriseId), Number(params.pageNo || 1), Number(params.pageSize || 10)));
  } else if (method === 'POST' && /^\/enterprises\/\d+\/balance-adjustments$/.test(url)) { const item = enterprises.find(row => row.id === Number(url.split('/')[2])); item.balance += Number(body.amount); result = ok({ balance: item.balance }); }
  else if (method === 'PUT' && /^\/enterprises\/\d+\/subscription$/.test(url)) { const item = enterprises.find(row => row.id === Number(url.split('/')[2])); const plan = plans.find(row => row.id === Number(body.planId)); Object.assign(item, { planId: plan.id, planName: plan.name, memberLimit: plan.memberLimit, subscriptionEndDate: body.endDate }); result = ok(item); }
  else if (method === 'DELETE' && /^\/enterprises\/\d+\/subscription$/.test(url)) { const item = enterprises.find(row => row.id === Number(url.split('/')[2])); Object.assign(item, { planId: null, planName: null, subscriptionEndDate: null }); result = ok(null); }
  else if (method === 'GET' && url === '/plans') result = ok([...plans].sort((a,b) => a.sortOrder-b.sortOrder));
  else if (method === 'POST' && url === '/plans') { const item = { ...body, id: Math.max(...plans.map(row => row.id)) + 1, updatedAt: '2026-07-25 18:18' }; plans.push(item); result = ok(item); }
  else if (method === 'GET' && /^\/plans\/\d+$/.test(url)) result = ok(plans.find(item => item.id === Number(url.split('/').at(-1))) || null);
  else if (method === 'PUT' && /^\/plans\/\d+$/.test(url)) { const item = plans.find(row => row.id === Number(url.split('/').at(-1))); Object.assign(item, body, { updatedAt: '2026-07-25 18:18' }); result = ok(item); }
  else if (method === 'PATCH' && /^\/plans\/\d+\/status$/.test(url)) { const item = plans.find(row => row.id === Number(url.split('/')[2])); item.status = Number(body.status); result = ok(item); }
  else if (method === 'GET' && url === '/users') { let rows = [...platformUsers]; if (params.keyword) rows = rows.filter(item => `${item.realName}${item.username}${item.phone}${item.email}`.includes(params.keyword)); if (params.roleCode) rows = rows.filter(item => item.roleCode === params.roleCode); if (params.status !== '' && params.status !== undefined) rows = rows.filter(item => item.status === Number(params.status)); result = ok(page(rows, Number(params.pageNo || 1), Number(params.pageSize || 10))); }
  else if (method === 'POST' && url === '/users') { if (platformUsers.some(item => item.username === body.username)) return fail(40901, '登录账号已存在'); const item = { ...body, id: Math.max(...platformUsers.map(row => row.id)) + 1, roleName: body.roleCode === 'ADMIN' ? '管理员' : '售后客服', status: 1, lastLoginAt: null, createdByName: '林嘉诚', createdAt: '2026-07-25 18:18' }; platformUsers.push(item); result = ok({ user: item, initialPassword: 'Xm@123456' }); }
  else if (method === 'GET' && /^\/users\/\d+$/.test(url)) result = ok(platformUsers.find(item => item.id === Number(url.split('/').at(-1))) || null);
  else if (method === 'PUT' && /^\/users\/\d+$/.test(url)) { const item = platformUsers.find(row => row.id === Number(url.split('/').at(-1))); Object.assign(item, body, { roleName: body.roleCode === 'ADMIN' ? '管理员' : '售后客服' }); result = ok(item); }
  else if (method === 'PATCH' && /^\/users\/\d+\/status$/.test(url)) { const item = platformUsers.find(row => row.id === Number(url.split('/')[2])); if (item.current && Number(body.status) === 0) return fail(40903, '不能停用当前登录账号'); item.status = Number(body.status); result = ok(item); }
  else if (method === 'POST' && /^\/users\/\d+\/reset-password$/.test(url)) result = ok({ initialPassword: 'Xm@654321' });
  else if (method === 'DELETE' && /^\/users\/\d+$/.test(url)) { const index = platformUsers.findIndex(row => row.id === Number(url.split('/').at(-1))); if (platformUsers[index]?.current) return fail(40902, '不能删除当前登录账号'); platformUsers.splice(index, 1); result = ok(null); }
  else return fail(40400, `Mock 未实现：${method} ${url}`);
  return delay(result);
}
