<template>
  <router-view></router-view>
</template>

<script setup>

/**
 * 创建可取消的 ResizeObserver 防抖回调。普通防抖在组件卸载后仍可能执行最后一个定时任务，导致
 * Element Plus 使用已经销毁的 Select 节点测量宽度；显式暴露 cancel 后，观察器生命周期结束时可以
 * 同步清除该任务。
 */
const createCancellableDebounce = (fn, delay) => {
  let timer = null;
  const invoke = (...args) => {
    if (timer !== null) clearTimeout(timer);
    timer = setTimeout(() => {
      timer = null;
      fn(...args);
    }, delay);
  };
  invoke.cancel = () => {
    if (timer !== null) clearTimeout(timer);
    timer = null;
  };
  return invoke;
};

/**
 * 用具备生命周期保护的防抖观察器包装浏览器原生 ResizeObserver。回调执行前确认观察器仍有效且至少
 * 一个目标仍挂载在文档中；组件断开观察时取消未执行任务，避免 Select 卸载后继续调用
 * getComputedStyle，同时继续合并表格的高频尺寸变化。
 */
const _ResizeObserver = window.ResizeObserver;
if (_ResizeObserver) {
window.ResizeObserver = class SafeDebouncedResizeObserver extends _ResizeObserver {
  #targets = new Set();
  #debouncedCallback;

  /**
   * 保留原生构造契约，并在延迟回调触发前检查观察目标是否仍是文档中的 Element。
   */
  constructor(callback) {
    let safeCallback;
    super((entries, observer) => safeCallback(entries, observer));
    this.#debouncedCallback = createCancellableDebounce((entries, observer) => {
      const hasMountedTarget = Array.from(this.#targets).some(
        target => target instanceof Element && target.isConnected
      );
      if (hasMountedTarget) callback(entries, observer);
    }, 20);
    safeCallback = this.#debouncedCallback;
  }

  /** 记录有效观察目标；原观察器在 disconnect 后重新 observe 时仍可复用。 */
  observe(target, options) {
    if (!(target instanceof Element)) return;
    this.#targets.add(target);
    super.observe(target, options);
  }

  /** 移除单个目标，最后一个目标解除观察时同步取消尚未执行的延迟回调。 */
  unobserve(target) {
    this.#targets.delete(target);
    super.unobserve(target);
    if (this.#targets.size === 0) this.#debouncedCallback.cancel();
  }

  /** 组件卸载时清除全部目标和延迟任务，再交由原生观察器释放浏览器资源。 */
  disconnect() {
    this.#targets.clear();
    this.#debouncedCallback.cancel();
    super.disconnect();
  }
};
}


</script>

<style>
#app {
  /* overflow: hidden; */
  /* height: 100vh; */
  /* width: 100vw; */
  box-sizing: border-box;
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  /* margin-top: 60px; */
}

</style>
