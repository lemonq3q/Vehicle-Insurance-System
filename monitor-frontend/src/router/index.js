import { createRouter, createWebHistory } from "vue-router";
import {
  authState,
  clearSession,
  isAuthenticated,
  saveSession,
} from "@/auth/session";
import { authApi } from "@/api/monitor";

const routes = [
  {
    path: "/login",
    name: "login",
    component: () => import("@/views/LoginPage.vue"),
    meta: { guest: true, title: "登录" },
  },
  {
    path: "/",
    component: () => import("@/layout/MonitorLayout.vue"),
    redirect: "/dashboard",
    meta: { requiresAuth: true },
    children: [
      {
        path: "dashboard",
        component: () => import("@/views/DashboardPage.vue"),
        meta: { title: "仪表盘" },
      },
      {
        path: "reminders",
        component: () => import("@/views/reminder/ReminderListPage.vue"),
        meta: { title: "提醒处理" },
      },
      {
        path: "enterprises",
        component: () => import("@/views/enterprise/EnterpriseListPage.vue"),
        meta: { title: "企业列表" },
      },
      {
        path: "enterprises/compare",
        component: () => import("@/views/enterprise/EnterpriseComparePage.vue"),
        meta: { title: "企业用量对比" },
      },
      {
        path: "enterprises/:id/overview",
        component: () => import("@/views/enterprise/EnterpriseDetailPage.vue"),
        meta: { title: "企业详情" },
      },
      {
        path: "enterprises/:id/members",
        component: () => import("@/views/enterprise/EnterpriseMembersPage.vue"),
        meta: { title: "企业成员" },
      },
      {
        path: "enterprises/:id/finance",
        component: () => import("@/views/enterprise/EnterpriseFinancePage.vue"),
        meta: { title: "企业财务" },
      },
      {
        path: "plans",
        component: () => import("@/views/plan/PlanListPage.vue"),
        meta: { title: "套餐管理" },
      },
      {
        path: "promotion/targets",
        component: () => import("@/views/promotion/PromotionTargetPage.vue"),
        meta: { title: "推广信息录入" },
      },
      {
        path: "visitor-leads",
        component: () => import("@/views/visitor/VisitorLeadPage.vue"),
        meta: { title: "游客信息" },
      },
      {
        path: "promotion/send",
        component: () => import("@/views/promotion/PromotionSendPage.vue"),
        meta: { title: "信息推广" },
      },
      {
        path: "plans/:id/edit",
        component: () => import("@/views/plan/PlanEditPage.vue"),
        meta: { title: "编辑套餐" },
      },
      {
        path: "platform-users",
        component: () => import("@/views/user/UserListPage.vue"),
        meta: { title: "用户管理", roles: ["ADMIN"] },
      },
      {
        path: "system-logs",
        component: () => import("@/views/system/SystemLogPage.vue"),
        meta: { title: "审计日志", roles: ["ADMIN"] },
      },
      {
        path: "profile",
        component: () => import("@/views/ProfilePage.vue"),
        meta: { title: "个人中心" },
      },
      {
        path: "platform-users/:id",
        component: () => import("@/views/user/UserDetailPage.vue"),
        meta: { title: "用户详情", roles: ["ADMIN"] },
      },
    ],
  },
];

/**
 * 监控后台使用统一布局及路由级懒加载，并在页面切换时回到内容顶部。
 */
const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
});
/**
 * 根据本地监控角色执行前端路由保护；带 roles 元数据的管理员页面对 VIEWER 重定向到仪表盘。
 * 后端仍需执行最终权限校验，前端守卫只负责避免暴露不可操作页面。
 */
router.beforeEach(async (to) => {
  document.title = `${to.meta.title || "运营监控"} - iDatag 运营监控中心`;
  if (to.meta.guest && isAuthenticated()) return "/dashboard";
  if (
    to.matched.some((record) => record.meta.requiresAuth) &&
    !isAuthenticated()
  ) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }
  /*
   * 浏览器刷新后本地可能只有 token。首次进入受保护页面时向后端确认 Redis 会话并恢复用户资料；
   * 验证失败会清理残留令牌并回到登录页，不能仅凭可伪造的 localStorage 角色放行。
   */
  if (
    to.matched.some((record) => record.meta.requiresAuth) &&
    !authState.user
  ) {
    try {
      const user = await authApi.me();
      saveSession(authState.token, user);
    } catch {
      clearSession();
      return { path: "/login", query: { redirect: to.fullPath } };
    }
  }
  const role = authState.user?.roleCode;
  if (to.meta.roles && !to.meta.roles.includes(role)) return "/dashboard";
  return true;
});
export default router;
