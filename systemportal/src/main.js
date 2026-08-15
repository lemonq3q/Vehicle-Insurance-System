import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import store from './store';
import { setUnauthorizedHandler } from './api/request';
import 'layui/dist/css/layui.css';
import './styles/global.css';

/**
 * 统一处理接口返回的未授权状态：清除 Vuex 中的登录及企业上下文，并将非登录页导航回登录页。
 * 路径判断避免登录接口自身返回 401 时重复执行同一路由替换。
 */
setUnauthorizedHandler(() => {
  store.dispatch('logout');
  if (router.currentRoute.value.path !== '/login') {
    router.replace('/login');
  }
});

/**
 * 创建门户应用并注册路由与全局状态后挂载到页面根节点。
 */
createApp(App).use(router).use(store).mount('#app');
