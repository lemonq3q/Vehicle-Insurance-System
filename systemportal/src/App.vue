<template>
  <router-view />
  <NotificationCenter />
</template>

<script>
import NotificationCenter from '@/components/NotificationCenter.vue';

/**
 * SaaS 门户根组件同时承载当前路由页面和全局通知中心，使接口错误及业务提示不依赖具体页面生命周期。
 */
export default {
  name: 'App',
  components: { NotificationCenter }
};
</script>
