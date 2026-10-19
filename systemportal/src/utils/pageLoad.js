/**
 * 初始化页面数据状态。初次挂载即处于 pending，不能用空对象判断“没有企业/订阅/资料”。
 * ready 只在完整业务快照加载成功后置位；刷新期间保留已确认的数据，失败不伪造空状态。
 * @returns {object} 由页面 data 纳入响应式管理的加载状态。
 */
export function createPageLoad() { return { pending: true, ready: false, error: '' }; }

/**
 * 统一收尾页面读取过程，覆盖网络失败、业务拒绝和数据转换异常。
 * 页面传入的任务负责读取并同步完整快照；失败由页面展示重试，禁止回落到“没有数据”的分支。
 * @param {object} state 当前页面的加载状态。
 * @param {Function} task 返回 Promise 的数据读取和快照同步任务，不执行重要写入。
 * @returns {Promise<boolean>} 是否成功取得可供业务条件判断的数据。
 */
export async function loadPageData(state, task) {
  state.pending = true;
  state.error = '';
  try {
    await task();
    state.ready = true;
    return true;
  } catch (error) {
    state.error = error?.message || '数据加载失败，请重试';
    return false;
  } finally {
    state.pending = false;
  }
}
