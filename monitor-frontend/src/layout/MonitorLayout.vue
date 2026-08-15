<template>
  <div class="monitor-app" :class="{ collapsed }">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <aside class="monitor-sidebar">
      <router-link class="brand" to="/dashboard" aria-label="小马e保运营监控中心">
        <span class="brand-mark">XM</span><span class="brand-copy"><strong>小马e保</strong><small>运营监控中心</small></span>
      </router-link>
      <nav aria-label="主导航">
        <template v-for="group in menus" :key="group.title">
          <p class="nav-caption">{{ group.title }}</p>
          <router-link v-for="item in group.items" :key="item.path" :to="item.path" class="nav-item">
            <i :class="item.icon" aria-hidden="true"></i><span>{{ item.label }}</span>
          </router-link>
        </template>
      </nav>
      <div class="sidebar-status"><span class="status-dot"></span><div><strong>系统运行正常</strong><small>统计更新于 10:18</small></div></div>
    </aside>
    <header class="monitor-header">
      <button class="icon-button" type="button" aria-label="切换侧边栏" @click="collapsed = !collapsed"><i class="layui-icon layui-icon-shrink-right"></i></button>
      <div class="breadcrumb"><span>运营监控</span><i class="layui-icon layui-icon-right"></i><strong>{{ $route.meta.title }}</strong></div>
      <div class="header-actions">
        <div class="operator"><span>林</span><div><strong>林嘉诚</strong><small>管理员</small></div><i class="layui-icon layui-icon-down"></i></div>
      </div>
    </header>
    <main id="main-content" class="monitor-main"><router-view /></main>
  </div>
</template>

<script>
export default {
  name: 'MonitorLayout',
  /**
   * 维护侧栏折叠状态和监控后台菜单。平台用户管理菜单仅向 ADMIN 注入，
   * VIEWER 即使手工访问对应路由也会由路由守卫再次拦截。
   */
  data() {
    return {
      collapsed: false,
      menus: [
        { title: '总览', items: [{ label: '仪表盘', path: '/dashboard', icon: 'layui-icon layui-icon-console' }] },
        { title: '客户运营', items: [
          { label: '企业列表', path: '/enterprises', icon: 'layui-icon layui-icon-template-1' },
          { label: '企业用量对比', path: '/enterprises/compare', icon: 'layui-icon layui-icon-chart-screen' }
        ] },
        { title: '产品配置', items: [{ label: '套餐管理', path: '/plans', icon: 'layui-icon layui-icon-rmb' }] },
        ...((localStorage.getItem('monitorRole') || 'ADMIN') === 'ADMIN' ? [{ title: '平台设置', items: [{ label: '用户管理', path: '/platform-users', icon: 'layui-icon layui-icon-user' }] }] : [])
      ]
    };
  },
  computed: {
    /**
     * 从本地会话读取监控角色；开发和 mock 环境没有角色时默认以管理员展示完整功能。
     */
    currentRole() { return localStorage.getItem('monitorRole') || 'ADMIN'; }
  }
};
</script>
