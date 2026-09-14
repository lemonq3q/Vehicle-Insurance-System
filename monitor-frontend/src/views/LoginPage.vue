<template>
  <main class="login-page">
    <section class="login-intro" aria-labelledby="login-title">
      <div class="login-brand"><span><img src="@/assets/brand/idatag-monitor-logo.png" alt="" /></span><div><strong>iDatag</strong><small>运营监控中心</small></div></div>
      <div class="intro-copy">
        <p>PLATFORM OPERATIONS</p>
        <h1 id="login-title">让每一项平台运行数据<br>清晰、及时、可追踪</h1>
        <span>统一查看企业运营、系统调用、OCR 用量与资金流水，为平台决策提供可靠依据。</span>
      </div>
      <p class="login-security"><i class="layui-icon layui-icon-password" aria-hidden="true"></i> 独立平台账号 · JWT 会话保护</p>
    </section>

    <section class="login-form-side" aria-label="监控后台登录">
      <form class="login-card" novalidate @submit.prevent="submit">
        <header>
          <p>欢迎回来</p>
          <h2>登录运营监控中心</h2>
          <span>请输入平台管理员或客服手机号</span>
        </header>

        <div class="field" :class="{ invalid: errors.username }">
          <label for="monitor-username">登录手机号</label>
          <div class="input-shell"><i class="layui-icon layui-icon-username" aria-hidden="true"></i><input id="monitor-username" ref="username" v-model.trim="form.username" class="layui-input" type="tel" maxlength="11" inputmode="numeric" autocomplete="username" placeholder="请输入手机号码" @blur="validateField('username')"></div>
          <p v-if="errors.username" class="error-text" role="alert">{{ errors.username }}</p>
        </div>

        <div class="field" :class="{ invalid: errors.password }">
          <label for="monitor-password">登录密码</label>
          <div class="input-shell"><i class="layui-icon layui-icon-password" aria-hidden="true"></i><input id="monitor-password" v-model="form.password" class="layui-input" :type="passwordVisible ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入密码" @blur="validateField('password')"><button type="button" class="password-toggle" :aria-label="passwordVisible ? '隐藏密码' : '显示密码'" @click="passwordVisible = !passwordVisible"><i class="layui-icon" :class="passwordVisible ? 'layui-icon-eye-invisible' : 'layui-icon-eye'"></i></button></div>
          <p v-if="errors.password" class="error-text" role="alert">{{ errors.password }}</p>
        </div>

        <p v-if="submitError" class="submit-error" role="alert"><i class="layui-icon layui-icon-close-fill" aria-hidden="true"></i>{{ submitError }}</p>
        <button class="layui-btn monitor-primary login-submit" :disabled="submitting">
          <i v-if="submitting" class="layui-icon layui-icon-loading layui-anim layui-anim-rotate layui-anim-loop" aria-hidden="true"></i>
          {{ submitting ? '正在登录…' : '登录监控后台' }}
        </button>
        <p class="account-note">监控账号由平台管理员统一创建，如无法登录请联系管理员。</p>
      </form>
    </section>
  </main>
</template>

<script>
import { authApi } from '@/api/monitor';
import { saveSession } from '@/auth/session';

/**
 * 监控后台独立登录页，仅提供管理员分配账号的密码登录，不包含开放注册和找回密码。
 * 表单先执行本地必填校验，再由后端验证 BCrypt 密码、账号状态和有效角色。
 */
export default {
  name: 'LoginPage',
  data() {
    return {
      form: { username: '', password: '' },
      errors: { username: '', password: '' },
      submitError: '',
      submitting: false,
      passwordVisible: false
    };
  },
  mounted() {
    this.$refs.username?.focus();
  },
  methods: {
    /** 按字段生成就近错误提示，空值之外的凭据正确性由服务端统一判断。 */
    validateField(field) {
      this.errors[field] = field === 'username' ? (/^1\d{10}$/.test(this.form.username) ? '' : '请输入有效的手机号码') : (this.form.password ? '' : '请输入登录密码');
      return !this.errors[field];
    },
    /**
     * 校验全部字段并提交登录。成功后保存 token 与用户角色，再返回原目标页面；
     * 后端验证失败时保留手机号和密码原值，便于用户定位错误并直接修改后重试。
     */
    async submit() {
      const valid = ['username', 'password'].map(this.validateField).every(Boolean);
      if (!valid || this.submitting) return;
      this.submitting = true;
      this.submitError = '';
      try {
        const result = await authApi.login(this.form);
        saveSession(result.token, result.user);
        localStorage.setItem('monitorRole', result.user.roleCode);
        const redirect = typeof this.$route.query.redirect === 'string' && this.$route.query.redirect.startsWith('/') ? this.$route.query.redirect : '/dashboard';
        await this.$router.replace(redirect);
      } catch (error) {
        this.submitError = error.message || '登录失败，请稍后重试';
      } finally {
        this.submitting = false;
      }
    }
  }
};
</script>

<style scoped>
.login-page { display: grid; grid-template-columns: minmax(0,1.2fr) minmax(420px,.8fr); min-height: 100dvh; background: var(--surface); }
.login-intro { position: relative; display: flex; flex-direction: column; min-height: 100%; padding: clamp(32px,5vw,72px); overflow: hidden; color: #e2e8f0; background: linear-gradient(145deg,#0f172a 0%,#172554 58%,#1e3a8a 100%); }
.login-intro::before,.login-intro::after { position: absolute; border: 1px solid rgba(147,197,253,.16); border-radius: 50%; content: ''; }
.login-intro::before { right: -18vw; bottom: -28vw; width: 56vw; height: 56vw; }.login-intro::after { right: 7vw; bottom: -11vw; width: 31vw; height: 31vw; }
.login-brand { position: relative; z-index: 1; display: flex; align-items: center; gap: 12px; }.login-brand>span { display: grid; place-items: center; width: 42px; height: 42px; }.login-brand img { width:100%;height:100%;object-fit:contain }.login-brand div { display:flex;flex-direction:column }.login-brand strong { color:#fff;font-size:17px }.login-brand small { color:#94a3b8;font-size:12px }
.intro-copy { position: relative; z-index: 1; max-width: 680px; margin: auto 0; }.intro-copy p { margin:0 0 18px;color:#93c5fd;font-size:12px;font-weight:700;letter-spacing:.16em }.intro-copy h1 { margin:0 0 24px;color:#fff;font-size:clamp(38px,4.5vw,66px);line-height:1.12;letter-spacing:-.04em }.intro-copy span { display:block;max-width:590px;color:#cbd5e1;font-size:17px;line-height:1.8 }
.login-security { position:relative;z-index:1;margin:0;color:#94a3b8;font-size:12px }.login-security i { margin-right:6px;color:#60a5fa }
.login-form-side { display:grid;place-items:center;padding:40px clamp(28px,5vw,72px);background:#f8fafc }.login-card { width:min(420px,100%);padding:34px;border:1px solid var(--border);border-radius:14px;background:#fff;box-shadow:0 24px 60px rgba(15,23,42,.10) }.login-card header { margin-bottom:28px }.login-card header p { margin:0 0 7px;color:var(--primary);font-weight:700 }.login-card h2 { margin:0 0 8px;font-size:25px }.login-card header span,.account-note { color:var(--muted);font-size:12px }.login-card .field { margin-bottom:18px }.input-shell { position:relative }.input-shell>i { position:absolute;z-index:1;top:50%;left:13px;color:#64748b;transform:translateY(-50%) }.input-shell .layui-input { height:44px;padding:0 44px 0 40px }.password-toggle { position:absolute;top:0;right:0;display:grid;place-items:center;width:44px;height:44px;padding:0;border:0;background:transparent;color:#64748b }.login-submit { width:100%;height:44px;margin-top:4px }.login-submit i { margin-right:7px }.submit-error { display:flex;align-items:center;gap:7px;padding:10px 12px;margin:-2px 0 14px;border-radius:7px;color:var(--danger);background:#fef2f2;font-size:12px }.account-note { margin:16px 0 0;text-align:center }
/* 隐藏 Edge/IE 为密码框注入的原生显隐和清除控件，避免与右侧自定义按钮重复。 */
#monitor-password::-ms-reveal,
#monitor-password::-ms-clear { display: none; }
@media(max-width:900px){.login-page{grid-template-columns:1fr}.login-intro{min-height:290px;padding:28px}.intro-copy{margin:52px 0 18px}.intro-copy h1{font-size:34px}.login-security{display:none}.login-form-side{padding:32px 20px}}
@media(max-width:480px){.login-intro{min-height:230px}.intro-copy{margin:38px 0 0}.intro-copy h1{font-size:28px}.intro-copy span{display:none}.login-card{padding:26px 20px;border-radius:10px}}
</style>
