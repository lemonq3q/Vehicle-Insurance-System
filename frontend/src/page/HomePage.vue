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
      <main ref="mainViewport" class="right">
        <router-view v-slot="{ Component, route: viewRoute }">
          <keep-alive :include="cachedWorkorderComponents" :max="4">
            <component :is="Component" :key="viewRoute.path" />
          </keep-alive>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import HeaderComponent from '@/components/HeaderComponent.vue';
import MenuComponent from '@/components/MenuComponent.vue';
import { ref, provide, watch, nextTick } from 'vue';
import { useRoute, onBeforeRouteUpdate } from 'vue-router';
import { WORKORDER_LIST_COMPONENTS, WORKORDER_NAVIGATION_KEY, captureWorkorderScroll, restoreWorkorderScroll } from '@/utils/workorderNavigation';

const sidebarCollapsed = ref(false);
const mainViewport = ref(null);
const route = useRoute();
const cachedWorkorderComponents = Object.values(WORKORDER_LIST_COMPONENTS);
const scrollPositions = new Map();
const revision = ref(0);

/**
 * 在路由内容完成切换或工单列表刷新后恢复当前视窗；异步等待后再次校验路径，
 * 防止快速连续导航时旧页面的恢复任务滚动新页面。详情始终从顶部开始，不沿用列表位置。
 */
const restoreScroll = async (path = route.path) => {
  await nextTick();
  if (route.path !== path) return;
  restoreWorkorderScroll(mainViewport.value, scrollPositions.get(path));
};

/*
 * 父工作台复用期间，在子路由替换DOM之前记录列表视窗；缓存中保留筛选、分页、选项和表格实例。
 * 快照只属于当前HomePage，退出登录/离开工作台会随组件销毁，不跨账号保存客户列表。
 */
onBeforeRouteUpdate((to, from) => {
  if (WORKORDER_LIST_COMPONENTS[from.path]) {
    scrollPositions.set(from.path, captureWorkorderScroll(mainViewport.value));
  }
});
watch(() => route.fullPath, () => restoreScroll(), { flush: 'post' });
provide(WORKORDER_NAVIGATION_KEY, { revision, restoreScroll, currentPath: () => route.path });

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
