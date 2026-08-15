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
  /**
   * 保存当前可见通知、全局订阅取消函数及每条通知的自动关闭计时器，便于逐条清理资源。
   */
  data() {
    return {
      notifications: [],
      unsubscribe: null,
      timers: new Map()
    };
  },
  /**
   * 订阅应用级通知流；新通知进入队列后启动独立的自动移除计时器，使接口层和页面层可共享提示出口。
   */
  created() {
    this.unsubscribe = subscribeNotifications(notification => {
      this.notifications.push(notification);
      const timer = window.setTimeout(() => this.remove(notification.id), 4200);
      this.timers.set(notification.id, timer);
    });
  },
  /**
   * 组件卸载时取消全局订阅并清除所有未触发的计时器，避免路由切换后继续修改已销毁组件。
   */
  beforeUnmount() {
    if (this.unsubscribe) this.unsubscribe();
    this.timers.forEach(timer => window.clearTimeout(timer));
    this.timers.clear();
  },
  methods: {
    /**
     * 将通知级别映射到 layui 图标；普通成功信息使用确认图标，错误和警告显示各自的风险标识。
     */
    iconClass(type) {
      return type === 'error'
        ? 'layui-icon-close-fill'
        : type === 'warning'
          ? 'layui-icon-tips-fill'
          : 'layui-icon-ok-circle';
    },
    /**
     * 主动或定时关闭通知时同步取消计时器、移除计时器索引并更新可见队列，保证重复关闭是安全的。
     */
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
