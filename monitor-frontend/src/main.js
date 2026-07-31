import { createApp } from 'vue';
import 'layui/dist/css/layui.css';
import '@/styles/global.css';
import App from './App.vue';
import router from './router';

createApp(App).use(router).mount('#app');
