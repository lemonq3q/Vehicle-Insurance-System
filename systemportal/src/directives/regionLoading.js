import { ref, watch } from 'vue';
import { isRegionLoading, loadingState } from '@/utils/loading';

const regions = new WeakMap();

/**
 * 在已有业务容器上挂载局部遮罩，不新增布局包装层、不覆盖侧栏或其他独立区域。
 * 有边框的卡片使用 inset:0 覆盖整个 padding box；无边框的整页分组显式指定 main 呈现。
 * 普通刷新延迟 160ms 避免闪烁；首次加载可 immediate，业务页面另外使用 ready 门控阻止默认分支渲染。
 * @param {HTMLElement} element 指令绑定的业务区域。
 * @param {object} binding Vue 指令配置，value 为区域名称、名称数组或本地 busy 状态。
 */
function mounted(element, binding) {
  const value = ref(binding.value);
  const mask = document.createElement('div');
  mask.className = 'portal-loading-mask';
  mask.setAttribute('role', 'status');
  mask.setAttribute('aria-live', 'polite');
  const indicator = document.createElement('span');
  indicator.className = 'portal-loading-indicator';
  const ring = document.createElement('span');
  ring.className = 'portal-loading-ring';
  ring.setAttribute('aria-hidden', 'true');
  const text = document.createElement('span');
  text.textContent = '正在加载…';
  indicator.append(ring, text);
  mask.append(indicator);
  const previousBusy = element.getAttribute('aria-busy');
  element.classList.add('portal-loading-region');
  element.classList.toggle('portal-loading-compact', value.value?.compact === true);
  let timer;
  let ownsMain = false;
  /*
   * 全屏提交期间暂停局部动画，避免一页内重复转圈；任务计数仍保留，提交结束后恢复未完成查询。
   * 遮罩只拦截所属容器的鼠标操作，导航及页面其他区域仍然可以使用。
   */
  const stop = watch([
    () => isRegionLoading(value.value),
    () => value.value?.main === true,
    () => loadingState.fullscreen > 0,
    () => loadingState.main > 0
  ], ([busy, main, fullscreen, covered]) => {
    const current = { busy, main, fullscreen: Number(fullscreen), mainCount: Number(covered) };
    window.clearTimeout(timer);
    element.setAttribute('aria-busy', String(busy));
    /* 没有边框的整页数据容器交由布局的 main 遮罩呈现，不在原容器上画突兀的白色矩形。 */
    if (current.main && busy && !ownsMain) { ownsMain = true; loadingState.main++; }
    else if ((!current.main || !busy) && ownsMain) { ownsMain = false; loadingState.main--; }
    mask.remove();
    element.classList.remove('portal-region-busy');
    if (busy && !current.main && current.fullscreen === 0 && current.mainCount === 0) {
      const show = () => {
        element.classList.add('portal-region-busy');
        element.append(mask);
      };
      if (value.value?.immediate) show();
      else timer = window.setTimeout(show, 160);
    }
  }, { immediate: true, flush: 'post' });
  regions.set(element, {
    value,
    /** 离开页面时释放观察器、延迟任务和辅助属性；不取消业务请求或清理其他区域的加载计数。 */
    cleanup() {
      stop();
      if (ownsMain) { ownsMain = false; loadingState.main--; }
      window.clearTimeout(timer);
      mask.remove();
      element.classList.remove('portal-loading-region', 'portal-region-busy', 'portal-loading-compact');
      if (previousBusy === null) element.removeAttribute('aria-busy');
      else element.setAttribute('aria-busy', previousBusy);
    }
  });
}

export default {
  mounted,
  /** 参数更新时沿用已有观察器，支持 SDK 的 busy 状态与查询区域组合，不重复创建遮罩。 */
  updated(element, binding) {
    regions.get(element).value.value = binding.value;
    element.classList.toggle('portal-loading-compact', binding.value?.compact === true);
  },
  /** 容器卸载时销毁自己的加载反馈，防止旧页面计时器在新页面插入遮罩。 */
  beforeUnmount(element) { regions.get(element)?.cleanup(); regions.delete(element); }
};
