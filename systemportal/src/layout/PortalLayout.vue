<template>
  <div class="portal-app" :class="{ collapsed: collapsed }">
    <aside class="portal-sidebar">
      <router-link class="sidebar-brand" to="/portal/dashboard">
        <span>XM</span>
        <strong>小马e保</strong>
      </router-link>
      <nav class="sidebar-nav" aria-label="门户导航">
        <template v-for="group in menus" :key="group.title">
          <div class="nav-group-title">{{ group.title }}</div>
          <router-link v-for="item in group.children" :key="item.path" :to="item.path" class="nav-link">
            <i :class="item.icon"></i>
            <span>{{ item.label }}</span>
          </router-link>
        </template>
      </nav>
    </aside>

    <header class="portal-header">
      <button class="header-icon" type="button" aria-label="折叠侧边栏" @click="toggleSidebar">
        <i class="layui-icon layui-icon-shrink-right"></i>
      </button>
      <div>
        <strong>{{ pageTitle }}</strong>
        <span>{{ enterpriseName }}</span>
      </div>
      <div class="header-actions">
        <button
          class="layui-btn portal-btn portal-btn-primary enter-system-button"
          type="button"
          :disabled="enteringSystem || !$store.state.currentMember"
          @click="enterInsuranceSystem"
        >
          <i class="layui-icon layui-icon-release"></i>
          <span>{{ enteringSystem ? '正在进入...' : '进入车险系统' }}</span>
        </button>
        <button
          class="layui-btn layui-btn-primary layui-border-green portal-btn test-role-button"
          type="button"
          :disabled="!$store.state.currentMember"
          :title="`当前测试角色：${currentRoleName}，点击切换`"
          :aria-label="`切换测试角色，当前为${currentRoleName}`"
          @click="switchTestRole"
        >
          <i class="layui-icon layui-icon-transfer"></i>
          <span>角色：{{ currentRoleName }}</span>
        </button>
        <router-link class="layui-btn layui-btn-primary layui-border-green portal-btn" to="/">官网</router-link>
        <button class="layui-btn layui-btn-primary layui-border-red portal-btn" type="button" @click="logoutDialogVisible = true">退出</button>
      </div>
    </header>

    <main class="portal-main">
      <router-view />
    </main>

    <ConfirmDialog
      :visible="logoutDialogVisible"
      title="退出登录"
      message="确定退出当前账号吗？退出后需要重新登录才能进入门户后台。"
      confirm-text="确认退出"
      @cancel="logoutDialogVisible = false"
      @confirm="confirmLogout"
    />
  </div>
</template>

<script>
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import { createInsuranceAuthorization } from '@/api/portal';

export default {
  name: 'PortalLayout',
  components: { ConfirmDialog },
  data() {
    return {
      logoutDialogVisible: false,
      enteringSystem: false,
      menus: [
        {
          title: '工作台',
          children: [
            { label: '仪表盘', path: '/portal/dashboard', icon: 'layui-icon layui-icon-console' }
          ]
        },
        {
          title: '企业管理',
          children: [
            { label: '企业信息', path: '/portal/enterprise/info', icon: 'layui-icon layui-icon-template' },
            { label: '企业人员', path: '/portal/enterprise/members', icon: 'layui-icon layui-icon-user' }
          ]
        },
        {
          title: '财务中心',
          children: [
            { label: '订阅服务', path: '/portal/finance/subscription', icon: 'layui-icon layui-icon-rmb' },
            { label: '充值订单', path: '/portal/finance/recharges', icon: 'layui-icon layui-icon-form' },
            { label: '订阅订单', path: '/portal/finance/orders', icon: 'layui-icon layui-icon-list' },
            { label: '资金明细', path: '/portal/finance/transactions', icon: 'layui-icon layui-icon-chart' }
          ]
        },
        {
          title: '服务',
          children: [
            { label: '用户中心', path: '/portal/user/profile', icon: 'layui-icon layui-icon-username' },
            { label: '帮助中心', path: '/portal/help', icon: 'layui-icon layui-icon-service' }
          ]
        }
      ]
    };
  },
  computed: {
    collapsed() {
      return this.$store.state.sidebarCollapsed;
    },
    pageTitle() {
      return this.$route.meta.title || '门户后台';
    },
    enterpriseName() {
      return this.$store.state.currentEnterprise?.name || '暂未加入企业';
    },
    currentRoleName() {
      return { OWNER: '拥有者', ADMIN: '管理员', ISSUER: '出单员' }[this.$store.getters.roleCode] || '无';
    }
  },
  async created() {
    if (!this.$store.state.contextLoaded) {
      await this.$store.dispatch('loadContext');
    }
  },
  methods: {
    async enterInsuranceSystem() {
      if (this.enteringSystem) return;
      this.enteringSystem = true;
      try {
        const response = await createInsuranceAuthorization();
        window.location.assign(response.data.redirectUrl);
      } catch {
        this.enteringSystem = false;
        // Request errors are displayed by the Axios interceptor.
      }
    },
    toggleSidebar() {
      this.$store.commit('setSidebarCollapsed', !this.collapsed);
    },
    switchTestRole() {
      const roles = ['OWNER', 'ADMIN', 'ISSUER'];
      const currentIndex = roles.indexOf(this.$store.getters.roleCode);
      const nextRole = roles[(currentIndex + 1) % roles.length];
      this.$store.commit('setTestRoleCode', nextRole);
    },
    confirmLogout() {
      this.logoutDialogVisible = false;
      this.$store.dispatch('logout');
      this.$router.push('/login');
    }
  }
};
</script>

<style scoped>
.portal-app {
  min-height: 100dvh;
  background: var(--portal-bg);
}

.portal-sidebar {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 30;
  width: 248px;
  overflow-y: auto;
  padding: 18px 14px;
  background: #0f172a;
  color: #e2e8f0;
  transition: width 180ms ease;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 48px;
  padding: 0 10px;
  margin-bottom: 18px;
}

.sidebar-brand span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #22c55e;
  color: #052e16;
  font-weight: 800;
}

.nav-group-title {
  padding: 18px 12px 8px;
  color: #94a3b8;
  font-size: 12px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 0 12px;
  border-radius: 8px;
  color: #dbeafe;
  transition: background 160ms ease, color 160ms ease;
}

.nav-link i {
  width: 20px;
  text-align: center;
}

.nav-link:hover,
.nav-link.router-link-active {
  background: rgba(34, 197, 94, 0.14);
  color: #bbf7d0;
}

.portal-header {
  position: fixed;
  top: 0;
  right: 0;
  left: 248px;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 16px;
  height: 68px;
  padding: 0 24px;
  border-bottom: 1px solid var(--portal-border);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(14px);
  transition: left 180ms ease;
}

.portal-header strong {
  display: block;
  font-size: 18px;
}

.portal-header span {
  color: var(--portal-muted);
  font-size: 13px;
}

.header-icon {
  width: 42px;
  height: 42px;
  border: 1px solid var(--portal-border);
  border-radius: 8px;
  background: #fff;
}

.header-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
}

.header-actions .layui-btn {
  margin: 0;
}

.test-role-button {
  min-width: 112px;
}

.enter-system-button i {
  margin-right: 5px;
}

.enter-system-button span {
  display: inline;
  color: inherit;
  font-size: inherit;
}

.test-role-button i {
  margin-right: 5px;
}

.test-role-button span {
  display: inline;
  color: inherit;
  font-size: inherit;
}

.portal-main {
  position: fixed;
  top: 68px;
  right: 0;
  bottom: 0;
  left: 248px;
  overflow-y: auto;
  padding: 24px;
  transition: left 180ms ease;
}

.portal-app.collapsed .portal-sidebar {
  width: 76px;
}

.portal-app.collapsed .sidebar-brand strong,
.portal-app.collapsed .nav-group-title,
.portal-app.collapsed .nav-link span {
  display: none;
}

.portal-app.collapsed .portal-header,
.portal-app.collapsed .portal-main {
  left: 76px;
}

@media (max-width: 840px) {
  .portal-sidebar {
    width: 76px;
  }

  .sidebar-brand strong,
  .nav-group-title,
  .nav-link span {
    display: none;
  }

  .portal-header,
  .portal-main,
  .portal-app.collapsed .portal-header,
  .portal-app.collapsed .portal-main {
    left: 76px;
  }

  .portal-main {
    padding: 16px;
  }
}

@media (max-width: 620px) {
  .header-actions a {
    display: none;
  }

  .test-role-button {
    min-width: 42px;
    width: 42px;
    padding: 0;
  }

  .test-role-button i {
    margin: 0;
  }

  .test-role-button span {
    display: none;
  }

  .enter-system-button span {
    display: none;
  }

  .enter-system-button {
    width: 42px;
    padding: 0;
  }

  .enter-system-button i {
    margin: 0;
  }
}
</style>
