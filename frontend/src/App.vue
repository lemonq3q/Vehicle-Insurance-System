<template>
  <router-view></router-view>
</template>

<script setup>

/**
 * 将 ResizeObserver 高频回调合并到最后一次触发，降低 Element Plus 表格连续测量尺寸时的执行频率。
 * 每次调用都会取消上一计时器，并在指定延迟后以原参数执行原函数。
 */
const debounce = (fn, delay) => {
  let timer
  return (...args) => {
    if (timer) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      fn(...args)
    }, delay)
  }
}

/**
 * 用带防抖能力的浏览器 ResizeObserver 替换全局实现。
 * Element Plus 表格仍按原方式创建观察器，但尺寸回调至少间隔 20ms，从而避免短时间内循环触发
 * “ResizeObserver loop completed”并减少复杂工单表格重排次数。
 */
const _ResizeObserver = window.ResizeObserver;
window.ResizeObserver = class ResizeObserver extends _ResizeObserver{
  /**
   * * 保留原生观察器的构造契约，只包装调用方传入的回调，不改变被观察元素及 disconnect 行为。
   */
  constructor(callback) {
    callback = debounce(callback, 20);
    super(callback);
  }
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
