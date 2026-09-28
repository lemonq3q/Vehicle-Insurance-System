<template>
  <div class="menu_container">
    <el-menu
      :default-active="activeMenu"
      class="menu"
      :default-openeds="['5','7']"
      :collapse="collapsed"
      :collapse-transition="false"
      router
    >
      <p v-if="!collapsed" class="nav-caption">业务工作台</p>
      <!--
        菜单自身的 router 模式负责跳转，标题插槽负责折叠时隐藏文字并提供名称提示。
        不额外包裹 router-link，避免破坏折叠菜单的图标布局与交互结构。
      -->
      <el-menu-item index="/home/allWorkorder" v-if="isHasPerm('workorder:select')" aria-label="工单管理">
        <el-icon><List /></el-icon>
        <template #title>工单管理</template>
      </el-menu-item>

      <el-menu-item index="/home/renewWorkorder" v-if="isHasPerm('workorder:select')" aria-label="工单续保">
        <el-icon><Bell /></el-icon>
        <template #title>工单续保</template>
      </el-menu-item>

      <p class="nav-caption" v-if="!collapsed && isHasPerm('all')">渠道管理</p>
      <el-sub-menu index="5" v-if="isHasPerm('all')">
        <template #title>
          <el-icon><Location /></el-icon>
          <span>上下游管理</span>
        </template>
        <el-menu-item index="/home/upstream">上游管理</el-menu-item>
        <el-menu-item index="/home/downstreamMerchant">下游管理</el-menu-item>
        <el-menu-item index="/home/downstreamUser">商户管理</el-menu-item>
      </el-sub-menu>

      <!--
        车险工作台不再展示个人中心导航入口，同时移除仅属于该入口的账户分组标题。
        保留个人中心页面和路由，隐藏菜单不改变原有访问权限或个人资料接口。
      -->


    </el-menu>
  </div>
</template>

<script>
export default {
  props: { collapsed: { type: Boolean, default: false } },
};
</script>

<script setup>
import { isHasPerm } from '@/utils/authenticate';
import { onMounted, watch, ref } from 'vue';
import { useRoute } from 'vue-router';
import {
  List, Location, Bell
} from '@element-plus/icons-vue';

// 获取当前路由实例
const route = useRoute();
// 定义响应式的激活菜单索引
const activeMenu = ref('');

/**

 * * 使用当前路由完整路径更新侧栏激活项；菜单 index 与路由路径保持一致，因此无需额外映射表。

 */
const updateActiveMenu = () => {
  // 将当前路由路径赋值给activeMenu（核心：路由路径和menu-item的index保持一致）
  activeMenu.value = route.path;
};

/**

 * * 菜单挂载时立即同步一次路由，保证刷新子页面后仍高亮正确入口。

 */
onMounted(() => {
  updateActiveMenu();
});

/**

 * * 监听后续路由变化，在详情、编辑和列表之间切换时实时更新侧栏高亮。

 */
watch(
  () => route.path,
  () => {
    updateActiveMenu();
  },
  { immediate: true }
);
</script>

<style scoped>
.menu_container {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  background: var(--insurance-sidebar);
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, .45) transparent;
}
.menu {
  min-height: 0;
  border-right: 0;
  background: transparent;
  --el-menu-bg-color: var(--insurance-sidebar);
  --el-menu-text-color: var(--insurance-sidebar-text);
  --el-menu-hover-bg-color: rgba(255, 255, 255, .07);
  --el-menu-active-color: #fff;
}
.menu_container :deep(.el-menu--collapse) { width: 100%; }
.menu_container :deep(.el-menu--collapse .el-menu-item),
.menu_container :deep(.el-menu--collapse .el-sub-menu__title) {
  display: flex;
  justify-content: center;
  padding: 0 !important;
}
/*
 * 折叠后图标独立居中，清除展开状态为文字预留的右侧间距。
 * 菜单项标题的隐藏和悬浮提示继续由 Element Plus 的标题插槽处理。
 */
.menu_container :deep(.el-menu--collapse .el-icon) {
  margin-right: 0;
  flex-shrink: 0;
}
.menu_container :deep(.el-menu--collapse .el-menu-tooltip__trigger) {
  justify-content: center;
  padding: 0 !important;
}
.nav-caption {
  margin: 22px 12px 7px;
  color: #718096;
  font-size: 12px;
  line-height: 1.5;
  letter-spacing: .08em;
}
.menu_container :deep(.el-menu-item),
.menu_container :deep(.el-sub-menu__title) {
  height: 44px;
  min-height: 44px;
  margin: 3px 0;
  border-radius: 8px;
  color: var(--insurance-sidebar-text);
  font-size: 14px;
  transition: background 160ms ease, color 160ms ease;
}
.menu_container :deep(.el-menu-item .el-icon),
.menu_container :deep(.el-sub-menu__title .el-icon) {
  width: 20px;
  font-size: 18px;
}
.menu_container :deep(.el-menu-item:hover),
.menu_container :deep(.el-sub-menu__title:hover) {
  color: #fff;
}
.menu_container :deep(.el-menu-item.is-active) {
  color: #fff;
  background: var(--insurance-primary);
  box-shadow: 0 8px 20px rgba(30, 64, 175, .26);
}
.menu_container :deep(.el-sub-menu .el-menu-item) {
  min-width: 0;
  padding-left: 36px !important;
}
.menu_container :deep(a:focus-visible),
.menu_container :deep(.el-menu-item:focus-visible),
.menu_container :deep(.el-sub-menu__title:focus-visible) {
  outline: 2px solid #93c5fd;
  outline-offset: -2px;
}
</style>
