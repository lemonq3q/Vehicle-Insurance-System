<template>
  <dialog ref="dialog" class="binding-dialog" aria-labelledby="binding-dialog-title" @cancel.prevent="$emit('close')" @click="handleBackdrop">
    <header class="binding-dialog__header">
      <h2 id="binding-dialog-title">绑定新的登录方式</h2>
      <button type="button" class="binding-dialog__close" aria-label="关闭绑定对话框" autofocus @click="$emit('close')">×</button>
    </header>
    <EmailBindingForm v-if="!emailBound" @bound="$emit('bound', $event)" />
    <WechatLogin v-if="!wechatBound" binding />
    <div v-if="submitting" class="portal-loading-mask" role="status" aria-live="polite">
      <div class="portal-loading-indicator"><span class="portal-loading-ring" aria-hidden="true"></span><span>正在提交，请稍候…</span></div>
    </div>
  </dialog>
</template>

<script>
import EmailBindingForm from '@/components/EmailBindingForm.vue';
import WechatLogin from '@/components/WechatLogin.vue';
import { loadingState } from '@/utils/loading';

/**
 * 个人中心首次绑定入口，在原生模态对话框中复用邮箱验证和微信扫码组件。
 * 父页面按缺失登录方式决定内容；原生 dialog 限制键盘焦点，关闭时销毁二维码和计时器。
 */
export default {
  name: 'LoginMethodDialog',
  components: { EmailBindingForm, WechatLogin },
  props: { emailBound: { type: Boolean, default: false }, wechatBound: { type: Boolean, default: false } },
  emits: ['close', 'bound'],
  computed: {
    /**
     * 已打开的原生对话框处于浏览器顶层，重要绑定提交在其有边框的容器内反馈。
     * 不再为绑定额外打开覆盖整个应用的模态遮罩，也不改变原有对话框生命周期。
     */
    submitting() { return loadingState.fullscreen > 0; }
  },
  /** 保存页面原滚动状态和触发按钮，避免关闭后丢失键盘操作位置。 */
  data() { return { previousOverflow: '', returnFocus: null }; },
  /** 挂载后打开顶层模态框并暂停背景滚动，焦点由原生 dialog 管理。 */
  mounted() {
    this.returnFocus = document.activeElement;
    this.previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    this.$refs.dialog.showModal();
  },
  /** 退出或路由跳转时恢复页面滚动和原触发位置，不保留正在展示的二维码。 */
  beforeUnmount() {
    this.$refs.dialog.close();
    document.body.style.overflow = this.previousOverflow;
    if (this.returnFocus?.isConnected) this.returnFocus.focus();
  },
  methods: {
    /** 仅点击对话框外的遮罩时关闭；内容、空白内边距和二维码内点击均不触发关闭。 */
    handleBackdrop(event) {
      if (event.target !== this.$refs.dialog) return;
      const bounds = this.$refs.dialog.getBoundingClientRect();
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) this.$emit('close');
    }
  }
};
</script>

<style scoped>
.binding-dialog{box-sizing:border-box;width:min(480px,calc(100vw - 32px));max-height:calc(100dvh - 48px);overflow:auto;border:1px solid var(--portal-border);border-radius:16px;padding:24px;color:var(--portal-text);background:#fff;box-shadow:var(--portal-shadow)}
.binding-dialog::backdrop{background:rgb(15 23 42 / 52%)}
.binding-dialog__header{display:flex;align-items:center;justify-content:space-between;gap:16px;padding-bottom:12px;border-bottom:1px solid var(--portal-border)}
.binding-dialog__header h2{margin:0;font-size:20px}.binding-dialog__close{display:grid;place-items:center;width:44px;height:44px;flex-shrink:0;border:0;border-radius:8px;background:transparent;color:var(--portal-muted);font-size:28px;cursor:pointer}.binding-dialog__close:hover{background:#f1f5f9}.binding-dialog__close:focus-visible{outline:2px solid var(--portal-accent);outline-offset:2px}
</style>
