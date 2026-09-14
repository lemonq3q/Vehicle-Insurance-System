<template>
  <div class="monitor-app" :class="{ collapsed }">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <aside class="monitor-sidebar">
      <router-link
        class="brand"
        to="/dashboard"
        aria-label="iDatag 运营监控中心"
      >
        <span class="brand-mark"
          ><img src="@/assets/brand/idatag-monitor-logo.png" alt="" /></span
        ><span class="brand-copy"
          ><strong>iDatag</strong><small>运营监控中心</small></span
        >
      </router-link>
      <nav aria-label="主导航">
        <template v-for="group in menus" :key="group.title">
          <p class="nav-caption">{{ group.title }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="nav-item"
          >
            <i :class="item.icon" aria-hidden="true"></i
            ><span>{{ item.label }}</span>
          </router-link>
        </template>
      </nav>
    </aside>
    <header class="monitor-header">
      <button
        class="icon-button"
        type="button"
        aria-label="切换侧边栏"
        @click="collapsed = !collapsed"
      >
        <i class="layui-icon layui-icon-shrink-right"></i>
      </button>
      <div class="breadcrumb">
        <span>运营监控</span><i class="layui-icon layui-icon-right"></i
        ><strong>{{ $route.meta.title }}</strong>
      </div>
      <div class="header-actions">
        <div class="operator">
          <span>{{ initials }}</span>
          <div>
            <strong>{{ currentUser.realName || currentUser.username }}</strong
            ><small>{{ currentUser.roleName || roleLabel }}</small>
          </div>
        </div>
        <button
          class="logout-button"
          type="button"
          :disabled="loggingOut"
          @click="logout"
        >
          <i class="layui-icon layui-icon-logout" aria-hidden="true"></i
          >{{ loggingOut ? "退出中" : "退出登录" }}
        </button>
      </div>
    </header>
    <main id="main-content" class="monitor-main"><router-view /></main>
  </div>
</template>

<script>
import { authApi } from "@/api/monitor";
import { authState, clearSession } from "@/auth/session";

export default {
  name: "MonitorLayout",
  /**
   * 维护侧栏折叠状态和监控后台菜单。平台用户管理菜单仅向 ADMIN 注入，
   * VIEWER 即使手工访问对应路由也会由路由守卫再次拦截。
   */
  data() {
    return {
      collapsed: false,
      loggingOut: false,
    };
  },
  computed: {
    /**
     * 返回认证模块维护的实时用户资料，刷新恢复和退出后布局会同步更新。
     */
    currentUser() {
      return authState.user || {};
    },
    /** 根据姓名或账号首字符生成无图片头像。 */
    initials() {
      return String(
        this.currentUser.realName || this.currentUser.username || "用户"
      ).slice(0, 1);
    },
    /** 将后端角色代码转换为稳定兜底文案。 */
    roleLabel() {
      return this.currentUser.roleCode === "ADMIN" ? "管理员" : "售后客服";
    },
    /**
     * 根据真实登录角色生成导航。管理员额外拥有平台用户管理入口，客服不会在前端看到该菜单；
     * 账户分组在权限菜单组装完成后最后追加，只保证它是导航列表的最后一项，不再固定到整个屏幕底部。
     */
    menus() {
      const menus = [
        {
          title: "总览",
          items: [
            {
              label: "仪表盘",
              path: "/dashboard",
              icon: "layui-icon layui-icon-console",
            },
            {
              label: "提醒处理",
              path: "/reminders",
              icon: "layui-icon layui-icon-notice",
            },
          ],
        },
        {
          title: "客户运营",
          items: [
            {
              label: "企业列表",
              path: "/enterprises",
              icon: "layui-icon layui-icon-template-1",
            },
            {
              label: "企业用量对比",
              path: "/enterprises/compare",
              icon: "layui-icon layui-icon-chart-screen",
            },
          ],
        },
        {
          title: "销售推广",
          items: [
            {
              label: "游客信息",
              path: "/visitor-leads",
              icon: "layui-icon layui-icon-survey",
            },
            {
              label: "信息录入",
              path: "/promotion/targets",
              icon: "layui-icon layui-icon-form",
            },
            {
              label: "信息推广",
              path: "/promotion/send",
              icon: "layui-icon layui-icon-release",
            },
          ],
        },
        {
          title: "产品配置",
          items: [
            {
              label: "套餐管理",
              path: "/plans",
              icon: "layui-icon layui-icon-rmb",
            },
          ],
        },
      ];
      if (this.currentUser.roleCode === "ADMIN")
        menus.push({
          title: "平台设置",
          items: [
            {
              label: "用户管理",
              path: "/platform-users",
              icon: "layui-icon layui-icon-user",
            },
            {
              label: "审计日志",
              path: "/system-logs",
              icon: "layui-icon layui-icon-log",
            },
          ],
        });
      menus.push({
        title: "账户",
        items: [
          {
            label: "个人中心",
            path: "/profile",
            icon: "layui-icon layui-icon-username",
          },
        ],
      });
      return menus;
    },
  },
  methods: {
    /**
     * 主动请求服务端删除 Redis 会话，并在成功或网络失败时都清理本地 token。
     * finally 清理确保用户点击退出后不会因为后端暂时不可达而继续暴露后台页面。
     */
    async logout() {
      if (this.loggingOut) return;
      this.loggingOut = true;
      try {
        await authApi.logout();
      } catch {
        // 本地会话仍必须清除；后端 JWT 或网络异常由下一次访问时的鉴权继续兜底。
      } finally {
        clearSession();
        this.loggingOut = false;
        await this.$router.replace("/login");
      }
    },
  },
};
</script>
