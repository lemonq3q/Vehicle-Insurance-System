<template>
  <main v-region-loading="['auth-callback', 'context']" class="wechat-result" aria-live="polite"><h1>{{ error ? '微信验证未完成' : '正在完成微信验证' }}</h1><p>{{ error || '请稍候，正在建立安全登录会话…' }}</p><router-link :to="binding ? '/portal/user/profile' : '/login'">{{ binding ? '返回个人信息' : '返回登录' }}</router-link></main>
</template>
<script>
import { completeWechat } from '@/api/wechat';

/**
 * 接收官方顶层重定向，只在持有本标签页 verifier 时消费授权码；立即清理 URL 中的临时参数。
 * 登录复用既有 Vuex 上下文，绑定保留当前会话并返回个人中心；失败不创建任何前端登录态。
 */
export default {
  name: 'WechatCallbackPage',
  /** 保存反馈与返回目的，不在页面渲染临时授权码。 */
  data() { return { error: '', binding: false }; },
  /** 校验原浏览器事务并提交后端，防止复制回调链接导致登录到其他人的账号。 */
  async mounted() {
    const { code, state } = this.$route.query;
    window.history.replaceState(window.history.state, '', window.location.pathname);
    const key = `wechat:${state}`;
    try {
      const pending = JSON.parse(sessionStorage.getItem(key) || 'null');
      sessionStorage.removeItem(key);
      if (!pending || pending.expiresAt < Date.now()) throw new Error('授权已过期或不是由当前页面发起，请重新扫码');
      this.binding = pending.binding;
      if (!code || !state) throw new Error('已取消微信授权，请重新扫码');
      const response = await completeWechat(this.binding, { code, state, verifier: pending.verifier });
      if (!this.binding) {
        this.$store.commit('setToken', response.data.token);
        this.$store.commit('setContext', response.data);
      } else await this.$store.dispatch('loadContext');
      await this.$router.replace(this.binding ? '/portal/user/profile' : '/portal/dashboard');
    } catch (error) { this.error = error.message || '微信验证失败，请重试'; }
  }
};
</script>
<style scoped>.wechat-result{max-width:560px;margin:15vh auto;padding:32px;text-align:center;border:1px solid var(--portal-border);border-radius:var(--portal-radius);background:#fff}.wechat-result p{margin:24px 0;line-height:1.7}</style>
