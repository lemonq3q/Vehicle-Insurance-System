import { createApp } from 'vue';
import App from './App.vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import '@/style/global.css';
import '@/style/container.css';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import zhCn from 'element-plus/es/locale/lang/zh-cn'; 
import router from '@/router';
import store from '@/store';

const app = createApp(App);
app.use(ElementPlus, {
  locale: zhCn, // 关键：配置中文语言包
});
app.use(router);
app.use(store);
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}
app.mount('#app');
export default app;

