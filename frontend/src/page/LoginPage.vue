<template>
  <div class="auth-container">
    <!-- 切换标签 -->
    <div class="auth-tabs">
      <div 
        class="auth-tab no_select" 
        :class="{ active: activeTab === 'login' }"
        @click="activeTab = 'login'"
      >
        登录
      </div>
      <div 
        class="auth-tab no_select" 
        :class="{ active: activeTab === 'forget' }"
        @click="activeTab = 'forget'"
      >
        找回密码
      </div>
    </div>

    <!-- 登录表单 -->
    <div class="auth-box" v-show="activeTab === 'login'">
      <el-form
        ref="loginRef"
        :model="loginForm"
        status-icon
        :rules="loginRules"
        class="auth-form"
      >
        <el-form-item prop="email">
          <el-input
            v-model.trim="loginForm.email"
            class="auth-input"
            placeholder="请输入邮箱" type="email" maxlength="100" autocomplete="username"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            class="auth-input"
            placeholder="请输入密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item class="auth-btn-item">
          <el-button 
            type="primary" 
            size="large" 
            class="auth-btn"
            @click="handleLogin(loginRef)" :loading="submitting.login"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 找回密码表单 -->
    <div class="auth-box" v-show="activeTab === 'forget'">
      <el-form
        ref="forgetRef"
        :model="forgetForm"
        status-icon
        :rules="forgetRules"
        class="auth-form"
        autocomplete="off"
      >
        <div style="position:absolute;left:-9999px;top:-9999px;z-index:-1;">
          <input type="text" name="fake-username" autocomplete="username">
          <input type="password" name="fake-password" autocomplete="password">
        </div>
        <el-form-item prop="email">
          <el-input
            v-model.trim="forgetForm.email"
            class="auth-input"
            placeholder="请输入绑定的邮箱"
            size="large"
            :prefix-icon="ElMessageIcon"
          />
        </el-form-item>

        <!-- 验证码 + 获取验证码按钮 -->
        <el-form-item prop="emailCode">
          <el-input
            v-model.trim="forgetForm.emailCode"
            class="auth-input"
            placeholder="请输入验证码"
            size="large"
            :prefix-icon="MessageBox"
          >
            <template #append>
              <el-button 
                @click="getCode" 
                :disabled="codeDisabled"
                size="large"
                v-loading="codeLoading"
              >
                {{ codeText }}
              </el-button>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="forgetForm.password"
            class="auth-input"
            placeholder="请输入新密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="forgetForm.confirmPassword"
            class="auth-input"
            placeholder="请确认新密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item class="auth-btn-item">
          <el-button 
            type="primary" 
            size="large" 
            class="auth-btn"
            @click="handleForgetPassword(forgetRef)" :loading="submitting.forgot"
          >
            确认重置密码
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onUnmounted } from 'vue';
import { User, Lock, MessageBox, Message as ElMessageIcon } from '@element-plus/icons-vue';
import { login, sendEmailCode, forgetPassword } from '@/api/login';
import Message from '@/utils/message';
import { useRouter } from 'vue-router';
import { useStore } from 'vuex';
import { objToJsonStr } from '@/utils/convert';
import Storage from '@/utils/storage';
import Loading from '@/utils/loading';

const router = useRouter();
const store = useStore();
const activeTab = ref('login');
const loginRef = ref();
const forgetRef = ref();
const loginForm = reactive({ email: '', password: '' });
const forgetForm = reactive({ email: '', emailCode: '', password: '', confirmPassword: '' });
const codeStates = reactive({ forgot: { sending: false, seconds: 0, timer: null } });
const submitting = reactive({ login: false, forgot: false });
const codeLoading = computed(() => codeStates.forgot.sending);
const codeDisabled = computed(() => codeStates.forgot.sending || codeStates.forgot.seconds > 0);
const codeText = computed(() => codeStates.forgot.seconds > 0 ? `${codeStates.forgot.seconds}s 后重发` : '获取邮箱验证码');

/**
 * 校验两端共用的邮箱身份，限制到账号表支持的 100 字符并拒绝空地址。
 */
const validateEmail = (rule, value, callback) => {
  callback(value && value.length <= 100 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim()) ? undefined : new Error('请输入有效的邮箱地址'));
};
/**
 * 重置密码使用共享账号的长度规则，避免 BCrypt 的 72 字节截断。
 */
const validatePassword = (rule, value, callback) => {
  callback(value && value.length >= 8 && value.length <= 64 && new TextEncoder().encode(value).length <= 72 ? undefined : new Error('密码需为 8 至 64 位，且编码长度不超过 72 字节'));
};
/**
 * 为指定表单验证重复密码，不向后端提交确认字段。
 */
const confirmRule = form => ({ validator: (rule, value, callback) => callback(value && value === form.password ? undefined : new Error('两次输入的密码不一致')), trigger: 'blur' });
const emailRules = [{ validator: validateEmail, trigger: 'blur' }];
const codeRules = [{ pattern: /^\d{6}$/, required: true, message: '请输入 6 位邮箱验证码', trigger: 'blur' }];
const passwordRules = [{ validator: validatePassword, trigger: 'blur' }];
const loginRules = { email: emailRules, password: [{ required: true, message: '请输入密码', trigger: 'blur' }] };
const forgetRules = { email: emailRules, emailCode: codeRules, password: passwordRules, confirmPassword: [confirmRule(forgetForm)] };

/**
 * 按场景调用真实邮件接口，发送成功后才启动独立冷却；错误由请求拦截器显示。
 */
const requestCode = async (key, form, scene) => {
  const state = codeStates[key];
  if (state.sending || state.seconds > 0) return;
  if (!form.email || form.email.length > 100 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) { Message.warning('请输入有效的邮箱地址'); return; }
  state.sending = true;
  try {
    const response = await sendEmailCode({ email: form.email, scene });
    Message.success(response.data.msg);
    state.seconds = response.data.data.retryAfterSeconds;
    clearInterval(state.timer);
    state.timer = setInterval(() => { if (--state.seconds <= 0) clearInterval(state.timer); }, 1000);
  } catch { /* 请求拦截器负责反馈；发送失败不进入倒计时。 */ }
  finally { state.sending = false; }
};
/**
 * 找回密码验证码只发到已有账号的邮箱。
 */
const getCode = () => requestCode('forgot', forgetForm, 'RESET_PASSWORD');

/**
 * 验证邮箱密码并建立车险会话，企业和套餐访问规则仍由服务端判定。
 */
const handleLogin = async formEl => {
  if (!formEl || submitting.login) return;
  if (!await formEl.validate().catch(() => false)) return;
  submitting.login = true; Loading.open();
  try {
    const response = await login(loginForm);
    const { token, user } = response.data.data;
    Storage.set('token', token, 60 * 60);
    localStorage.setItem('userInfo', objToJsonStr(user));
    store.commit('login/setToken', token); store.commit('login/setUser', user);
    await router.push('/home');
  } catch { /* 统一请求层显示错误，失败不建立登录态。 */ }
  finally { submitting.login = false; Loading.close(); }
};
/**
 * 验证邮箱所有权后重置共享账号密码，使用 POST 避免密码出现在 URL。
 */
const handleForgetPassword = async formEl => {
  if (!formEl || submitting.forgot) return;
  if (!await formEl.validate().catch(() => false)) return;
  submitting.forgot = true; Loading.open();
  try {
    const payload = { email: forgetForm.email, emailCode: forgetForm.emailCode, password: forgetForm.password };
    const response = await forgetPassword(payload);
    Message.success(response.data.msg);
    loginForm.email = forgetForm.email; activeTab.value = 'login';
    forgetForm.password = ''; forgetForm.confirmPassword = ''; forgetForm.emailCode = '';
  } catch { /* 请求失败保留输入，统一拦截器显示原因。 */ }
  finally { submitting.forgot = false; Loading.close(); }
};
/**
 * 离开页面时释放找回密码的计时器。
 */
onUnmounted(() => Object.values(codeStates).forEach(state => clearInterval(state.timer)));
</script>

<style scoped>
/* 整体容器 */
.auth-container {
  position: relative;
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

/* 背景模糊效果 */
.auth-container::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle at 15% 12%, rgba(34, 197, 94, .18), transparent 32%), linear-gradient(135deg, #07111f 0%, #0f172a 48%, #10223b 100%);
  z-index: -1;
}

/* 标签切换 */
.auth-tabs {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
  background: rgba(255, 255, 255, 0.9);
  padding: 8px;
  border-radius: 50px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.auth-tab {
  padding: 8px 25px;
  border-radius: 50px;
  cursor: pointer;
  font-size: 16px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.auth-tab.active {
  background: var(--insurance-primary);
  color: white;
}

/* 表单盒子 */
.auth-box {
  width: 100%;
  max-width: 450px;
  background: #fff;
  padding: 40px 30px;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(3, 10, 24, .24);
  backdrop-filter: blur(10px);
}

/* 表单样式 */
.auth-form {
  width: 100%;
}

/* 输入框样式 */
.auth-input {
  width: 100%;
  margin-bottom: 1px;
  border-radius: 8px;
}

/* 按钮区域 */
.auth-btn-item {
  margin-top: 20px;
}

.auth-btn {
  width: 100%;
  height: 50px;
  font-size: 16px;
  border-radius: 8px;
  background: var(--insurance-primary);
  border: none;
  transition: all 0.3s ease;
}

.auth-btn:hover {
  background: var(--insurance-primary-hover);
}

/* 响应式适配 */
@media (max-width: 500px) {
  .auth-box {
    padding: 30px 20px;
    margin: 0 10px;
  }
  .auth-tab {
    padding: 8px 15px;
  }
}
</style>
