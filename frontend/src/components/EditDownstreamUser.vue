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
        <el-form-item label="身份证" prop="idNum">
          <el-input
          v-model="userInfo.idNum"
          class="responsive-input"
          placeholder="请输入身份证"
          />
        </el-form-item>
        <el-form-item label="用户名称" prop="name">
          <el-input
          v-model="userInfo.name"
          class="responsive-input"
          placeholder="请输入用户名称"
          />
        </el-form-item>
        <el-form-item prop="merchantId" label="所属商家">
          <el-select
            style="width: 100%;"
            v-model="userInfo.merchantId"
            filterable
            clearable
            remote
            reserve-keyword
            placeholder="输入关键词选择所属商家"
            :remote-method="getMerchantOption"
            :loading="merchantLoading"
          >
            <el-option
              v-for="item in merchantOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
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
// import { useRoute } from 'vue-router';
import { validateIdNum, validatePhoneNumber, validateText } from '@/utils/validate';
import { insertMerchantStaff, selectMerchantStaffById, selectMerchantStaffRoles, updateMerchantStaff } from '@/api/merchantStaff';
import Message from '@/utils/message';
import router from '@/router';
import { selectDownstreamOption } from '@/api/downstream';
import Loading from '@/utils/loading';

// const route = useRoute();
// const type = route.query.type;
// const id = route.query.id;

const type = sessionStorage.getItem("merchant_user_type");
const id = sessionStorage.getItem("merchant_user_id");
const defaultMerchantOption = sessionStorage.getItem("merchant_user_default_option")
  ? JSON.parse(sessionStorage.getItem("merchant_user_default_option"))
  : null;

const editRef = ref();

const merchantOptions = ref([]);

const roleOptions = ref([]);

// const loading = ref(false);
const roleLoading = ref(false);
const merchantLoading = ref(false);


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
  idNum: [
    { validator: validateIdNum, trigger: 'blur'}
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
  idNum: '',
  name: '',
  merchantId: undefined,
  roleId: undefined
});

const oriUserInfo = ref({});

onMounted(() => {
  if(defaultMerchantOption){
    merchantOptions.value.push(defaultMerchantOption);
    userInfo.merchantId = defaultMerchantOption.value;
  }
  if (type == 'update'){
    getDataById();
  }
  getRoleOption();
});

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

const addSubmit = async () => {
  try{
    Loading.open();
    let data = buildInsertData();
    await insertMerchantStaff(data).then(res=>{
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

const buildInsertData = () => {
  const data = { ...userInfo };
  return data;
}

const buildUserInfo = () => {
  userInfo.username = oriUserInfo.value.username;
  userInfo.name = oriUserInfo.value.name;
  userInfo.idNum = oriUserInfo.value.idNum;
  userInfo.roleId = oriUserInfo.value.roleId;
  userInfo.merchantId = oriUserInfo.value.merchantId;
  if (oriUserInfo.value.merchantCode != null && oriUserInfo.value.merchantCode != undefined && oriUserInfo.value.merchantCode != ''){
    let option = {
      label: `${oriUserInfo.value.merchantCode} ${oriUserInfo.value.merchantName}`,
      value: oriUserInfo.value.merchantId
    }
    merchantOptions.value = [option];
  }
}

const getDataById = async () => {
  try{
    Loading.open();
    await selectMerchantStaffById(id).then(res=>{
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

const updateSubmit = async () => {
  try{
    Loading.open();
    let data = buildInsertData();
    data.id = id;
    data.code = oriUserInfo.value.code;
    await updateMerchantStaff(data).then(res => {
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

const handleReset = () => {
  buildUserInfo();
}

const handleBack = () => {
  router.push('/home/downstreamUser');
}


const getRoleOption = async () => {
  try{
    roleLoading.value = true;
    await selectMerchantStaffRoles().then(res=>{
      res = res.data;
      if (res.code == 200){
        roleOptions.value = res.data.map(item => {
          let option = {
            label: item.name,
            value: item.id
          }
          return option;
        });
      }
    });
  }
  finally{
    roleLoading.value = false;
  }
}

const getMerchantOption = async (blurParam) => {
  try{
    merchantLoading.value = true;
    await selectDownstreamOption(blurParam).then(res=>{
      res = res.data;
      if(res.code == 200){
        merchantOptions.value = res.data.map(item => {
          return {
            label: `${item.code} ${item.name}`,
            value: item.id
          }
        });
      }
    });
  }
  finally{
    merchantLoading.value = false;
  }
}
</script>


<style scoped>

</style>
