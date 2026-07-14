import { createRouter, createWebHistory } from 'vue-router';
import store from '@/store';

const MarketingPage = () => import('@/views/MarketingPage.vue');
const AuthPage = () => import('@/views/AuthPage.vue');
const PortalLayout = () => import('@/layout/PortalLayout.vue');
const DashboardPage = () => import('@/views/DashboardPage.vue');
const EnterpriseInfoPage = () => import('@/views/enterprise/EnterpriseInfoPage.vue');
const EnterpriseMembersPage = () => import('@/views/enterprise/EnterpriseMembersPage.vue');
const SubscriptionServicePage = () => import('@/views/finance/SubscriptionServicePage.vue');
const SubscriptionOrderDetailPage = () => import('@/views/finance/SubscriptionOrderDetailPage.vue');
const RechargePlaceholderPage = () => import('@/views/finance/RechargePlaceholderPage.vue');
const RechargeOrdersPage = () => import('@/views/finance/RechargeOrdersPage.vue');
const SubscriptionOrdersPage = () => import('@/views/finance/SubscriptionOrdersPage.vue');
const WalletTransactionsPage = () => import('@/views/finance/WalletTransactionsPage.vue');
const UserCenterPage = () => import('@/views/user/UserCenterPage.vue');
const HelpCenterPage = () => import('@/views/help/HelpCenterPage.vue');

const routes = [
  {
    path: '/',
    name: 'marketing',
    component: MarketingPage,
    meta: { title: '小马e保门户' }
  },
  {
    path: '/login',
    name: 'login',
    component: AuthPage,
    meta: { guest: true, title: '登录' }
  },
  {
    path: '/portal',
    component: PortalLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/portal/dashboard' },
      { path: 'dashboard', name: 'dashboard', component: DashboardPage, meta: { title: '仪表盘' } },
      { path: 'enterprise/info', name: 'enterprise-info', component: EnterpriseInfoPage, meta: { title: '企业信息' } },
      { path: 'enterprise/members', name: 'enterprise-members', component: EnterpriseMembersPage, meta: { title: '企业人员' } },
      { path: 'finance/subscription', name: 'finance-subscription', component: SubscriptionServicePage, meta: { title: '订阅服务' } },
      { path: 'finance/subscription/order/:planId', name: 'finance-subscription-order-detail', component: SubscriptionOrderDetailPage, meta: { title: '订阅订单详情' } },
      { path: 'finance/recharge', name: 'finance-recharge', component: RechargePlaceholderPage, meta: { title: '余额充值' } },
      { path: 'finance/recharges', name: 'finance-recharges', component: RechargeOrdersPage, meta: { title: '充值订单' } },
      { path: 'finance/orders', name: 'finance-orders', component: SubscriptionOrdersPage, meta: { title: '订阅订单' } },
      { path: 'finance/transactions', name: 'finance-transactions', component: WalletTransactionsPage, meta: { title: '资金明细' } },
      { path: 'user/profile', name: 'user-profile', component: UserCenterPage, meta: { title: '用户中心' } },
      { path: 'help', name: 'help', component: HelpCenterPage, meta: { title: '帮助中心' } }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  }
});

router.beforeEach(async (to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 小马e保` : '小马e保';
  if (to.meta.requiresAuth && !store.getters.isLogin) {
    next('/login');
    return;
  }
  if (to.meta.guest && store.getters.isLogin) {
    next('/portal/dashboard');
    return;
  }
  next();
});

export default router;
