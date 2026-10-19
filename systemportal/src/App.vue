<template>
  <router-view />
  <NotificationCenter />
  <SubmitLoading />
</template>

<script>
import NotificationCenter from '@/components/NotificationCenter.vue';
import SubmitLoading from '@/components/SubmitLoading.vue';

/**
 * SaaS 门户根组件同时承载当前路由页面和全局通知中心，使接口错误及业务提示不依赖具体页面生命周期。
 */
export default {
  name: 'App',
  components: { NotificationCenter, SubmitLoading }
};
</script>
