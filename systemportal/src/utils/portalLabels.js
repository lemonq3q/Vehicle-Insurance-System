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

export function getRoleName(roleCode) {
  return roleNames[roleCode] || roleCode || '-';
}

export function getStatusName(type, status) {
  return statusNames[type]?.[status] || '-';
}
