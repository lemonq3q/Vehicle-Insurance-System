<template>
  <div class="notification-center" aria-live="polite" aria-atomic="false">
    <TransitionGroup name="notification-list">
      <div
        v-for="item in notifications"
        :key="item.id"
        class="notification-item"
        :class="`notification-item--${item.type}`"
        :role="item.type === 'error' ? 'alert' : 'status'"
      >
        <i class="layui-icon" :class="iconClass(item.type)" aria-hidden="true"></i>
        <span>{{ item.message }}</span>
        <button type="button" aria-label="关闭提示" @click="remove(item.id)">
          <i class="layui-icon layui-icon-close" aria-hidden="true"></i>
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<script>
import { subscribeNotifications } from '@/utils/notification';

export default {
  name: 'NotificationCenter',
  data() {
    return {
      notifications: [],
      unsubscribe: null,
      timers: new Map()
    };
  },
  created() {
    this.unsubscribe = subscribeNotifications(notification => {
      this.notifications.push(notification);
      const timer = window.setTimeout(() => this.remove(notification.id), 4200);
      this.timers.set(notification.id, timer);
    });
  },
  beforeUnmount() {
    if (this.unsubscribe) this.unsubscribe();
    this.timers.forEach(timer => window.clearTimeout(timer));
    this.timers.clear();
  },
  methods: {
    iconClass(type) {
      return type === 'error'
        ? 'layui-icon-close-fill'
        : type === 'warning'
          ? 'layui-icon-tips-fill'
          : 'layui-icon-ok-circle';
    },
    remove(id) {
      const timer = this.timers.get(id);
      if (timer) window.clearTimeout(timer);
      this.timers.delete(id);
      this.notifications = this.notifications.filter(item => item.id !== id);
    }
  }
};
</script>

<style scoped>
.notification-center {
  position: fixed;
  z-index: 3000;
  top: 20px;
  left: 50%;
  display: grid;
  width: min(420px, calc(100vw - 32px));
  gap: 10px;
  pointer-events: none;
  transform: translateX(-50%);
}

.notification-item {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) 28px;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 10px 10px 10px 14px;
  border: 1px solid;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 36px rgb(15 23 42 / 16%);
  pointer-events: auto;
}

.notification-item > .layui-icon {
  font-size: 19px;
}

.notification-item span {
  min-width: 0;
  overflow-wrap: anywhere;
  color: #334155;
  line-height: 1.5;
}

.notification-item button {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  place-items: center;
  color: #64748b;
  background: transparent;
}

.notification-item--warning {
  border-color: #f6c96b;
  background: #fffbeb;
}

.notification-item--warning > .layui-icon {
  color: #d97706;
}

.notification-item--error {
  border-color: #f5a5a5;
  background: #fef2f2;
}

.notification-item--error > .layui-icon {
  color: #dc2626;
}

.notification-item--success {
  border-color: #86efac;
  background: #f0fdf4;
}

.notification-item--success > .layui-icon {
  color: #16a34a;
}

.notification-list-enter-active,
.notification-list-leave-active {
  transition: opacity 160ms ease, transform 160ms ease;
}

.notification-list-enter-from,
.notification-list-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
