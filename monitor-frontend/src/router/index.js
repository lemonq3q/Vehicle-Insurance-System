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

const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) });
router.beforeEach(to => {
  const role = localStorage.getItem('monitorRole') || 'ADMIN';
  if (to.meta.roles && !to.meta.roles.includes(role)) return '/dashboard';
  return true;
});
export default router;
