import request from './request';

export const dashboardApi = {
  /**
   * 按监控时间范围读取平台总览指标、趋势及分布数据。
   */
  get: params => request.get('/dashboard', { params })
};

export const enterpriseApi = {
  /**
   * 按关键字、状态、套餐和分页条件查询 SaaS 企业列表。
   */
  list: params => request.get('/enterprises', { params }),
  /**
   * 获取企业选择器使用的精简企业 ID 与名称集合。
   */
  options: () => request.get('/enterprises/options'),
  /**
   * 读取单个企业的资料、订阅、钱包和汇总状态。
   */
  detail: id => request.get(`/enterprises/${id}`),
  /**
   * 按时间维度读取指定企业的系统调用与业务用量趋势。
   */
  usage: (id, params) => request.get(`/enterprises/${id}/usage`, { params }),
  /**
   * 对多个企业在同一时间窗口内的用量进行横向对比。
   */
  compare: params => request.get('/enterprises/usage-comparison', { params }),
  /**
   * 分页查询企业成员及其角色、状态和加入时间。
   */
  members: (id, params) => request.get(`/enterprises/${id}/members`, { params }),
  /**
   * 获取企业余额、订单及流水数量等财务汇总指标。
   */
  financeSummary: id => request.get(`/enterprises/${id}/finance-summary`),
  /**
   * 按充值、订阅或流水资源类型分页读取企业财务明细。
   */
  finance: (id, type, params) => request.get(`/enterprises/${id}/${type}`, { params }),
  /**
   * 执行平台人工余额调账并记录原因及对应资金流水。
   */
  adjustBalance: (id, data) => request.post(`/enterprises/${id}/balance-adjustments`, data),
  /**
   * 由平台管理员为企业开通或变更指定套餐订阅。
   */
  setSubscription: (id, data) => request.put(`/enterprises/${id}/subscription`, data),
  /**
   * 取消企业当前订阅，并将操作原因放入 DELETE 请求体供审计留痕。
   */
  cancelSubscription: (id, data) => request.delete(`/enterprises/${id}/subscription`, { data })
};

export const planApi = {
  /**
   * 获取平台全部套餐及其上下架状态。
   */
  list: () => request.get('/plans'),
  /**
   * 读取套餐编辑页需要的完整额度和计费配置。
   */
  detail: id => request.get(`/plans/${id}`),
  /**
   * 新建 SaaS 套餐。
   */
  create: data => request.post('/plans', data),
  /**
   * 更新已有套餐的价格、周期及业务额度。
   */
  update: (id, data) => request.put(`/plans/${id}`, data),
  /**
   * 单独切换套餐上架或停用状态，避免状态操作覆盖其他配置。
   */
  updateStatus: (id, data) => request.patch(`/plans/${id}/status`, data)
};

export const userApi = {
  /**
   * 分页查询监控后台账号。
   */
  list: params => request.get('/users', { params }),
  /**
   * 获取后台账号详情及近期操作记录。
   */
  detail: id => request.get(`/users/${id}`),
  /**
   * 创建新的平台管理或只读账号。
   */
  create: data => request.post('/users', data),
  /**
   * 更新后台账号基础资料和角色。
   */
  update: (id, data) => request.put(`/users/${id}`, data),
  /**
   * 启用或停用后台账号。
   */
  updateStatus: (id, data) => request.patch(`/users/${id}/status`, data),
  /**
   * 管理员为指定后台账号重置密码。
   */
  resetPassword: (id, data) => request.post(`/users/${id}/reset-password`, data),
  /**
   * 删除后台账号并携带删除原因供服务端审计。
   */
  remove: (id, data) => request.delete(`/users/${id}`, { data })
};
