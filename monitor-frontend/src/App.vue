<template><router-view /><AppToast :message="notification?.message" :type="notification?.type || 'success'" /></template>
<script>
import AppToast from '@/components/AppToast.vue';
import { subscribeNotifications } from '@/utils/notification';

/**
 * 根组件承载请求层唯一的全局通知出口，跨路由保持可见，成功通知仍由业务页面自行决定。
 * 订阅及关闭计时器随根组件生命周期清理，页面已有局部错误文本不再次触发全局弹窗。
 */
export default {
  name: 'App',
  components: { AppToast },
  data: () => ({ notification: null, unsubscribe: null, notificationTimer: null }),
  mounted() {
    this.unsubscribe = subscribeNotifications(notification => {
      this.notification = notification;
      clearTimeout(this.notificationTimer);
      this.notificationTimer = setTimeout(() => { this.notification = null; }, 3200);
    });
  },
  beforeUnmount() {
    this.unsubscribe?.();
    clearTimeout(this.notificationTimer);
  }
};
</script>
