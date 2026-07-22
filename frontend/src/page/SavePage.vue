<template>
  <div class="auth-container">
    <!-- 切换标签 -->
    <div class="auth-tabs">
      <div 
        class="auth-tab" 
        :class="{ active: activeTab === 'login' }"
        @click="activeTab = 'login'"
      >
        登录
      </div>
      <div 
        class="auth-tab" 
        :class="{ active: activeTab === 'register' }"
        @click="activeTab = 'register'"
      >
        注册
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
        <!-- <el-form-item prop="idNum">
          <el-input
            v-model="registerForm.idNum"
            class="auth-input"
            placeholder="请输入身份证号"
            size="large"
            :prefix-icon="Grid"
          />
        </el-form-item> -->
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
        <!-- <el-form-item>
          <el-radio-group v-model="registerForm.roleName">
              <el-radio value="admin">admin</el-radio>
              <el-radio value="出单员">出单员</el-radio>
            </el-radio-group>
        </el-form-item> -->
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
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { 
  User, Lock, Phone, UserFilled
} from '@element-plus/icons-vue';

import { login, register } from '@/api/login';
import Message from '@/utils/message';
import { useRouter } from 'vue-router';
import { validatePhoneNumber, validateStr, validateIdNum } from '@/utils/validate';
import { useStore } from 'vuex';
import { objToJsonStr } from '@/utils/convert';
import Storage from '@/utils/storage';
import Loading from '@/utils/loading';

// 路由实例
const router = useRouter();

const store = useStore();

// 切换标签
const activeTab = ref('login');

// 登录表单相关
const loginRef = ref();
const loginForm = reactive({
  username: '',
  password: ''
});

// 登录表单校验规则
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
  // idNum: '',
  password: '',
  confirmPassword: '',
  roleName: '出单员'
});

// 注册表单校验规则
const registerRules = reactive({
  username: [
    { required: true, message: '请输入手机号码', trigger: 'blur' },
    { validator: validatePhoneNumber, trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 1, max: 50, message: '输入内容过长', trigger: 'blur' }
  ],
  idNum: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { validator: validateIdNum, trigger: 'blur' }
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
        })
      }
      finally{
        Loading.close();
      }
    }
  });
};
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
  padding: 8px 30px;
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
    padding: 8px 20px;
  }
}
</style>
