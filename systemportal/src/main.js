import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import store from './store';
import { setUnauthorizedHandler } from './api/request';
import 'layui/dist/css/layui.css';
import './styles/global.css';

setUnauthorizedHandler(() => {
  store.dispatch('logout');
  if (router.currentRoute.value.path !== '/login') {
    router.replace('/login');
  }
});

createApp(App).use(router).use(store).mount('#app');
