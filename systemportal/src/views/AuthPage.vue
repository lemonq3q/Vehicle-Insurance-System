<template>
  <main class="auth-page portal-shell">
    <router-link class="auth-brand" to="/">
      <span class="brand-mark"><img src="@/assets/brand/idatag-logo.png" alt="" /></span>
      <span>iDatag SaaS</span>
    </router-link>

    <section class="auth-panel">
      <div class="auth-copy">
        <p>企业门户登录</p>
        <h1>从账号进入你的企业工作台</h1>
        <span>使用邮箱登录，统一管理企业成员、订阅服务与资金记录。</span>
      </div>

      <div class="auth-card">
        <div v-if="mode === 'login'" class="auth-tabs" role="tablist" aria-label="登录方式">
          <button type="button" role="tab" :aria-selected="loginMethod === 'wechat'" :class="{ active: loginMethod === 'wechat' }" @click="loginMethod = 'wechat'">微信扫码</button>
          <button type="button" role="tab" :aria-selected="loginMethod === 'email'" :class="{ active: loginMethod === 'email' }" @click="loginMethod = 'email'">邮箱登录</button>
        </div>
        <div v-else class="auth-form-heading"><h2>{{ mode === 'register' ? '注册账号' : '找回密码' }}</h2><button type="button" @click="switchMode('login')">返回登录</button></div>
        <WechatLogin v-if="mode === 'login' && loginMethod === 'wechat'" />
        <form v-if="mode === 'login' && loginMethod === 'email'" @submit.prevent="submitLogin">
          <div class="form-field">
            <label for="loginUser">邮箱</label>
            <input id="loginUser" v-model.trim="loginForm.email" class="layui-input" type="email" maxlength="100" autocomplete="username" />
          </div>
          <div class="form-field">
            <label for="loginPassword">密码</label>
            <PasswordField id="loginPassword" v-model="loginForm.password" autocomplete="current-password" />
          </div>
          <button class="layui-btn portal-btn portal-btn-primary auth-submit" :disabled="submitting.login">
            {{ submitting.login ? '登录中...' : '登录门户' }}
          </button>
        </form>

        <div v-if="mode === 'login' && loginMethod === 'email'" class="auth-links"><button type="button" @click="switchMode('register')">注册账号</button><button type="button" @click="switchMode('forgot')">找回密码</button></div>
        <form v-if="mode === 'register'" @submit.prevent="submitRegister">
          <div class="form-field">
            <label for="regEmail">邮箱</label>
            <input id="regEmail" v-model.trim="registerForm.email" class="layui-input" type="email" maxlength="100" autocomplete="email" />
          </div>
          <div class="form-field">
            <label for="regCode">邮箱验证码</label>
            <div class="verification-row">
              <input id="regCode" v-model.trim="registerForm.emailCode" class="layui-input" inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
              <button v-region-loading="{ scopes: ['email-code'], compact: true }" type="button" class="code-button" :disabled="isEmailDisabled('register')" @click="sendCode('REGISTER', registerForm.email, 'register')">
                {{ codeButtonText('register') }}
              </button>
            </div>
          </div>
          <div class="form-field">
            <label for="regName">姓名</label>
            <input id="regName" v-model.trim="registerForm.realName" class="layui-input" autocomplete="name" />
          </div>
          <div class="form-field">
            <label for="regPassword">密码</label>
            <PasswordField id="regPassword" v-model="registerForm.password" autocomplete="new-password" />
          </div>
          <button class="layui-btn portal-btn portal-btn-primary auth-submit" :disabled="submitting.register">
            {{ submitting.register ? '注册中...' : '创建账号' }}
          </button>
        </form>

        <form v-else-if="mode === 'forgot'" @submit.prevent="submitForgot">
          <div class="form-field">
            <label for="forgotEmail">邮箱</label>
            <input id="forgotEmail" v-model.trim="forgotForm.email" class="layui-input" type="email" maxlength="100" autocomplete="email" />
          </div>
          <div class="form-field">
            <label for="forgotCode">邮箱验证码</label>
            <div class="verification-row">
              <input id="forgotCode" v-model.trim="forgotForm.emailCode" class="layui-input" inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
              <button v-region-loading="{ scopes: ['email-code'], compact: true }" type="button" class="code-button" :disabled="isEmailDisabled('forgot')" @click="sendCode('RESET_PASSWORD', forgotForm.email, 'forgot')">
                {{ codeButtonText('forgot') }}
              </button>
            </div>
          </div>
          <div class="form-field">
            <label for="forgotPassword">新密码</label>
            <PasswordField id="forgotPassword" v-model="forgotForm.password" autocomplete="new-password" />
          </div>
          <button class="layui-btn portal-btn portal-btn-primary auth-submit" :disabled="submitting.forgot">
            {{ submitting.forgot ? '重置中...' : '重置密码' }}
          </button>
        </form>

      </div>
    </section>
  </main>
</template>

<script>
import { forgetPassword, register, sendEmailCode } from '@/api/portal';
import { notifySuccess, notifyWarning } from '@/utils/notification';
import PasswordField from '@/components/PasswordField.vue';
import WechatLogin from '@/components/WechatLogin.vue';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const EMAIL_CODE_PATTERN = /^\d{6}$/;

export default {
  name: 'AuthPage',
  components: { PasswordField, WechatLogin },
  /**
   * 初始化登录、注册、找回密码三组表单，以及注册和找回场景彼此独立的邮箱发送与倒计时状态。
   */
  data() {
    return {
      mode: 'login',
      loginMethod: 'wechat',
      loginForm: { email: '', password: '' },
      registerForm: { email: '', emailCode: '', realName: '', password: '' },
      forgotForm: { email: '', emailCode: '', password: '' },
      submitting: { login: false, register: false, forgot: false },
      emailState: {
        register: { sending: false, seconds: 0, timer: null },
        forgot: { sending: false, seconds: 0, timer: null }
      }
    };
  },
  /**
   * 页面卸载时清理所有邮箱倒计时，避免离开认证页后定时器继续修改已销毁组件。
   */
  beforeUnmount() {
    Object.values(this.emailState).forEach(state => window.clearInterval(state.timer));
  },
  /** 门户注册链接可直接进入注册表单，不改变既有邮箱注册流程。 */
  created() { if (this.$route.query.mode === 'register') this.mode = 'register'; },
  methods: {
    /**
     * 在登录、注册和找回密码面板间切换，不清除用户已经填写的其他面板内容。
     */
    switchMode(mode) {
      this.mode = mode;
      if (mode === 'login') this.loginMethod = 'email';
    },
    /**
     * 判断邮箱是否符合邮箱地址格式，供发送验证码和提交校验共用。
     */
    isEmailValid(email) {
      return String(email || '').trim().length <= 100 && EMAIL_PATTERN.test(String(email || '').trim());
    },
    /**
     * 邮箱正在发送或处于冷却倒计时时禁用对应场景按钮。
     */
    isEmailDisabled(key) {
      const state = this.emailState[key];
      return state.sending || state.seconds > 0;
    },
    /**
     * 根据发送中、倒计时或可发送状态生成邮箱按钮文案。
     */
    codeButtonText(key) {
      const state = this.emailState[key];
      if (state.sending) return '发送中...';
      if (state.seconds > 0) return `${state.seconds}s 后重发`;
      return '获取邮箱验证码';
    },
    /**
     * 为指定邮箱场景启动后端建议时长的重发倒计时，启动前先清理旧计时器避免重复递减。
     */
    startCountdown(key, seconds) {
      const state = this.emailState[key];
      window.clearInterval(state.timer);
      state.seconds = Number(seconds) || 60;
      state.timer = window.setInterval(() => {
        state.seconds -= 1;
        if (state.seconds <= 0) {
          window.clearInterval(state.timer);
          state.timer = null;
        }
      }, 1000);
    },
    /**
     * 校验邮箱后按注册或找回场景申请邮箱验证码；成功才启动冷却倒计时，失败由请求层统一提示。
     */
    async sendCode(scene, email, key) {
      if (!email) {
        notifyWarning('请输入邮箱');
        return;
      }
      if (!this.isEmailValid(email)) {
        notifyWarning('请输入有效的邮箱地址');
        return;
      }
      const state = this.emailState[key];
      if (state.sending || state.seconds > 0) return;
      state.sending = true;
      try {
        const response = await sendEmailCode({ email, scene });
        notifySuccess(response.msg || '验证码已发送');
        this.startCountdown(key, response.data.retryAfterSeconds);
      } catch {
        // Request errors are displayed by the Axios interceptor.
      } finally {
        state.sending = false;
      }
    },
    /**
     * 按邮箱业务统一校验邮箱、六位验证码和至少八位密码；注册场景额外要求真实姓名。
     * 返回首个错误文案，空串表示可以提交。
     */
    validateEmailForm(form, requireName = false) {
      if (!form.email) return '请输入邮箱';
      if (!this.isEmailValid(form.email)) return '请输入有效的邮箱地址';
      if (!form.emailCode) return '请输入邮箱验证码';
      if (!EMAIL_CODE_PATTERN.test(form.emailCode || '')) return '请输入 6 位邮箱验证码';
      if (requireName && !form.realName) return '请输入姓名';
      if (!form.password) return requireName ? '请输入密码' : '请输入新密码';
      if (form.password.length < 8 || form.password.length > 64 || new TextEncoder().encode(form.password).length > 72) return '密码需为 8 至 64 位，且编码长度不超过 72 字节';
      return '';
    },
    /**
     * 校验门户邮箱和密码是否已填写，具体凭据正确性由后端认证。
     */
    validateLoginForm() {
      if (!this.isEmailValid(this.loginForm.email)) return '请输入有效的邮箱地址';
      if (!this.loginForm.password) return '请输入密码';
      return '';
    },
    /**
     * 校验并提交门户登录，通过 Vuex action 保存 token 和账号上下文后进入仪表盘。
     */
    async submitLogin() {
      const validationMessage = this.validateLoginForm();
      if (validationMessage) {
        notifyWarning(validationMessage);
        return;
      }
      this.submitting.login = true;
      try {
        await this.$store.dispatch('login', this.loginForm);
        this.$router.push('/portal/dashboard');
      } catch {
        // Request errors are displayed by the Axios interceptor.
      } finally {
        this.submitting.login = false;
      }
    },
    /**
     * 提交邮箱验证后的注册资料，成功时切回登录面板并自动带入新邮箱。
     */
    async submitRegister() {
      const validationMessage = this.validateEmailForm(this.registerForm, true);
      if (validationMessage) {
        notifyWarning(validationMessage);
        return;
      }
      this.submitting.register = true;
      try {
        const response = await register(this.registerForm);
        notifySuccess(response.msg || '注册成功');
        this.mode = 'login';
        this.loginMethod = 'email';
        this.loginForm.email = this.registerForm.email;
        this.registerForm.password = '';
        this.registerForm.emailCode = '';
      } catch {
        // Request errors are displayed by the Axios interceptor.
      } finally {
        this.submitting.register = false;
      }
    },
    /**
     * 使用邮箱验证码重置门户密码，成功后返回登录面板并回填邮箱。
     */
    async submitForgot() {
      const validationMessage = this.validateEmailForm(this.forgotForm);
      if (validationMessage) {
        notifyWarning(validationMessage);
        return;
      }
      this.submitting.forgot = true;
      try {
        const response = await forgetPassword(this.forgotForm);
        notifySuccess(response.msg || '密码已重置');
        this.mode = 'login';
        this.loginMethod = 'email';
        this.loginForm.email = this.forgotForm.email;
        this.forgotForm.password = '';
        this.forgotForm.emailCode = '';
      } catch {
        // Request errors are displayed by the Axios interceptor.
      } finally {
        this.submitting.forgot = false;
      }
    }
  }
};
</script>

<style scoped>
.auth-links,.auth-form-heading{display:flex;justify-content:space-between;align-items:center;gap:16px;margin:18px 0}
.auth-links button,.auth-form-heading button{border:0;background:none;color:#24795b;min-height:44px;cursor:pointer}
.auth-form-heading h2{margin:0;font-size:24px}
.auth-tabs button{min-height:56px!important;border:0!important;border-bottom:3px solid transparent!important;border-radius:0!important;background:transparent!important;font-size:20px;color:#737b82}
.auth-tabs button.active{border-bottom-color:#24a879!important;color:#182330!important}
button:focus-visible{outline:2px solid #24795b;outline-offset:3px}

.auth-page {
  display: flex;
  min-height: 100dvh;
  padding: 32px clamp(20px, 6vw, 80px);
  color: #fff;
}

.auth-brand {
  position: fixed;
  top: 28px;
  left: clamp(20px, 6vw, 80px);
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 800;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
}

.brand-mark img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.auth-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(400px, 480px);
  gap: 56px;
  width: 100%;
  max-width: 1160px;
  margin: auto;
  align-items: center;
}

.auth-copy p {
  color: #bbf7d0;
  font-weight: 700;
}

.auth-copy h1 {
  max-width: 620px;
  margin: 0 0 18px;
  font-size: clamp(38px, 5vw, 64px);
  line-height: 1.08;
}

.auth-copy span {
  display: block;
  max-width: 560px;
  color: #cbd5e1;
  font-size: 17px;
}

.auth-card {
  padding: 28px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.98);
  color: var(--portal-text);
  box-shadow: var(--portal-shadow);
}

.auth-tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-bottom: 22px;
}

.auth-tabs button {
  min-height: 40px;
  border: 1px solid var(--portal-border);
  border-radius: 6px;
  background: #f8fafc;
}

.auth-tabs button.active {
  border-color: var(--portal-accent);
  background: #e8f7ee;
  color: #166534;
  font-weight: 700;
}

.form-field {
  margin-bottom: 16px;
}

.verification-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 124px;
  gap: 10px;
}

.code-button {
  min-height: 38px;
  border: 1px solid #86efac;
  border-radius: 4px;
  background: #f0fdf4;
  color: #166534;
  font-weight: 600;
}

.code-button:disabled {
  border-color: var(--portal-border);
  background: #f8fafc;
  color: #94a3b8;
  cursor: not-allowed;
}

.auth-submit {
  width: 100%;
  margin-top: 6px;
}

@media (max-width: 420px) {
  .verification-row {
    grid-template-columns: minmax(0, 1fr) 112px;
  }
}

@media (max-width: 900px) {
  .auth-panel {
    grid-template-columns: 1fr;
    padding-top: 82px;
  }
}
</style>
