<template>
  <div v-if="!state.ready || state.error" class="portal-card data-load-feedback" :aria-busy="state.pending">
    <div v-if="state.pending" class="portal-loading-indicator" role="status" aria-live="polite">
      <span class="portal-loading-ring" aria-hidden="true"></span><span>正在加载，请稍候…</span>
    </div>
    <div v-else-if="state.error" class="data-load-error" role="alert">
      <span>{{ state.error }}</span><button type="button" class="layui-btn portal-btn portal-btn-primary" @click="$emit('retry')">重新加载</button>
    </div>
  </div>
</template>
<script>
/**
 * 在首次数据尚未确认时提供有边框的稳定占位，失败时提供独立重试入口。
 * 不从字段默认值推断业务状态，既能用于整页分支门控，也能用于局部表格和资料卡片。
 */
export default {
  name: 'DataLoadFeedback',
  props: { state: { type: Object, required: true } },
  emits: ['retry']
};
</script>
<style scoped>
.data-load-feedback{min-height:280px;display:grid;place-items:center;padding:24px}
.data-load-feedback .portal-loading-indicator{position:static}
.data-load-error{display:flex;flex-direction:column;align-items:center;gap:18px;color:var(--portal-muted);text-align:center}
</style>
