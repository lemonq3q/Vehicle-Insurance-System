/**
 * 提醒生命周期联调样例：旧提醒只要仍生效就可见，失效和人工已处理分别保留独立状态。
 * 内存数据由现有 Mock Adapter 使用，不参与生产请求或后端业务条件判断。
 */
export const reminders = [
  { id: 1, enterpriseId: 1, enterpriseNameSnapshot: '示例企业', enterpriseCode: 'ENT001', reminderType: 'WALLET_BALANCE_NEGATIVE', typeName: '账户余额为负', categoryCode: 'ACCOUNT_BALANCE', categoryName: '账户资金', reminderStage: 'NEGATIVE', stageLevel: 10, severity: 'WARNING', title: '账户余额为负，请尽快充值', content: '该提醒超过一个月，但欠费尚未解决，仍然生效。', lastTriggeredAt: '2026-07-01T04:00:00', isActive: 1, invalidatedAt: null, lifecycleVersion: 1, processStatus: 0, revision: 1 },
  { id: 2, enterpriseId: 1, enterpriseNameSnapshot: '示例企业', reminderType: 'WALLET_BALANCE_NEGATIVE', typeName: '账户余额为负', categoryCode: 'ACCOUNT_BALANCE', categoryName: '账户资金', reminderStage: 'NEGATIVE', stageLevel: 10, severity: 'WARNING', title: '上一欠费周期提醒', content: '充值后余额恢复，原提醒已失效。', lastTriggeredAt: '2026-08-01T04:00:00', isActive: 0, invalidatedAt: '2026-09-28T12:00:00', lifecycleVersion: 2, processStatus: 0, revision: 2 },
  { id: 3, enterpriseId: 1, enterpriseNameSnapshot: '示例企业', reminderType: 'WALLET_BALANCE_NEGATIVE', typeName: '账户余额为负', categoryCode: 'ACCOUNT_BALANCE', categoryName: '账户资金', reminderStage: 'NEGATIVE', stageLevel: 10, severity: 'WARNING', title: '已联系企业的欠费提醒', content: '客服已跟进，业务风险仍然存在。', lastTriggeredAt: '2026-09-20T04:00:00', isActive: 1, invalidatedAt: null, lifecycleVersion: 1, processStatus: 1, processedAt: '2026-09-21T10:00:00', revision: 2 }
];
