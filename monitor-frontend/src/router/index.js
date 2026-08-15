import { createRouter, createWebHistory } from 'vue-router';

const routes = [{
  path: '/', component: () => import('@/layout/MonitorLayout.vue'), redirect: '/dashboard', children: [
    { path: 'dashboard', component: () => import('@/views/DashboardPage.vue'), meta: { title: '仪表盘' } },
    { path: 'enterprises', component: () => import('@/views/enterprise/EnterpriseListPage.vue'), meta: { title: '企业列表' } },
    { path: 'enterprises/compare', component: () => import('@/views/enterprise/EnterpriseComparePage.vue'), meta: { title: '企业用量对比' } },
    { path: 'enterprises/:id/overview', component: () => import('@/views/enterprise/EnterpriseDetailPage.vue'), meta: { title: '企业详情' } },
    { path: 'enterprises/:id/members', component: () => import('@/views/enterprise/EnterpriseMembersPage.vue'), meta: { title: '企业员工' } },
    { path: 'enterprises/:id/finance', component: () => import('@/views/enterprise/EnterpriseFinancePage.vue'), meta: { title: '企业财务' } },
    { path: 'plans', component: () => import('@/views/plan/PlanListPage.vue'), meta: { title: '套餐管理' } },
    { path: 'plans/:id/edit', component: () => import('@/views/plan/PlanEditPage.vue'), meta: { title: '编辑套餐' } },
    { path: 'platform-users', component: () => import('@/views/user/UserListPage.vue'), meta: { title: '用户管理', roles: ['ADMIN'] } },
    { path: 'platform-users/:id', component: () => import('@/views/user/UserDetailPage.vue'), meta: { title: '用户详情', roles: ['ADMIN'] } }
  ]
}];

/**
 * 监控后台使用统一布局及路由级懒加载，并在页面切换时回到内容顶部。
 */
const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) });
/**
 * 根据本地监控角色执行前端路由保护；带 roles 元数据的管理员页面对 VIEWER 重定向到仪表盘。
 * 后端仍需执行最终权限校验，前端守卫只负责避免暴露不可操作页面。
 */
router.beforeEach(to => {
  const role = localStorage.getItem('monitorRole') || 'ADMIN';
  if (to.meta.roles && !to.meta.roles.includes(role)) return '/dashboard';
  return true;
});
export default router;
