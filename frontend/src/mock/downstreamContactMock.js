/**
 * 下游联系人扁平化后的接口联调样例；由现有请求适配机制选择使用，不在页面中写死数据。
 * 新增、编辑、详情与选项均复用 contact/phone，工单只保存 submitterName 字符串。
 */
export const downstreamDetailMock = {
  code: 200, data: {
    id: '17', code: 'DS00017', name: '示例车商', type: '车商店铺', location: '440100',
    address: '示例地址', contact: '张三', phone: '13800000000', bank: '示例银行',
    bankCardNum: '6222020200000000000', businessArea: []
  }
};

export const downstreamListMock = {
  code: 200, data: { total: 1, table: [downstreamDetailMock.data] }
};

export const downstreamEmptyMock = { code: 200, data: { total: 0, table: [] } };
export const downstreamSaveMock = { code: 200, msg: '已更新1条数据' };
export const downstreamConflictMock = { code: 400, msg: '当前企业已存在同名下游' };

export const workorderSubmitterMock = {
  code: 200, data: { id: '21', createMerchantId: '17', submitterName: '李四', createBy: '8', createUserName: '李四' }
};

/**
 * 接单、派单及续保重新分配共用系统用户选项，只包含本企业启用成员。
 * 历史处理人的显示仍来自工单快照，不要求出现在当前可分配选项中。
 */
export const handlerOptionsMock = {
  code: 200, data: [{ id: '8', realName: '张三', username: 'zhangsan', email: 'zhangsan@example.com', idNum: null, status: 1 }]
};
