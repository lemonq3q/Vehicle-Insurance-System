export default {
  data: () => ({ toastMessage: '', toastType: 'success', toastTimer: null }),
  beforeUnmount() { clearTimeout(this.toastTimer); },
  methods: {
    notify(message, type = 'success') { this.toastMessage = message; this.toastType = type; clearTimeout(this.toastTimer); this.toastTimer = setTimeout(() => { this.toastMessage = ''; }, 3200); },
    errorMessage(error) { this.notify(error?.message || '操作失败，请稍后重试', 'error'); }
  }
};
