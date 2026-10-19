<template>
  <main class="wechat-result" aria-live="polite"><h1>{{ error ? failureTitle : binding ? '正在绑定微信' : '正在完成微信登录' }}</h1><p :role="error ? 'alert' : 'status'">{{ error || '请稍候，正在建立安全登录会话…' }}</p><router-link :to="binding ? '/home/personalCenter' : '/login'">{{ binding ? '返回个人信息' : '返回登录' }}</router-link></main>
</template>
<script>
import { completeWechat } from '@/api/wechat';
import Storage from '@/utils/storage';

/**
 * 将认证拒绝转换为明确的业务反馈，仅后端确认的账号缺失才显示“账号不存在”。
 * 网关路径 404、微信服务故障和绑定冲突分别保留真实原因，不据此推断账号不存在或自动注册。
 * @param {Error & {code?: number}} error 统一请求层或本地授权校验产生的异常。
 * @param {boolean} binding 当前事务是否为已登录用户绑定微信。
 * @returns {{title: string, message: string}} 回调页标题与可操作的恢复说明，不含授权凭据。
 */
function describeFailure(error, binding) {
  const code = Number(error.code);
  const message = error.message || '微信授权未能完成，请返回后重新扫码';
  if (!binding && code === 404 && message.startsWith('账号不存在')) {
    return {
      title: '账号不存在',
      message: '该微信尚未关联已注册账号。车险系统不提供注册，请先使用已有邮箱账号登录并绑定微信，或前往 SaaS 门户注册。'
    };
  }
  if ([502, 503].includes(code)) return { title: '微信登录服务暂不可用', message };
  return { title: binding ? '微信绑定失败' : '微信登录失败', message };
}

/**
 * 接收官方顶层重定向，只在持有本标签页 verifier 时消费授权码；立即清理 URL 中的临时参数。
 * 登录复用既有 Vuex 上下文，绑定保留当前会话并返回个人中心；失败不创建任何前端登录态。
 */
export default {
  name: 'WechatCallbackPage',
  /** 保存反馈与返回目的，不在页面渲染临时授权码。 */
  data() { return { error: '', failureTitle: '', binding: false }; },
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
        const { token, user } = response.data.data;
        Storage.set('token', token, 60 * 60 * 24);
        localStorage.setItem('userInfo', JSON.stringify(user));
        this.$store.commit('login/setToken', token);
        this.$store.commit('login/setUser', user);
      }
      await this.$router.replace(this.binding ? '/home/personalCenter' : '/home');
    } catch (error) {
      const failure = describeFailure(error, this.binding);
      this.failureTitle = failure.title;
      this.error = failure.message;
    }
  }
};
</script>
<style scoped>.wechat-result{max-width:560px;margin:15vh auto;padding:32px;text-align:center}.wechat-result p{margin:24px 0;line-height:1.7}</style>
