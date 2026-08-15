import { createApp } from 'vue';
import 'layui/dist/css/layui.css';
import '@/styles/global.css';
import App from './App.vue';
import router from './router';

/**
 * 创建监控后台应用，注册路由后挂载根组件。
 */
createApp(App).use(router).mount('#app');
