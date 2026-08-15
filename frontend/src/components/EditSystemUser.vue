<template>
  <div class="container_body card">
    <div class="search_title" style="border-bottom: 1px solid #DCDFE6; padding-bottom: 10px;">基础信息</div>
      <el-form
        ref="editRef"
        style="max-width: 700px; margin: 20px auto;"
        :model="userInfo"
        status-icon
        :rules="rules"
        label-width="auto"
        class="login_form"
      >
        <el-form-item prop="username" label="手机号码">
          <el-input
          v-model="userInfo.username"
          class="responsive-input"
          placeholder="请输入手机号码"
          />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input
          v-model="userInfo.email"
          class="responsive-input"
          placeholder="请输入邮箱"
          />
        </el-form-item>
        <el-form-item label="用户名称" prop="name">
          <el-input
          v-model="userInfo.name"
          class="responsive-input"
          placeholder="请输入用户名称"
          />
        </el-form-item>
        <el-form-item prop="roleId" label="用户角色">
          <el-select
            style="width: 100%;"
            clearable
            v-model="userInfo.roleId"
            placeholder="请选择用户角色"
            :loading="roleLoading"
          >
            <el-option
              v-for="item in roleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
              :disabled="item.disabled"
            />
          </el-select>
        </el-form-item>
        <div>
          <el-button type="primary" @click="handleSubmit(editRef)">{{ type=='add'?'新增':'更新' }}</el-button>
          <el-button @click="handleReset" v-if="type=='update'">重置</el-button>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </el-form>
  </div>

</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { validatePhoneNumber, validateText, vaildateEmail } from '@/utils/validate';
import { insertUser, selectUserById, updateUser } from '@/api/user';
import Message from '@/utils/message';
import router from '@/router';
import { selectAllRole } from '@/api/role';
import Loading from '@/utils/loading';

const route = useRoute();
const type = route.query.type;
const id = route.query.id;

const editRef = ref();

const roleOptions = ref([]);

const roleLoading = ref(false);

const rules = reactive({
  username: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { validator: validatePhoneNumber, trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入用户名称', trigger: 'blur' },
    { min: 1, max: 100, message: '输入内容过长', trigger: 'blur' },
    { validator: validateText, trigger: 'blur' }
  ],
  email: [
    { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' },
    { validator: vaildateEmail, trigger: 'blur'}
  ],
  merchantId: [
    { required: true, message: '请选择所属商家', trigger: 'blur' }
  ],
  roleId: [
    { required: true, message: '请选择用户角色', trigger: 'blur' }
  ]
});

const userInfo = reactive({
  username: '',
  email: '',
  name: '',
  roleId: undefined
});

const oriUserInfo = ref({});

/**

 * * 页面初始化时始终加载可分配角色，更新模式额外读取用户资料回填表单。

 */
onMounted(() => {
  if (type == 'update'){
    getDataById();
  }
  getRoleOption();
});

/**

 * * 校验手机号、姓名、邮箱和角色后，根据页面模式新增或更新系统用户。

 */
const handleSubmit = (formEl) => {
  if (!formEl) return;
  formEl.validate((valid) => {
    if(valid){
      if (type == 'add'){
        addSubmit();
      }
      else {
        updateSubmit();
      }
    }
  });
};

/**

 * * 提交新系统用户资料，后端负责账号唯一性、初始密码及权限关系的最终校验。

 */
const addSubmit = async () => {
  try{
    Loading.open();
    let data = buildInsertData();
    await insertUser(data).then(res=>{
      res = res.data;
      if (res.code == 200){
        Message.success("添加成功");
      }
    });
  }
  finally{
    Loading.close();
  }
}

/**

 * * 复制当前用户表单形成独立请求体，避免接口处理过程中直接修改响应式对象。

 */
const buildInsertData = () => {
  const data = { ...userInfo };
  return data;
}

/**

 * * 从原始用户快照回填手机号、姓名、邮箱和角色，用于初次展示及重置操作。

 */
const buildUserInfo = () => {
  userInfo.username = oriUserInfo.value.username;
  userInfo.name = oriUserInfo.value.name;
  userInfo.email = oriUserInfo.value.email;
  userInfo.roleId = oriUserInfo.value.roleId;
}

/**

 * * 查询路由指定用户并保存原始快照，成功后建立可编辑表单。

 */
const getDataById = async () => {
  try{
    Loading.open();
    await selectUserById(id).then(res=>{
      res = res.data;
      if(res.code == 200){
        oriUserInfo.value = res.data;
        buildUserInfo();
      }
    });
  }
  finally{
    Loading.close();
  }
}

/**

 * * 在表单数据中补充原用户 ID 和业务编码后更新账号，保持用户身份不被重新创建。

 */
const updateSubmit = async () => {
  try{
    Loading.open();
    let data = buildInsertData();
    data.id = id;
    data.code = oriUserInfo.value.code;
    await updateUser(data).then(res => {
      res = res.data;
      if (res.code == 200){
        Message.success("更新成功");
      }
    });
  }
  finally{
    Loading.close();
  }
}

/**

 * * 从原始快照恢复用户表单，撤销尚未保存的资料和角色修改。

 */
const handleReset = () => {
  buildUserInfo();
}

/**

 * * 返回系统用户管理列表。

 */
const handleBack = () => {
  router.push('/home/userManagement');
}

/**

 * * 查询角色字典并转换为下拉选项，仅允许系统用户选择管理员或出单员角色。

 */
const getRoleOption = async () => {
  try{
    roleLoading.value = true;
    await selectAllRole().then(res=>{
      res = res.data;
      if (res.code == 200){
        roleOptions.value = res.data.map(item => {
          let option = {
            label: item.name,
            value: item.id
          }
          return option;
        });
        roleOptions.value = roleOptions.value.filter(item => item.label == 'admin' || item.label == '出单员');
      }
    });
  }
  finally{
    roleLoading.value = false;
  }
}

</script>


<style scoped>

</style>
