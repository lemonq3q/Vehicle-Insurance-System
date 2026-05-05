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
        :class="{ active: activeTab === 'register' }"
        @click="activeTab = 'register'"
      >
        注册
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
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            class="auth-input"
            placeholder="请输入电话号码"
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
            @click="handleLogin(loginRef)"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 注册表单 -->
    <div class="auth-box" v-show="activeTab === 'register'">
      <el-form
        ref="registerRef"
        :model="registerForm"
        status-icon
        :rules="registerRules"
        class="auth-form"
        autocomplete="off"       
        data-form-type="other"
      >
        <!-- 骗浏览器把用户密码填到这里来 -->
        <div style="position:absolute;left:-9999px;top:-9999px;z-index:-1;">
          <input type="text" name="fake-username" autocomplete="username">
          <input type="password" name="fake-password" autocomplete="password">
        </div>
        <el-form-item prop="username">
          <el-input
            v-model="registerForm.username"
            class="auth-input"
            placeholder="请输入手机号码"
            size="large"
            :prefix-icon="Phone"
          />
        </el-form-item>
        <el-form-item prop="name">
          <el-input
            v-model="registerForm.name"
            class="auth-input"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="UserFilled"
          />
        </el-form-item>
        <el-form-item prop="email">
          <el-input
            v-model="registerForm.email"
            class="auth-input"
            placeholder="请输入邮箱"
            size="large"
            :prefix-icon="ElMessageIcon"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            class="auth-input"
            placeholder="请输入密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            class="auth-input"
            placeholder="请确认密码"
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
            @click="handleRegister(registerRef)"
          >
            注册
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
            v-model="forgetForm.email"
            class="auth-input"
            placeholder="请输入绑定的邮箱"
            size="large"
            :prefix-icon="ElMessageIcon"
          />
        </el-form-item>

        <!-- 验证码 + 获取验证码按钮 -->
        <el-form-item prop="code">
          <el-input
            v-model="forgetForm.code"
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
            @click="handleForgetPassword(forgetRef)"
          >
            确认重置密码
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onUnmounted } from 'vue';
import { 
  User, Lock, Phone, UserFilled, MessageBox, Message as ElMessageIcon
} from '@element-plus/icons-vue';

import { login, register, getSmsCode, forgetPassword } from '@/api/login';
import Message from '@/utils/message';
import { useRouter } from 'vue-router';
import { validatePhoneNumber, validateStr, vaildateEmail, judgeEmail } from '@/utils/validate';
import { useStore } from 'vuex';
import { objToJsonStr } from '@/utils/convert';
import Storage from '@/utils/storage';
import Loading from '@/utils/loading';

// 路由实例
const router = useRouter();
const store = useStore();

const codeLoading = ref(false);

// 切换标签
const activeTab = ref('login');

// 登录表单相关
const loginRef = ref();
const loginForm = reactive({
  username: '',
  password: ''
});
const loginRules = reactive({
  username: [
    { required: true, message: '请输入手机号码', trigger: 'submit' },
    { validator: validatePhoneNumber, trigger: 'submit' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'submit' },
    { min: 1, max: 16, message: '密码长度超出16位', trigger: 'submit' },
    { validator: validateStr, trigger: 'submit' }
  ]
});

// 注册表单相关
const registerRef = ref();
const registerForm = reactive({
  username: '',
  name: '',
  email: '',
  password: '',
  confirmPassword: '',
  roleName: '出单员'
});
const registerRules = reactive({
  username: [
    { required: true, message: '请输入手机号码', trigger: 'blur' },
    { validator: validatePhoneNumber, trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 1, max: 50, message: '输入内容过长', trigger: 'blur' }
  ],
  email: [
    { min: 0, max: 95, message: '输入内容过长', trigger: 'blur'},
    { validator: vaildateEmail, trigger: 'blur'}
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 16, message: '密码长度需在6-16位之间', trigger: 'blur' },
    { validator: validateStr, trigger: 'submit' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { 
      validator: (rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入的密码不一致'));
        } else {
          callback();
        }
      }, 
      trigger: 'blur' 
    }
  ]
});

// ==============================================
// 找回密码表单相关（新增）
// ==============================================
const forgetRef = ref();
const forgetForm = reactive({
  email: '',    
  code: '',          // 验证码
  password: '',      // 新密码
  confirmPassword: ''// 确认密码
});

// 验证码倒计时
const codeText = ref('获取验证码');
const codeDisabled = ref(false);
let codeTimer = null;

// 找回密码表单校验规则
const forgetRules = reactive({
  email: [
    { required: true, message: '请输入邮箱', trigger: 'submit' },
    { min: 1, max: 95, message: '输入内容过长', trigger: 'blur'},
    { validator: vaildateEmail, trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'submit' },
    { min: 4, max: 6, message: '验证码格式不正确', trigger: 'submit' }
  ],
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 16, message: '密码长度需在6-16位之间', trigger: 'blur' },
    { validator: validateStr, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { 
      validator: (rule, value, callback) => {
        if (value !== forgetForm.password) {
          callback(new Error('两次输入的新密码不一致'));
        } else {
          callback();
        }
      }, 
      trigger: 'blur' 
    }
  ]
});

// 获取验证码（60s倒计时）
const getCode = async () => {
  // 1. 先校验手机号
  if (!forgetForm.email || forgetForm.email == '' || !judgeEmail(forgetForm.email)) {
    Message.warning('请输入正确的邮箱');
    return;
  }
  codeDisabled.value = true;
  codeLoading.value = true;
  let flag = true;
  try{
    await getSmsCode(forgetForm.email).then(res => {
      res = res.data;
      if(res.code == 200){
        Message.success('验证码发送成功');
        flag = false;
      }
      else{
        Message.warning(res.msg);
      }
    });
  }
  finally{
    codeLoading.value = false;
    codeDisabled.value = false;
  }

  if(flag){
    return;
  }

  // 倒计时开始
  codeDisabled.value = true;
  let count = 60;
  codeText.value = `${count}s 后重新获取`;

  codeTimer = setInterval(() => {
    count--;
    codeText.value = `${count}s 后重新获取`;
    if (count <= 0) {
      clearInterval(codeTimer);
      codeText.value = '获取验证码';
      codeDisabled.value = false;
    }
  }, 1000);
};

// 找回密码提交
const handleForgetPassword = (formEl) => {
  if (!formEl) return;
  formEl.validate(async (valid) => {
    if (valid) {
      try {
        Loading.open();
        let data = {
          email: forgetForm.email,
          code: forgetForm.code,
          password: forgetForm.password
        };
        forgetPassword(data).then(res => {
          res = res.data;
          if(res.code == 200){
            Loading.close();
            Message.success('密码重置成功！');
            // 重置成功后跳回登录页
            activeTab.value = 'login';
            // 清空表单
            forgetForm.email  = '';
            forgetForm.code = '';
            forgetForm.password = '';
            forgetForm.confirmPassword = '';
          }
          else{
            Message.warning(res.msg);
          }
        });
      } finally {
        Loading.close();
      }
    }
  });
};

// 登录处理
const handleLogin = (formEl) => {
  if (!formEl) return;
  formEl.validate(async (valid) => {
    if(valid){
      try{
        Loading.open();
        await login(loginForm).then(res => {
          res = res.data;
          if (res.code == 200){
            Storage.set("token", res.data.token, 60 * 60);
            localStorage.setItem("userInfo", objToJsonStr(res.data.user));
            store.commit('login/setToken', res.data.token);
            store.commit('login/setUser', res.data.user);
            router.push('/home');
          }
          else if (res.code ==503){
            Message.warning("系统维护中，请稍后再试！");
          }
          else{
            Message.warning("账号或密码错误！");
          }
        })
      }
      finally{
        Loading.close();
      }
    }
  });
};

// 注册处理
const handleRegister = async (formEl) => {
  if (!formEl) return;
  formEl.validate(async (valid) => {
    if(valid){
      try{
        Loading.open();
        let registerData = {...registerForm};
        delete registerData.confirmPassword;
        await register(registerData).then(res => {
          res = res.data;
          if (res.code == 200){
            Message.success(res.msg);
          }
          else if (res.code ==503){
            Message.warning("系统维护中，请稍后再试！");
          }
          else{
            Message.warning(res.msg);
          }
        })
      }
      finally{
        Loading.close();
      }
    }
  });
};

// 销毁定时器
onUnmounted(() => {
  if (codeTimer) clearInterval(codeTimer);
});
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
  background-image: url('@/assets/login_background.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-attachment: fixed;
  filter: blur(8px);
  z-index: -1;
  transform: scale(1.05);
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
  background: #4289EC;
  color: white;
}

/* 表单盒子 */
.auth-box {
  width: 100%;
  max-width: 450px;
  background: rgba(255, 255, 255, 0.95);
  padding: 40px 30px;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
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
  background: #4289EC;
  border: none;
  transition: all 0.3s ease;
}

.auth-btn:hover {
  background: #3377e6;
  transform: translateY(-2px);
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