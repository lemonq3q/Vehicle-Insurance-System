<template>
  <main class="sso-page" aria-live="polite">
    <section class="sso-card">
      <div v-if="!failed" class="sso-spinner" aria-hidden="true"></div>
      <h1>{{ failed ? '自动登录失败' : '正在返回 SaaS 门户' }}</h1>
      <p>{{ message }}</p>
      <router-link v-if="failed" class="retry-button" to="/login">返回登录页</router-link>
    </section>
  </main>
</template>

<script>
import { exchangePortalSsoCode } from '@/api/portal';

export default {
  name: 'PortalSsoCallbackPage',
  /**
   * 初始化从车险系统返回门户的自动登录提示状态。
   */
  data() {
    return {
      failed: false,
      message: '正在安全验证您的车险系统登录信息…'
    };
  },
  /**
   * 读取车险系统签发的一次性 code 并换取门户 token 与完整账号上下文，成功后替换到仪表盘。
   * 缺少 code 或换票失败时不使用旧会话，并清除地址栏中的敏感 code。
   */
  async mounted() {
    const code = String(this.$route.query.code || '').trim();
    if (!code) {
      this.fail('缺少单点登录授权码，请从车险系统重新返回门户。');
      return;
    }
    try {
      const response = await exchangePortalSsoCode(code);
      this.$store.commit('setToken', response.data.token);
      this.$store.commit('setContext', response.data);
      await this.$router.replace('/portal/dashboard');
    } catch (error) {
      this.fail(error.message || '单点登录认证失败');
    }
  },
  methods: {
    /**
     * 切换为失败状态、显示原因并移除回调地址中的一次性授权码，防止刷新重复消费。
     */
    fail(message) {
      this.failed = true;
      this.message = message;
      window.history.replaceState({}, document.title, this.$router.resolve('/sso/callback').href);
    }
  }
};
</script>

<style scoped>
.sso-page { display: grid; min-height: 100dvh; padding: 24px; place-items: center; background: #f0fdf4; }
.sso-card { width: min(420px, 100%); padding: 40px 32px; border: 1px solid #bbf7d0; border-radius: 12px; background: #fff; box-shadow: 0 18px 45px rgba(15,23,42,.1); text-align: center; }
.sso-card h1 { margin: 20px 0 10px; color: #0f172a; font-size: 24px; }.sso-card p { margin: 0; color: #475569; line-height: 1.7; }
.sso-spinner { width: 42px; height: 42px; margin: auto; border: 4px solid #dcfce7; border-top-color: #16a34a; border-radius: 50%; animation: spin 700ms linear infinite; }
.retry-button { display: inline-block; min-width: 132px; min-height: 44px; margin-top: 24px; padding: 0 20px; border-radius: 6px; background: #16a34a; color: #fff; line-height: 44px; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .sso-spinner { animation: none; } }
</style>
