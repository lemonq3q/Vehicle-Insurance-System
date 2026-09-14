<template>
  <div class="card-container">
    <!-- 个人信息卡片 -->
    <el-card class="info-card">
      <template #header>
        <el-row justify="space-between" align="middle">
          <el-col>
            <span class="card-title">个人资料</span>
          </el-col>
          <el-col>
            <el-button type="primary" size="small" @click="showInfoDialog = true" link>
              <el-icon><Edit /></el-icon> 修改基本信息
            </el-button>
          </el-col>
        </el-row>
      </template>

      <el-row :gutter="20" class="info-content">
        <!-- 左侧头像区域 -->
        <el-col :span="6" class="avatar-col">
          <div class="avatar-wrapper">
            <el-avatar :size="150" class="user-avatar">
              <img src="https://cube.elemecdn.com/0/88/03b0d39583f4a807c0550858461710jpeg.jpeg" alt="用户头像">
            </el-avatar>
            <p class="avatar-tip">系统默认头像，暂不支持上传</p>
          </div>
        </el-col>

        <!-- 右侧信息展示区域 -->
        <el-col :span="18" class="info-display">
          <el-descriptions :column="2" border :size="'large'" class="info-desc">
            <el-descriptions-item label="用户姓名">{{ userInfo.name }}</el-descriptions-item>
            <el-descriptions-item label="手机号码">{{ userInfo.phone }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ userInfo.email }}</el-descriptions-item>
            <el-descriptions-item label="用户状态">{{ userInfo.status }}</el-descriptions-item>
            <el-descriptions-item label="用户角色">{{ userInfo.role }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ userInfo.createTime }}</el-descriptions-item>
          </el-descriptions>
        </el-col>
      </el-row>
    </el-card>

    <!-- 修改密码卡片 -->
    <el-card class="pwd-card" shadow="hover">
      <template #header>
        <el-row justify="space-between" align="middle">
          <el-col>
            <span class="card-title">密码管理</span>
          </el-col>
          <el-col>
            <el-button type="primary" size="small" @click="showPwdDialog = true" link>
              <el-icon><Lock /></el-icon> 修改密码
            </el-button>
          </el-col>
        </el-row>
      </template>
      <div class="pwd-tip">
        为了账户安全，建议定期修改密码，密码长度不少于6位，包含数字和字母组合。
      </div>
    </el-card>

    <!-- 修改基本信息弹窗 -->
    <el-dialog
      title="修改基本信息"
      v-model="showInfoDialog"
      width="800px"
      @close="resetInfoForm"
    >
      <el-form
        ref="infoFormRef"
        :model="editInfoForm"
        label-width="100px"
        :rules="infoRules"
        class="info-form"
      >
        <el-row>
          <el-col :span="12">
            <el-form-item label="用户姓名" prop="name">
              <el-input v-model="editInfoForm.name" placeholder="请输入姓名"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号码" prop="username">
              <el-input v-model="editInfoForm.username" placeholder="请输入手机号"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="editInfoForm.email" placeholder="请输入邮箱"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用户角色">
              <el-input v-model="editInfoForm.role" disabled placeholder="系统分配，不可修改"></el-input>
            </el-form-item>
          </el-col>
        </el-row>

      </el-form>
      <template #footer>
        <el-button @click="showInfoDialog = false">取消</el-button>
        <el-button type="primary" @click="saveInfo(infoFormRef)">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog
      title="修改密码"
      v-model="showPwdDialog"
      width="600px"
      @close="resetPwdForm"
    >
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        label-width="100px"
        :rules="pwdRules"
        class="pwd-form"
      >
        <!-- 骗浏览器把用户密码填到这里来 -->
        <div style="position:absolute;left:-9999px;top:-9999px;z-index:-1;">
          <input type="text" name="fake-username" autocomplete="username">
          <input type="password" name="fake-password" autocomplete="password">
        </div>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="pwdForm.password" type="password" show-password placeholder="请输入新密码"></el-input>
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPwdDialog = false">取消</el-button>
        <el-button type="primary" @click="changePassword(pwdFormRef)">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { Edit, Lock } from '@element-plus/icons-vue';
import { selectPersonalUser, updatePassword, updateUser} from '@/api/user';
import { formatSecondTimestamp } from '@/utils/time';
import Message from '@/utils/message';
import { validatePhoneNumber, vaildateEmail } from '@/utils/validate';
import Loading from '@/utils/loading';

// 弹窗控制
const showInfoDialog = ref(false);
const showPwdDialog = ref(false);

// 表单引用
const infoFormRef = ref(null);
const pwdFormRef = ref(null);

// 原始用户信息（展示用）
const userInfo = reactive({
  name: '',
  // gender: undefined,
  // age: undefined,
  // idCard: '',
  // createTime: null,
  phone: '',
  status: '',
  role: '', // 不可修改的字段
});

// 编辑用表单数据
const editInfoForm = reactive({});

const oriInfo = ref({});

// 修改密码表单
const pwdForm = reactive({
  password: '',
  confirmPassword: ''
});

// 信息校验规则
const infoRules = reactive({
  name: [
    { required: true, message: '请输入用户姓名', trigger: 'blur' },
    { min: 1, max: 45, message: '输入内容过长', trigger: 'blur' }
  ],
  email: [
    { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' },
    { validator: vaildateEmail, trigger: 'blur'}
  ],
  username: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { validator: validatePhoneNumber, trigger: 'blur' }
  ],
});

// 密码校验规则
const pwdRules = reactive({
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 16, message: '密码长度需要在6~16位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    {
      /**
       * * 比较两次密码输入并通过 Element Plus callback 返回校验结果，防止提交不一致的新密码。
       */
      validator: (rule, value, callback) => {
        if (value !== pwdForm.password) {
          callback(new Error('两次输入的密码不一致'));
        } else {
          callback();
        }
      },
      trigger: 'blur'
    }
  ]
});

// 生命周期 - 挂载时初始化编辑表单
/**
 * * 个人中心挂载后通过当前登录上下文读取用户资料，不接受页面传入任意用户 ID。
 */
onMounted(() => {
  getPersonalUser();
});


/**


 * * 查询当前账号个人资料，保存原始快照并同步展示区和编辑表单。


 */
const getPersonalUser = async () => {
  try{
    Loading.open();
    let res = await selectPersonalUser();
    res = res.data;
    if (res.code === 200) {
      oriInfo.value = res.data;
      buildInfo();
      resetInfoForm();
    }
  }
  finally{
    Loading.close();
  }
};

/**

 * * 将原始用户资料转换为个人中心展示值，包括状态、角色和格式化后的创建时间。

 */
const buildInfo = () => {
  userInfo.name = oriInfo.value.name;
  // userInfo.gender = oriInfo.value.gender == 1 ? '男' : '女';
  // userInfo.age = oriInfo.value.age;
  userInfo.phone = oriInfo.value.username;
  // userInfo.idCard = oriInfo.value.idNum;
  userInfo.status = oriInfo.value.status == 1 ? '正常' : '禁用';
  userInfo.role = oriInfo.value.roleName;
  userInfo.email = oriInfo.value.email;
  userInfo.createTime = formatSecondTimestamp(oriInfo.value.createTime);
}

// 重置编辑表单（同步原始数据）
/**
 * * 清除表单校验状态并从原始快照恢复姓名、手机号、邮箱和只读状态，撤销未保存修改。
 */
const resetInfoForm = () => {
  if (infoFormRef.value) {
    infoFormRef.value.resetFields();
  }
  editInfoForm.name = oriInfo.value.name;
  // editInfoForm.gender = String(oriInfo.value.gender);
  // editInfoForm.age = oriInfo.value.age;
  editInfoForm.username = oriInfo.value.username;
  editInfoForm.email = oriInfo.value.email;
  // editInfoForm.idNum = oriInfo.value.idNum;
  editInfoForm.status = oriInfo.value.status == 1 ? '正常' : '禁用';
};

/**

 * * 只提取个人中心允许修改的姓名、手机号和邮箱，并携带当前用户 ID，避免覆盖角色和状态。

 */
const buildInsertData = () => {
  return {
    id: oriInfo.value.id,
    name: editInfoForm.name,
    username: editInfoForm.username,
    email: editInfoForm.email,
  };
};

// 保存个人信息
/**
 * * 校验个人资料后提交更新，成功时重新查询后端数据并刷新展示区。
 */
const saveInfo = async (formEl) => {
  if (!formEl) return;
  formEl.validate(async (valid) => {
    if(valid){
      try{
        Loading.open();
        let data = buildInsertData();
        await updateUser(data).then(res => {
          res = res.data;
          if (res.code == 200){
            Message.success("更新成功");
            getPersonalUser();
          }
        });
      }
      finally{
        Loading.close();
      }
    }
  });
};

// 重置密码表单
/**
 * * 清除密码表单的校验状态及两次密码输入，避免弹窗再次打开时残留敏感信息。
 */
const resetPwdForm = () => {
  if (pwdFormRef.value) {
    pwdFormRef.value.resetFields();
  }
  pwdForm.password = '';
  pwdForm.confirmPassword = '';
};

// 修改密码
/**
 * * 校验密码长度和两次输入一致性后更新当前用户密码；请求体不包含其他个人资料字段。
 */
const changePassword = async (formEl) => {
  if (!formEl) return;
  formEl.validate(async (valid) => {
    if(valid){
      try{
        Loading.open();
        let user = {
          id: oriInfo.value.id,
          password: pwdForm.password
        }
        await updatePassword(user).then(res => {
          res = res.data;
          if (res.code == 200){
            Message.success('密码修改成功');
          }
        });
      }
      finally{
        Loading.close();
      }
    }
  });
};
</script>

<style scoped>

/* 卡片通用标题 */
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--insurance-text);
}

/* 个人信息卡片 */
.info-card {
  margin-bottom: 20px;
  border: 1px solid var(--insurance-border);
}

.info-content {
  align-items: flex-start;
  margin-top: 10px;
}

/* 头像区域样式 */
.avatar-col {
  display: flex;
  justify-content: center;
}

.avatar-wrapper {
  text-align: center;
}

.user-avatar {
  border: 4px solid #dbeafe;
}

.avatar-tip {
  margin-top: 12px;
  font-size: 12px;
  color: var(--insurance-muted);
}

/* 信息展示区域 */
.info-display {
  padding: 10px 0;
}

.info-desc {
  background-color: #fff;
}

/* 密码管理卡片 */
.pwd-card {
  border: 1px solid var(--insurance-border);
}

.pwd-tip {
  margin-top: 10px;
  font-size: 13px;
  color: var(--insurance-muted);
  line-height: 1.6;
}

/* 表单样式 */
.info-form, .pwd-form {
  width: 100%;
  margin-top: 10px;
}
</style>
