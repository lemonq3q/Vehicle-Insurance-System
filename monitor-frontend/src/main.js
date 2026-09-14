import { createApp } from 'vue';
import 'layui/dist/css/layui.css';
import '@/styles/global.css';
import App from './App.vue';
import router from './router';
import { clearSession } from '@/auth/session';
import { setUnauthorizedHandler } from '@/api/request';

/**
 * 当后端判定 JWT 无效、过期或 Redis 会话被覆盖时，统一清理本地身份并返回登录页。
 * 登录请求本身失败不会触发该流程，避免错误密码造成重复导航。
 */
setUnauthorizedHandler(() => {
  clearSession();
  if (router.currentRoute.value.path !== '/login') router.replace('/login');
});

/**
 * 创建监控后台应用，注册路由后挂载根组件。
 */
createApp(App).use(router).mount('#app');
