/* global globalThis */
/**
 * 保存本应用 warning/error 顶部提示的锁，以提示类型和最终文案共同索引。
 * 获取时按加入时间判断两秒有效期，不启动计时器；销毁回调只释放自己的任务。
 */
const activeMessages = new Map();
export const MESSAGE_LOCK_TIMEOUT_MS = 2000;

/**
 * 根据通知类型和展示文本同步获取占用，重复且未过期时返回 null。
 * 成功及普通提示不写缓存，返回空操作回调以兼容统一展示流程。
 * 新锁记录加入时间与随机任务 ID；返回的销毁回调同时校验索引和任务 ID，
 * 防止旧弹窗在锁过期并被替换后误删新锁。创建失败也应调用同一回调。
 */
export function acquireMessage(type, message) {
  if (type !== 'warning' && type !== 'error') return () => {};
  const key = JSON.stringify([type, message]);
  const now = Date.now();
  const current = activeMessages.get(key);
  if (current && now - current.addedAt < MESSAGE_LOCK_TIMEOUT_MS) return null;
  const taskId = Array.from(globalThis.crypto.getRandomValues(new Uint32Array(4)),
    value => value.toString(16).padStart(8, '0')).join('');
  activeMessages.set(key, { addedAt: now, taskId });
  return () => {
    if (activeMessages.get(key)?.taskId === taskId) activeMessages.delete(key);
  };
}
