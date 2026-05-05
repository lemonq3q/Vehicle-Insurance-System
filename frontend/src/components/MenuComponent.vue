<template>
  <div class="menu_container">
    <el-menu
      :default-active="activeMenu"
      class="menu"
      :default-openeds="['5','7']"
      router
    > 
      <router-link to="/home/allWorkorder">
        <el-menu-item index="/home/allWorkorder" v-if="isHasPerm('workorder:select')">
          <el-icon><List /></el-icon>
          <span>工单管理</span>
        </el-menu-item>
      </router-link>

      <router-link to="/home/renewWorkorder">
        <el-menu-item index="/home/renewWorkorder" v-if="isHasPerm('workorder:select')">
          <el-icon><Bell /></el-icon>
          <span>工单续保</span>
        </el-menu-item>
      </router-link>

      <el-sub-menu index="5" v-if="isHasPerm('all')">
        <template #title>
          <el-icon><Location /></el-icon>
          <span>上下游管理</span>
        </template>
        <router-link to="/home/upstream">
          <el-menu-item index="/home/upstream">上游管理</el-menu-item>
        </router-link>
        <router-link to="/home/downstreamMerchant">
          <el-menu-item index="/home/downstreamMerchant">下游管理</el-menu-item>
        </router-link>
        <router-link to="/home/downstreamUser">
          <el-menu-item index="/home/downstreamUser">商户管理</el-menu-item>
        </router-link>
      </el-sub-menu>
      
      <el-sub-menu index="7" v-if="isHasPerm('all')">
        <template #title>
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </template>
        <router-link to="/home/userManagement" v-if="isHasPerm('user:update')">
          <el-menu-item index="/home/userManagement">用户管理</el-menu-item>
        </router-link>
        <router-link to="/home/userApproval" v-if="isHasPerm('user:update')">
          <el-menu-item index="/home/userApproval">用户审批</el-menu-item>
        </router-link>
      </el-sub-menu>

      <router-link to="/home/personalCenter">
        <el-menu-item index="/home/personalCenter">
          <el-icon><HomeFilled /></el-icon>
          <span>个人中心</span>
        </el-menu-item>
      </router-link>


    </el-menu>
  </div>
</template>

<script setup>
import { isHasPerm } from '@/utils/authenticate';
import { onMounted, watch, ref } from 'vue';
import { useRoute } from 'vue-router';
import { 
  List, User, Location, HomeFilled, Bell
} from '@element-plus/icons-vue';

// 获取当前路由实例
const route = useRoute();
// 定义响应式的激活菜单索引
const activeMenu = ref('');

// 初始化/更新激活菜单
const updateActiveMenu = () => {
  // 将当前路由路径赋值给activeMenu（核心：路由路径和menu-item的index保持一致）
  activeMenu.value = route.path;
};

// 页面挂载时初始化
onMounted(() => {
  updateActiveMenu();
});

// 监听路由变化，实时更新激活状态
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
  height: 100%;
  overflow: auto
}
.menu {
  height: 100%;
}
</style>
