<template>
  <main class="sso-page" aria-live="polite">
    <section class="sso-card">
      <div class="sso-spinner" aria-hidden="true"></div>
      <h1>{{ failed ? '自动登录失败' : '正在进入车险系统' }}</h1>
      <p>{{ message }}</p>
      <button v-if="failed" class="retry-button" type="button" @click="$router.replace('/login')">
        返回登录页
      </button>
    </section>
  </main>
</template>

<script>
import { exchangeSsoCode } from '@/api/login';
import Storage from '@/utils/storage';
import { objToJsonStr } from '@/utils/convert';

export default {
  name: 'SsoCallbackPage',
  data() {
    return {
      failed: false,
      message: '正在安全验证您的 SaaS 登录信息…'
    };
  },
  async mounted() {
    const code = String(this.$route.query.code || '').trim();
    if (!code) {
      this.fail('缺少单点登录授权码，请从 SaaS 门户重新进入。');
      return;
    }
    try {
      const response = await exchangeSsoCode(code);
      const payload = response.data;
      if (Number(payload.code) !== 200) {
        this.fail(payload.msg || '单点登录认证失败');
        return;
      }
      Storage.set('token', payload.data.token, 60 * 60 * 24);
      localStorage.setItem('userInfo', objToJsonStr(payload.data.user));
      this.$store.commit('login/setToken', payload.data.token);
      this.$store.commit('login/setUser', payload.data.user);
      await this.$router.replace('/home');
    } catch (error) {
      this.fail(error.response?.data?.msg || error.message || '单点登录认证失败');
    }
  },
  methods: {
    fail(message) {
      this.failed = true;
      this.message = message;
      window.history.replaceState({}, document.title, '/sso/callback');
    }
  }
};
</script>

<style scoped>
.sso-page {
  display: grid;
  min-height: 100dvh;
  padding: 24px;
  place-items: center;
  background: #f0f7ff;
}

.sso-card {
  width: min(420px, 100%);
  padding: 40px 32px;
  border: 1px solid #bae6fd;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.1);
  text-align: center;
}

.sso-card h1 {
  margin: 20px 0 10px;
  color: #0f172a;
  font-size: 24px;
}

.sso-card p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.sso-spinner {
  width: 42px;
  height: 42px;
  margin: auto;
  border: 4px solid #dbeafe;
  border-top-color: #0369a1;
  border-radius: 50%;
  animation: spin 700ms linear infinite;
}

.retry-button {
  min-width: 132px;
  min-height: 44px;
  margin-top: 24px;
  border: 0;
  border-radius: 6px;
  background: #0369a1;
  color: #fff;
  cursor: pointer;
}

.retry-button:focus-visible {
  outline: 3px solid rgba(3, 105, 161, 0.35);
  outline-offset: 3px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .sso-spinner { animation: none; }
}
</style>
