<template>
  <Teleport to="body">
    <dialog ref="mask" class="portal-submit-loading" aria-label="正在提交，请稍候" @cancel.prevent>
      <div class="portal-loading-indicator" role="status" aria-live="polite">
        <span class="portal-loading-ring" aria-hidden="true"></span>
        <span>正在提交，请稍候…</span>
      </div>
    </dialog>
  </Teleport>
</template>

<script>
import { loadingState } from '@/utils/loading';

/**
 * 仅访客页的重要提交使用全屏白色遮罩；门户后台统一交由布局的 main 遮罩，保留导航操作。
 * 浏览器负责限制背景焦点并在结束后恢复，Escape 不会打断已发出的提交请求。
 */
export default {
  name: 'SubmitLoading',
  /** 保存防闪烁定时器；任务数由公共加载管理器维护，不依赖页面组件生命周期。 */
  data() { return { timer: null }; },
  computed: {
    /** 仅访客页的重要提交锁定全页，后台提交和普通数据读取均不会触发此模态层。 */
    busy() { return loadingState.fullscreen > 0 && !this.$route.path.startsWith('/portal'); }
  },
  watch: {
    /** 提交开始、失败和结束均通过相同入口管理弹层，确保并发提交全部结束后才关闭。 */
    busy(value) { this.syncVisibility(value); }
  },
  /** 处理应用挂载前已经启动的提交，保持首次认证回调与后续表单行为一致。 */
  mounted() { this.syncVisibility(this.busy); },
  /** 应用销毁时取消延迟显示并关闭模态层，避免遗留浏览器顶层遮罩。 */
  beforeUnmount() { window.clearTimeout(this.timer); this.$refs.mask.close(); },
  methods: {
    /**
     * 快速完成的提交不闪现遮罩，超过 160ms 后以模态方式呈现；结束时立即恢复页面操作。
     * @param {boolean} busy 当前是否有重要提交任务。
     */
    syncVisibility(busy) {
      window.clearTimeout(this.timer);
      if (!busy) { this.$refs.mask?.close(); return; }
      this.timer = window.setTimeout(() => {
        if (this.busy && !this.$refs.mask.open) this.$refs.mask.showModal();
      }, 160);
    }
  }
};
</script>
