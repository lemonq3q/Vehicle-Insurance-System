<template>
  <main class="auth-page portal-shell">
    <router-link class="auth-brand" to="/">
      <span class="brand-mark">XM</span>
      <span>小马e保 SaaS</span>
    </router-link>

    <section class="auth-panel">
      <div class="auth-copy">
        <p>企业门户登录</p>
        <h1>从账号进入你的企业工作台</h1>
        <span>使用手机号或账号登录，统一管理企业成员、订阅服务与资金记录。</span>
      </div>

      <div class="auth-card">
        <div class="auth-tabs">
          <button type="button" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</button>
          <button type="button" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</button>
          <button type="button" :class="{ active: mode === 'forgot' }" @click="switchMode('forgot')">找回密码</button>
        </div>

        <form v-if="mode === 'login'" @submit.prevent="submitLogin">
          <div class="form-field">
            <label for="loginUser">登录账号</label>
            <input id="loginUser" v-model="loginForm.username" class="layui-input" autocomplete="username" />
          </div>
          <div class="form-field">
            <label for="loginPassword">密码</label>
            <PasswordField id="loginPassword" v-model="loginForm.password" autocomplete="current-password" />
          </div>
          <button class="layui-btn portal-btn portal-btn-primary auth-submit" :disabled="submitting.login">
            {{ submitting.login ? '登录中...' : '登录门户' }}
          </button>
        </form>

        <form v-else-if="mode === 'register'" @submit.prevent="submitRegister">
          <div class="form-field">
            <label for="regPhone">手机号</label>
            <input id="regPhone" v-model.trim="registerForm.phone" class="layui-input" type="tel" inputmode="numeric" maxlength="11" autocomplete="tel" />
          </div>
          <div class="form-field">
            <label for="regCode">短信验证码</label>
            <div class="verification-row">
              <input id="regCode" v-model.trim="registerForm.smsCode" class="layui-input" inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
              <button type="button" class="code-button" :disabled="isSmsDisabled('register')" @click="sendCode('REGISTER', registerForm.phone, 'register')">
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

        <form v-else @submit.prevent="submitForgot">
          <div class="form-field">
            <label for="forgotPhone">手机号</label>
            <input id="forgotPhone" v-model.trim="forgotForm.phone" class="layui-input" type="tel" inputmode="numeric" maxlength="11" autocomplete="tel" />
          </div>
          <div class="form-field">
            <label for="forgotCode">短信验证码</label>
            <div class="verification-row">
              <input id="forgotCode" v-model.trim="forgotForm.smsCode" class="layui-input" inputmode="numeric" maxlength="6" autocomplete="one-time-code" />
              <button type="button" class="code-button" :disabled="isSmsDisabled('forgot')" @click="sendCode('RESET_PASSWORD', forgotForm.phone, 'forgot')">
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
import { forgetPassword, register, sendSmsCode } from '@/api/portal';
import { notifySuccess, notifyWarning } from '@/utils/notification';
import PasswordField from '@/components/PasswordField.vue';

const PHONE_PATTERN = /^1[3-9]\d{9}$/;
const SMS_CODE_PATTERN = /^\d{6}$/;

export default {
  name: 'AuthPage',
  components: { PasswordField },
  data() {
    return {
      mode: 'login',
      loginForm: { username: '', password: '' },
      registerForm: { phone: '', smsCode: '', realName: '', password: '' },
      forgotForm: { phone: '', smsCode: '', password: '' },
      submitting: { login: false, register: false, forgot: false },
      smsState: {
        register: { sending: false, seconds: 0, timer: null },
        forgot: { sending: false, seconds: 0, timer: null }
      }
    };
  },
  beforeUnmount() {
    Object.values(this.smsState).forEach(state => window.clearInterval(state.timer));
  },
  methods: {
    switchMode(mode) {
      this.mode = mode;
    },
    isPhoneValid(phone) {
      return PHONE_PATTERN.test(phone || '');
    },
    isSmsDisabled(key) {
      const state = this.smsState[key];
      return state.sending || state.seconds > 0;
    },
    codeButtonText(key) {
      const state = this.smsState[key];
      if (state.sending) return '发送中...';
      if (state.seconds > 0) return `${state.seconds}s 后重发`;
      return '获取验证码';
    },
    startCountdown(key, seconds) {
      const state = this.smsState[key];
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
    async sendCode(scene, phone, key) {
      if (!phone) {
        notifyWarning('请输入手机号');
        return;
      }
      if (!this.isPhoneValid(phone)) {
        notifyWarning('请输入正确的 11 位手机号');
        return;
      }
      const state = this.smsState[key];
      state.sending = true;
      try {
        const response = await sendSmsCode({ phone, scene });
        notifySuccess(response.msg || '验证码已发送');
        this.startCountdown(key, response.data.retryAfterSeconds);
      } catch {
        // Request errors are displayed by the Axios interceptor.
      } finally {
        state.sending = false;
      }
    },
    validateSmsForm(form, requireName = false) {
      if (!form.phone) return '请输入手机号';
      if (!this.isPhoneValid(form.phone)) return '请输入正确的 11 位手机号';
      if (!form.smsCode) return '请输入短信验证码';
      if (!SMS_CODE_PATTERN.test(form.smsCode || '')) return '请输入 6 位短信验证码';
      if (requireName && !form.realName) return '请输入姓名';
      if (!form.password) return requireName ? '请输入密码' : '请输入新密码';
      if (form.password.length < 8) return '密码长度不能少于 8 位';
      return '';
    },
    validateLoginForm() {
      if (!String(this.loginForm.username || '').trim()) return '请输入登录账号';
      if (!this.loginForm.password) return '请输入密码';
      return '';
    },
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
    async submitRegister() {
      const validationMessage = this.validateSmsForm(this.registerForm, true);
      if (validationMessage) {
        notifyWarning(validationMessage);
        return;
      }
      this.submitting.register = true;
      try {
        const response = await register(this.registerForm);
        notifySuccess(response.msg || '注册成功');
        this.mode = 'login';
        this.loginForm.username = this.registerForm.phone;
      } catch {
        // Request errors are displayed by the Axios interceptor.
      } finally {
        this.submitting.register = false;
      }
    },
    async submitForgot() {
      const validationMessage = this.validateSmsForm(this.forgotForm);
      if (validationMessage) {
        notifyWarning(validationMessage);
        return;
      }
      this.submitting.forgot = true;
      try {
        const response = await forgetPassword(this.forgotForm);
        notifySuccess(response.msg || '密码已重置');
        this.mode = 'login';
        this.loginForm.username = this.forgotForm.phone;
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
  border-radius: 8px;
  background: #22c55e;
  color: #052e16;
}

.auth-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 430px;
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
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  color: var(--portal-text);
  box-shadow: var(--portal-shadow);
}

.auth-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
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
