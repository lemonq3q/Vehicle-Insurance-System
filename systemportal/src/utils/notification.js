const listeners = new Set();

function publish(type, message) {
  const content = String(message || '').trim();
  listeners.forEach(listener => listener({
    id: `${Date.now()}-${Math.random()}`,
    type,
    message: content
  }));
}

export function subscribeNotifications(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function notifySuccess(message) {
  publish('success', message);
}

export function notifyWarning(message = '请求异常') {
  publish('warning', message || '请求异常');
}

export function notifyError(message = '请求错误') {
  publish('error', message || '请求错误');
}
