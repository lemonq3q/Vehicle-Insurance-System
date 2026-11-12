/**
 * 双险种续保联调数据；提醒数量按工单去重，已处理险种在后续年度重新进入未处理周期。
 * 页面不内嵌这些数据，可由现有请求适配方式离线返回。
 */
export const dualRenewalMock = {
  code: 200, data: { total: 1, table: [{
    id: '21', code: 'WO21', commercialReminder: true, compulsoryReminder: true,
    commercialRenewalStatus: 0, compulsoryRenewalStatus: 0,
    commercialActiveCycle: 1, compulsoryActiveCycle: 1, followUpRes: ''
  }] }
};
export const singleRenewalMock = {
  code: 200, data: { total: 1, table: [{
    ...dualRenewalMock.data.table[0], compulsoryReminder: false, compulsoryActiveCycle: 0
  }] }
};
export const handledCommercialRenewalMock = {
  code: 200, data: { total: 1, table: [{
    ...dualRenewalMock.data.table[0], commercialReminder: false,
    commercialRenewalStatus: 1, commercialActiveCycle: 1,
    compulsoryReminder: true, compulsoryRenewalStatus: 0, compulsoryActiveCycle: 1
  }] }
};
export const renewalCountMock = { code: 200, data: { selfCount: 1, allCount: 1 } };
export const renewalFollowUpMock = { code: 200, msg: '已保存续保跟进' };
export const dualRenewalFollowUpRequestMock = { commercialStatus: 1, compulsoryStatus: 2, followUpRes: '商业续保，交强流失' };
export const unprocessedRenewalFollowUpRequestMock = { commercialStatus: 0, followUpRes: '重新进入待处理' };
export const legacySingleRenewalFollowUpRequestMock = { scope: 'COMMERCIAL', status: 1, followUpRes: '' };
export const renewalEmptyMock = { code: 200, data: { total: 0, table: [] } };
