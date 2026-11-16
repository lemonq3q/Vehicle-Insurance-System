const listeners = new Set();

/**

 * * 向所有通知中心订阅者发布带唯一 ID 的消息，统一修剪文案并保留消息严重级别。

 */
function publish(type, message) {
  const content = String(message || '').trim();
  listeners.forEach(listener => listener({
    id: `${Date.now()}-${Math.random()}`,
    type,
    message: content
  }));
}

/**

 * * 注册全局通知监听器并返回取消订阅函数，组件卸载时调用可避免重复接收和内存泄漏。

 */
export function subscribeNotifications(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**

 * * 发布业务操作成功消息。

 */
export function notifySuccess(message) {
  publish('success', message);
}

/**

 * * 发布可由用户修正的业务警告，空文案回退到统一请求异常提示。

 */
export function notifyWarning(message = '请求异常') {
  publish('warning', message || '请求异常');
}

/**

 * * 发布系统或网络错误，空文案回退到统一请求错误提示。

 */
export function notifyError(message = '请求错误') {
  publish('error', message || '请求错误');
}
