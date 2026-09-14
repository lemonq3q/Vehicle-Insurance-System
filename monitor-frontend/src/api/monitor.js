import request from "./request";

export const authApi = {
  /** 使用监控平台独立账号密码建立 JWT 会话。 */
  login: (data) => request.post("/auth/login", data),
  /** 读取当前令牌对应的最新账号和角色，供刷新页面恢复登录上下文。 */
  me: () => request.get("/auth/me"),
  /** 注销服务端 Redis 会话；本地清理由调用方在 finally 中保证执行。 */
  logout: () => request.post("/auth/logout"),
  /** 更新当前登录人的姓名、手机号和邮箱。 */
  updateProfile: (data) => request.put("/auth/profile", data),
  /** 校验当前密码并设置新密码，成功后服务端会使当前会话失效。 */
  changePassword: (data) => request.put("/auth/password", data),
};

export const dashboardApi = {
  /**
   * 一次读取本月与上月五项核心指标，供顶部卡片展示环比。
   */
  summary: () => request.get("/dashboard/summary"),
  /** 读取不受其他筛选影响的近十二个月已支付充值流水。 */
  rechargeTrend: () => request.get("/dashboard/recharge-trend"),
  /** 按系统调用或 OCR 指标及独立范围读取自动分粒度趋势。 */
  usageTrend: (params) => request.get("/dashboard/usage-trend", { params }),
  /** 按独立范围和 Top 数量读取企业系统调用排行。 */
  ranking: (params) => request.get("/dashboard/enterprise-ranking", { params }),
};

export const reminderApi = {
  /** 按组合筛选和分页条件读取监控平台提醒处理队列。 */
  list: (params) => request.get("/reminders", { params }),
  /** 一次读取类别、具体提醒类型和相关企业选项。 */
  filterOptions: () => request.get("/reminders/filter-options"),
  /** 根据名称或编码关键词远程搜索企业，供提醒筛选联想使用。 */
  enterpriseOptions: (keyword) =>
    request.get("/reminders/enterprise-options", { params: { keyword } }),
  /** 提交页面所见版本号，将提醒安全地标记为已处理。 */
  markProcessed: (id, data) =>
    request.patch(`/reminders/${id}/processed`, data),
  /** 将误标为已处理的提醒按当前版本恢复到待处理队列。 */
  restoreUnprocessed: (id, data) =>
    request.patch(`/reminders/${id}/unprocessed`, data),
};

export const systemLogApi = {
  /** 按日志类别、严重等级和发生日期范围分页读取统一系统日志。 */
  list: (params) => request.get("/system-logs", { params }),
};

export const promotionApi = {
  /** 按组合条件分页读取销售推广目标。 */
  targets: params => request.get('/promotion-targets', { params }),
  /** 下载与后端导入解析器一致的官方 xlsx 模板。 */
  template: () => request.get('/promotion-targets/template', { responseType: 'blob' }),
  /** 手工创建一个推广目标。 */
  createTarget: data => request.post('/promotion-targets', data),
  /** 更新推广目标资料和可推广状态。 */
  updateTarget: (id, data) => request.put(`/promotion-targets/${id}`, data),
  /** 软删除推广目标，此业务不要求删除原因。 */
  removeTarget: id => request.delete(`/promotion-targets/${id}`),
  /** 按官方模板批量导入目标主数据。 */
  importTargets: file => { const data = new FormData(); data.append('file', file); return request.post('/promotion-targets/import', data); },
  /** 一次性解析推广 Excel 并返回全部有效行，名单仅由当前页面保存。 */
  previewImport: file => { const data = new FormData(); data.append('file', file); return request.post('/promotions/import-preview', data); },
  /** 计算选定渠道下的有效人数及 6000 条截断状态。 */
  preview: data => request.post('/promotions/preview', data),
  /** 调用后端当前的电话或邮箱 mock 渠道进行群发。 */
  send: data => request.post('/promotions/send', data),
};

export const visitorLeadApi = {
  /** 按完整游客编号精确查询；编号为空时分页读取最新官网提交。 */
  list: params => request.get('/visitor-leads', { params }),
};

export const enterpriseApi = {
  /**
   * 按关键字、状态、套餐和分页条件查询 SaaS 企业列表。
   */
  list: (params) => request.get("/enterprises", { params }),
  /**
   * 获取企业选择器使用的精简企业 ID 与名称集合。
   */
  options: (keyword) => request.get("/enterprises/options", { params: { keyword } }),
  /** 按统计指标、周期和数量读取系统推荐的 Top 企业。 */
  usageTop: (params) => request.get("/enterprises/usage-top", { params }),
  /**
   * 读取单个企业的资料、订阅、钱包和汇总状态。
   */
  detail: (id) => request.get(`/enterprises/${id}`),
  /**
   * 按时间维度读取指定企业的系统调用与业务用量趋势。
   */
  usage: (id, params) => request.get(`/enterprises/${id}/usage`, { params }),
  /**
   * 读取企业筛选和订阅设置使用的套餐精简选项。
   */
  subscriptionPlans: () => request.get("/enterprises/subscription-plans"),
  /**
   * 对多个企业在同一时间窗口内的用量进行横向对比。
   */
  compare: (params) => request.get("/enterprises/usage-comparison", { params }),
  /**
   * 分页查询企业成员及其角色、状态和加入时间。
   */
  members: (id, params) =>
    request.get(`/enterprises/${id}/members`, { params }),
  /**
   * 获取企业余额、订单及流水数量等财务汇总指标。
   */
  financeSummary: (id) =>
    request.get(`/enterprises/${id}/finance-summary`),
  /**
   * 按充值、订阅或流水资源类型分页读取企业财务明细。
   */
  finance: (id, type, params) =>
    request.get(`/enterprises/${id}/${type}`, { params }),
  /**
   * 执行平台人工余额调账并记录原因及对应资金流水。
   */
  adjustBalance: (id, data) =>
    request.post(`/enterprises/${id}/balance-adjustments`, data),
  /**
   * 由平台管理员为企业开通或变更指定套餐订阅。
   */
  setSubscription: (id, data) =>
    request.put(`/enterprises/${id}/subscription`, data),
  /**
   * 取消企业当前订阅，并将操作原因放入 DELETE 请求体供审计留痕。
   */
  cancelSubscription: (id, data) =>
    request.delete(`/enterprises/${id}/subscription`, { data }),
};

export const planApi = {
  /**
   * 获取平台全部套餐及其上下架状态。
   */
  list: () => request.get("/plans"),
  /**
   * 读取套餐编辑页需要的完整额度和计费配置。
   */
  detail: (id) => request.get(`/plans/${id}`),
  /**
   * 新建 SaaS 套餐。
   */
  create: (data) => request.post("/plans", data),
  /**
   * 更新已有套餐的价格、周期及业务额度。
   */
  update: (id, data) => request.put(`/plans/${id}`, data),
  /**
   * 单独切换套餐上架或停用状态，避免状态操作覆盖其他配置。
   */
  updateStatus: (id, data) => request.patch(`/plans/${id}/status`, data),
};

export const userApi = {
  /**
   * 分页查询监控后台账号。
   */
  list: (params) => request.get("/users", { params }),
  /**
   * 获取后台账号详情及近期操作记录。
   */
  detail: (id) => request.get(`/users/${id}`),
  /**
   * 创建新的平台管理或只读账号。
   */
  create: (data) => request.post("/users", data),
  /**
   * 更新后台账号基础资料和角色。
   */
  update: (id, data) => request.put(`/users/${id}`, data),
  /**
   * 启用或停用后台账号。
   */
  updateStatus: (id, data) =>
    request.patch(`/users/${id}/status`, data),
  /**
   * 管理员为指定后台账号重置密码。
   */
  resetPassword: (id, data) =>
    request.post(`/users/${id}/reset-password`, data),
  /**
   * 删除后台账号并携带删除原因供服务端审计。
   */
  remove: (id, data) => request.delete(`/users/${id}`, { data }),
};
