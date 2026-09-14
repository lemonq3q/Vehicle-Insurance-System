import { reactive, readonly } from 'vue';

const TOKEN_KEY = 'monitorToken';
const USER_KEY = 'monitorUser';

/**
 * 监控后台的轻量会话状态。项目当前没有引入 Vuex，因此使用 Vue 共享响应式对象集中维护
 * token 与用户资料；localStorage 只负责刷新恢复，权限展示始终读取同一运行时状态。
 */
const state = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: readUser()
});

/** 从本地缓存安全恢复用户资料，损坏的 JSON 会被清除并按未登录处理。 */
function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null');
  } catch {
    localStorage.removeItem(USER_KEY);
    return null;
  }
}

/**
 * 保存后端认证结果并同步响应式状态。只有 token 与用户资料同时存在才形成完整会话，
 * 避免页面把半成品登录结果误判为已认证。
 */
export function saveSession(token, user) {
  state.token = token || '';
  state.user = user || null;
  if (state.token) localStorage.setItem(TOKEN_KEY, state.token);
  else localStorage.removeItem(TOKEN_KEY);
  if (state.user) localStorage.setItem(USER_KEY, JSON.stringify(state.user));
  else localStorage.removeItem(USER_KEY);
}

/** 更新刷新令牌但保留当前用户资料，供请求拦截器处理后端滑动续签。 */
export function updateToken(token) {
  if (!token) return;
  state.token = token;
  localStorage.setItem(TOKEN_KEY, token);
}

/** 清理监控端全部身份数据，401、主动退出和无效上下文共用该入口。 */
export function clearSession() {
  saveSession('', null);
  localStorage.removeItem('monitorRole');
}

/** 返回当前令牌，避免请求层直接散落 localStorage 键名。 */
export function getToken() {
  return state.token;
}

/** 向组件暴露只读响应式会话，防止页面绕过统一方法直接修改身份。 */
export const authState = readonly(state);

/** 只有令牌存在时才具备基础登录态，路由进入后还会调用 /me 验证服务端会话。 */
export function isAuthenticated() {
  return Boolean(state.token);
}
