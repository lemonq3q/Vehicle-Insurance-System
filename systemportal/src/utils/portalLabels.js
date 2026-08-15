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

 * * 将企业成员角色编码转换为门户中文名称，未知编码保留原值便于发现新增角色。

 */
export function getRoleName(roleCode) {
  return roleNames[roleCode] || roleCode || '-';
}

/**

 * * 根据订单类别分别翻译订阅订单或充值订单状态，无法识别时显示占位符。

 */
export function getStatusName(type, status) {
  return statusNames[type]?.[status] || '-';
}
