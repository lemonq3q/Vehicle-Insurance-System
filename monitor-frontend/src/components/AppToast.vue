<template><teleport to="body"><transition name="toast"><div v-if="message" class="app-toast" :class="type" role="status">{{message}}</div></transition></teleport></template>
<script>
/**
 * 轻量页面通知组件根据消息内容控制显隐，并通过 type 切换成功或错误视觉状态。
 */
export default {
  name: 'AppToast',
  props: { message: String, type: { type: String, default: 'success' } }
};
</script>
