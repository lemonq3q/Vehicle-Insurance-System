import { reactive } from 'vue';

/**
 * 门户加载任务按业务区域计数；只有 API 显式声明 fullscreen 的重要提交才锁定全页。
 * 同一区域的并发查询共用一份状态，组件自行选择父级区域，避免每张卡片各显示一个动画。
 * 状态只保存任务数，不保存请求参数、账号资料或 DOM，页面离开不影响请求正常收尾。
 */
export const loadingState = reactive({ regions: {}, fullscreen: 0, main: 0 });

/**
 * 登记显式指定范围的任务，返回成功、失败、超时或取消时使用的幂等释放函数。
 * 无配置的请求不显示动画；普通查询提供 scope，全屏必须显式设 fullscreen=true。
 * @param {{scope?: string, fullscreen?: boolean}|undefined} options API 声明的前端交互配置，不发送给后端。
 * @returns {Function} 仅释放本任务的函数，不会提前关闭同组其他任务的动画。
 */
function open(options) {
  if (!options || (!options.fullscreen && !options.scope)) return () => {};
  const fullscreen = options.fullscreen === true;
  const scope = options.scope;
  if (fullscreen) loadingState.fullscreen++;
  else loadingState.regions[scope] = (loadingState.regions[scope] || 0) + 1;
  let finished = false;
  return () => {
    if (finished) return;
    finished = true;
    if (fullscreen) loadingState.fullscreen--;
    else if (--loadingState.regions[scope] === 0) delete loadingState.regions[scope];
  };
}

/**
 * 判断组件绑定的业务区域是否仍有请求；允许本地 SDK 加载状态一起参与父级遮罩。
 * @param {string|string[]|{scopes?: string[], busy?: boolean}} value 指令传入的区域名或本地加载状态。
 * @returns {boolean} 当前父区域是否需要显示一个加载动画。
 */
export function isRegionLoading(value) {
  if (value?.busy) return true;
  const scopes = typeof value === 'string' ? [value] : Array.isArray(value) ? value : value?.scopes || [];
  return scopes.some(scope => (loadingState.regions[scope] || 0) > 0);
}

export default { open };
