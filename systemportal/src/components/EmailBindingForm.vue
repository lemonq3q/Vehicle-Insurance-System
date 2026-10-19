<template>
  <form class="email-binding" @submit.prevent="submit">
    <h2>绑定邮箱</h2>
    <p>验证邮箱并设置密码后，可以使用邮箱登录 SaaS 门户和车险系统。</p>
    <div class="form-field"><label for="bindingEmail">邮箱</label><input id="bindingEmail" v-model.trim="form.email" class="layui-input" type="email" maxlength="100" autocomplete="email" required /></div>
    <div class="form-field"><label for="bindingCode">邮箱验证码</label><div class="verification-row"><input id="bindingCode" v-model.trim="form.emailCode" class="layui-input" inputmode="numeric" maxlength="6" autocomplete="one-time-code" required /><button v-region-loading="{ scopes: ['binding-email-code'], compact: true }" type="button" class="layui-btn layui-btn-primary" :disabled="sending || seconds > 0 || saving" @click="sendCode">{{ sending ? '发送中...' : seconds > 0 ? `${seconds}s 后重发` : '获取邮箱验证码' }}</button></div></div>
    <div class="form-field"><label for="bindingPassword">登录密码</label><PasswordField id="bindingPassword" v-model="form.password" autocomplete="new-password" /><small>密码需为 8 至 64 位</small></div>
    <button class="layui-btn portal-btn portal-btn-primary" :disabled="saving">{{ saving ? '绑定中...' : '验证并绑定邮箱' }}</button>
  </form>
</template>

<script>
import { bindEmail, sendBindingEmailCode } from '@/api/portal';
import PasswordField from '@/components/PasswordField.vue';
import { notifySuccess, notifyWarning } from '@/utils/notification';

/**
 * 为无邮箱账号提供经过邮件验证的绑定入口；验证码与当前会话关联。
 * 成功后由父页面刷新用户上下文，失败保留输入以便修正，离开组件时清理计时器。
 */
export default {
  name: 'EmailBindingForm',
  components: { PasswordField },
  emits: ['bound'],
  /**
   * 初始化只在本页使用的绑定表单与发送、保存状态，不持久化验证码或密码。
   */
  data() { return { form: { email: '', emailCode: '', password: '' }, sending: false, saving: false, seconds: 0, timer: null }; },
  /**
   * 离开个人中心后释放重发倒计时。
   */
  beforeUnmount() { window.clearInterval(this.timer); },
  methods: {
    /**
     * 验证待绑定邮箱的格式和数据库支持的长度，返回首个错误文案。
     */
    emailError() {
      return this.form.email.length <= 100 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.form.email) ? '' : '请输入有效的邮箱地址';
    },
    /**
     * 请求真实绑定邮件；服务端接受发送后才进入重发冷却，错误由统一拦截器展示。
     */
    async sendCode() {
      if (this.sending || this.seconds > 0) return;
      if (this.emailError()) { notifyWarning(this.emailError()); return; }
      this.sending = true;
      try {
        const response = await sendBindingEmailCode({ email: this.form.email });
        notifySuccess(response.msg);
        this.seconds = response.data.retryAfterSeconds;
        window.clearInterval(this.timer);
        this.timer = window.setInterval(() => { if (--this.seconds <= 0) window.clearInterval(this.timer); }, 1000);
      } catch { /* 接口错误由请求拦截器展示，失败不启动倒计时。 */ }
      finally { this.sending = false; }
    },
    /**
     * 校验六位验证码和新密码后提交绑定；成功清除敏感输入并通知父页面同步新资料。
     */
    async submit() {
      if (this.saving) return;
      if (this.emailError()) { notifyWarning(this.emailError()); return; }
      if (!/^\d{6}$/.test(this.form.emailCode)) { notifyWarning('请输入 6 位邮箱验证码'); return; }
      if (this.form.password.length < 8 || this.form.password.length > 64 || new TextEncoder().encode(this.form.password).length > 72) { notifyWarning('密码需为 8 至 64 位，且编码长度不超过 72 字节'); return; }
      this.saving = true;
      try {
        const response = await bindEmail(this.form);
        this.form.password = ''; this.form.emailCode = '';
        notifySuccess(response.msg);
        this.$emit('bound', response.data);
      } catch { /* 保留待修正表单，统一拦截器负责错误提示。 */ }
      finally { this.saving = false; }
    }
  }
};
</script>

<style scoped>
.email-binding { margin-top: 24px; padding: 24px; border: 1px solid var(--portal-border); border-radius: 8px; }
.email-binding p, .email-binding small { color: var(--portal-muted); }
.email-binding .form-field { margin: 16px 0; max-width: 520px; }
.verification-row { display: flex; gap: 12px; }.verification-row input { min-width: 0; }.verification-row button { flex-shrink: 0; }
</style>
