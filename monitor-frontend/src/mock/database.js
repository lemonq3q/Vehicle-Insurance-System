const days = Array.from({ length: 30 }, (_, index) => `2026-07-${String(index + 1).padStart(2, '0')}`);

export const enterprises = [
  { id: 1, code: 'ENT-202607-0012', name: '杭州星途汽车服务有限公司', status: 1, contactName: '陈晓峰', contactPhone: '138****6821', source: '后台创建', remark: '重点企业客户，季度回访', memberCount: 32, memberLimit: 50, balance: 28640, planId: 3, planName: '企业版', subscriptionEndDate: '2027-03-16', lastActiveAt: '2026-07-25 18:16', createdAt: '2026-01-18 09:20' },
  { id: 2, code: 'ENT-202606-0086', name: '上海致远保险代理有限公司', status: 1, contactName: '徐立', contactPhone: '139****2186', source: '自主注册', remark: '续费跟进中', memberCount: 18, memberLimit: 30, balance: 6820.5, planId: 2, planName: '专业版', subscriptionEndDate: '2026-07-28', lastActiveAt: '2026-07-25 17:42', createdAt: '2026-02-11 10:00' },
  { id: 3, code: 'ENT-202604-0031', name: '宁波安行汽车有限公司', status: 2, contactName: '王谦', contactPhone: '137****4412', source: '后台创建', remark: '', memberCount: 9, memberLimit: 10, balance: 0, planId: null, planName: null, subscriptionEndDate: null, lastActiveAt: '2026-07-25 15:06', createdAt: '2026-04-03 14:30' },
  { id: 4, code: 'ENT-202601-0018', name: '苏州鼎盛汽车服务有限公司', status: 0, contactName: '李明', contactPhone: '136****9261', source: '自主注册', remark: '企业已停用', memberCount: 0, memberLimit: 30, balance: 1260, planId: 2, planName: '专业版', subscriptionEndDate: '2026-06-30', lastActiveAt: '2026-06-29 11:20', createdAt: '2026-01-08 08:50' },
  { id: 5, code: 'ENT-202605-0042', name: '嘉兴诚泰汽贸有限公司', status: 1, contactName: '赵慧', contactPhone: '158****7103', source: '后台创建', remark: '', memberCount: 14, memberLimit: 30, balance: 9680, planId: 2, planName: '专业版', subscriptionEndDate: '2026-12-31', lastActiveAt: '2026-07-25 16:25', createdAt: '2026-05-06 13:10' }
];

export const plans = [
  { id: 1, code: 'BASIC', name: '基础版', description: '适合小型车商和初创团队', billingCycle: 'YEAR', durationDays: 365, price: 3600, listPrice: 4200, memberLimit: 10, workorderLimit: 1000, sortOrder: 10, status: 1, updatedAt: '2026-07-16 10:20' },
  { id: 2, code: 'PRO', name: '专业版', description: '适合稳定增长的保险服务团队', billingCycle: 'YEAR', durationDays: 365, price: 12800, listPrice: 14800, memberLimit: 30, workorderLimit: 5000, sortOrder: 20, status: 1, updatedAt: '2026-07-16 10:30' },
  { id: 3, code: 'ENTERPRISE', name: '企业版', description: '适合多门店与大型运营团队', billingCycle: 'YEAR', durationDays: 365, price: 28800, listPrice: 32800, memberLimit: 50, workorderLimit: 10000, sortOrder: 30, status: 1, updatedAt: '2026-07-16 10:40' },
  { id: 4, code: 'TRIAL', name: '体验版', description: '用于售前演示和短期试用', billingCycle: 'DAY', durationDays: 30, price: 0, listPrice: 0, memberLimit: 5, workorderLimit: 1000, sortOrder: 40, status: 0, updatedAt: '2026-07-15 09:00' }
];

export const platformUsers = [
  { id: 1, username: 'linjc', realName: '林嘉诚', phone: '13812341028', email: 'linjc@xiaoma.com', roleCode: 'ADMIN', roleName: '管理员', status: 1, lastLoginAt: '2026-07-25 18:18', passwordChangedAt: '2026-07-22 08:30', createdByName: '系统初始化', createdAt: '2026-07-22 08:20', current: true },
  { id: 2, username: 'fangsy', realName: '方思雨', phone: '18612345227', email: 'fangsy@xiaoma.com', roleCode: 'CUSTOMER_SERVICE', roleName: '售后客服', status: 1, lastLoginAt: '2026-07-25 17:56', passwordChangedAt: '2026-07-22 08:30', createdByName: '林嘉诚', createdAt: '2026-07-22 08:30' },
  { id: 3, username: 'shenhy', realName: '沈浩宇', phone: '13712348063', email: 'shenhy@xiaoma.com', roleCode: 'CUSTOMER_SERVICE', roleName: '售后客服', status: 1, lastLoginAt: '2026-07-24 17:42', passwordChangedAt: '2026-07-22 08:40', createdByName: '林嘉诚', createdAt: '2026-07-22 08:40' },
  { id: 4, username: 'luxy', realName: '陆欣怡', phone: '15912343268', email: 'luxy@xiaoma.com', roleCode: 'CUSTOMER_SERVICE', roleName: '售后客服', status: 1, lastLoginAt: '2026-07-24 16:28', passwordChangedAt: '2026-07-22 08:50', createdByName: '林嘉诚', createdAt: '2026-07-22 08:50' },
  { id: 5, username: 'service_test', realName: '测试账号', phone: '', email: '', roleCode: 'CUSTOMER_SERVICE', roleName: '售后客服', status: 0, lastLoginAt: null, passwordChangedAt: '2026-07-22 09:00', createdByName: '林嘉诚', createdAt: '2026-07-22 09:00' }
];

export const members = [
  { id: 101, enterpriseId: 1, realName: '陈晓峰', username: 'chenxf', phone: '138****6821', roleName: '拥有者', status: 1, joinedAt: '2026-01-18', lastLoginAt: '2026-07-25 18:16' },
  { id: 102, enterpriseId: 1, realName: '周雨婷', username: 'zhouyt', phone: '186****2376', roleName: '管理员', status: 1, joinedAt: '2026-02-03', lastLoginAt: '2026-07-25 17:48' },
  { id: 103, enterpriseId: 1, realName: '孙浩然', username: 'sunhr', phone: '137****8462', roleName: '出单员', status: 1, joinedAt: '2026-03-12', lastLoginAt: '2026-07-25 16:32' },
  { id: 104, enterpriseId: 1, realName: '吴思远', username: 'wusy', phone: '159****1278', roleName: '出单员', status: 1, joinedAt: '2026-04-08', lastLoginAt: '2026-07-24 17:46' },
  { id: 105, enterpriseId: 1, realName: '高静怡', username: 'gaojy', phone: '188****5506', roleName: '出单员', status: 0, joinedAt: '2026-04-16', lastLoginAt: '2026-07-09 14:18' }
];

export const dailyUsage = enterprises.flatMap((enterprise, enterpriseIndex) => days.map((statDate, dayIndex) => ({
  statDate, enterpriseId: enterprise.id,
  workorderCount: enterprise.status === 0 ? 0 : 70 + enterpriseIndex * 12 + dayIndex * 3,
  requestCount: enterprise.status === 0 ? 0 : 5200 + enterpriseIndex * 650 + dayIndex * 115,
  ocrCount: enterprise.status === 0 ? 0 : 680 + enterpriseIndex * 90 + dayIndex * 18
})));

export const finance = {
  recharges: [
    { id: 1, enterpriseId: 1, orderNo: 'RC202607180028', amount: 20000, channel: 'WECHAT', status: 2, paidAt: '2026-07-18 14:26', createdAt: '2026-07-18 14:20' },
    { id: 2, enterpriseId: 1, orderNo: 'RC202606120016', amount: 10000, channel: 'ALIPAY', status: 2, paidAt: '2026-06-12 09:38', createdAt: '2026-06-12 09:32' }
  ],
  subscriptions: [
    { id: 1, enterpriseId: 1, orderNo: 'SO202603160012', planName: '企业版', amount: 28800, status: 2, startedAt: '2026-03-16', endedAt: '2027-03-16', createdAt: '2026-03-16 11:20' },
    { id: 2, enterpriseId: 1, orderNo: 'SO202602030006', planName: '专业版', amount: 12800, status: 2, startedAt: '2026-02-03', endedAt: '2027-02-03', createdAt: '2026-02-03 16:08' }
  ],
  transactions: [
    { id: 1, enterpriseId: 1, transactionNo: 'TX202607180041', type: 'RECHARGE', amount: 20000, balanceAfter: 28640, referenceNo: 'RC202607180028', remark: '充值入账', createdAt: '2026-07-18 14:26' },
    { id: 2, enterpriseId: 1, transactionNo: 'TX202603160022', type: 'BUY_PLAN', amount: -28800, balanceAfter: 8640, referenceNo: 'SO202603160012', remark: '购买企业版', createdAt: '2026-03-16 11:20' }
  ]
};
