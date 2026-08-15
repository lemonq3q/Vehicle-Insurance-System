export default {
  /**
   * 为使用该 mixin 的页面提供一组独立 Toast 状态和自动关闭计时器。
   */
  data: () => ({ toastMessage: '', toastType: 'success', toastTimer: null }),
  /**
   * 页面卸载时清理尚未触发的通知计时器，避免销毁后继续写入组件状态。
   */
  beforeUnmount() { clearTimeout(this.toastTimer); },
  methods: {
    /**
     * 显示指定类型的页面通知，并重置自动关闭时间，保证连续操作时最新提示拥有完整阅读时间。
     */
    notify(message, type = 'success') {
      this.toastMessage = message;
      this.toastType = type;
      clearTimeout(this.toastTimer);
      this.toastTimer = setTimeout(() => { this.toastMessage = ''; }, 3200);
    },
    /**
     * 从接口异常中提取可读信息并按错误样式展示，没有具体信息时使用统一兜底文案。
     */
    errorMessage(error) { this.notify(error?.message || '操作失败，请稍后重试', 'error'); }
  }
};
