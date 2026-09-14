<template>
  <div class="home" :class="{ collapsed: sidebarCollapsed }">
    <aside class="left" aria-label="车险系统侧边栏">
      <div class="brand">
        <span class="brand-mark"><img src="@/assets/brand/idatag-monitor-logo.png" alt="" /></span>
        <span class="brand-copy"><strong>iDatag</strong><small>车险管理系统</small></span>
      </div>
      <MenuComponent :collapsed="sidebarCollapsed"></MenuComponent>
    </aside>
    <div class="workspace">
      <div class="top">
        <HeaderComponent :sidebar-collapsed="sidebarCollapsed" @toggle-sidebar="toggleSidebar"></HeaderComponent>
      </div>
      <main class="right">
        <router-view></router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import HeaderComponent from '@/components/HeaderComponent.vue';
import MenuComponent from '@/components/MenuComponent.vue';
import { ref } from 'vue';

const sidebarCollapsed = ref(false);

/**
 * 仅切换工作台侧栏的展示宽度。菜单的路由、权限和页面实例保持原样，
 * 使用户收起侧栏时正在处理的工单或表单状态不受影响。
 */
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
};


  
</script>


<style scoped>
.home {
  width: 100%;
  height: 100dvh;
  display: flex;
  color: var(--insurance-text);
  text-align: left;
}
.left {
  width: 248px;
  flex: 0 0 248px;
  height: 100dvh;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 16px 14px;
  overflow: hidden;
  background: var(--insurance-sidebar);
  color: var(--insurance-sidebar-text);
  transition: width 180ms ease, flex-basis 180ms ease;
}
.collapsed .left { width: 76px; flex-basis: 76px; }
.collapsed .brand { justify-content: center; padding-left: 0; padding-right: 0; }
.collapsed .brand-copy { display: none; }
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 6px 8px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, .08);
}
.brand-mark {
  display: grid;
  place-items: center;
  flex: 0 0 38px;
  height: 38px;
}
.brand-mark img { width: 100%; height: 100%; object-fit: contain; }
.brand-copy { display: flex; flex-direction: column; min-width: 0; }
.brand-copy strong { color: #fff; font-size: 16px; }
.brand-copy small { color: #94a3b8; }
.workspace {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.top {
  flex: 0 0 68px;
  z-index: 10;
  box-shadow: 0 1px 0 var(--insurance-border);
}
.right {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  background-color: var(--insurance-bg);
  padding: 10px;
}
</style>
