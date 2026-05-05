<template>
  <div class="container_body card">
    <div class="search_title" style="border-bottom: 1px solid #DCDFE6; padding-bottom: 10px;">基础信息</div>
      <el-form
        ref="editRef"
        style="max-width: 700px; margin: 20px auto;"
        :model="downstreamInfo"
        status-icon
        :rules="rules"
        label-width="auto"
        class="login_form"
      >
        <el-form-item prop="name" label="商家名称">
          <el-input
          v-model="downstreamInfo.name"
          class="responsive-input"
          placeholder="请输入商家名称"
          />
        </el-form-item>
        <el-form-item label="所在地区" prop="location">
          <AreaSelect style="width: 100%;" v-model="downstreamInfo.location" placeholder="请选择所在地区" :multi="false"></AreaSelect>
        </el-form-item>
        <el-form-item label="商家类型" prop="type">
          <el-radio-group v-model="downstreamInfo.type">
            <el-radio :value="'车商店铺'">车商店铺</el-radio>
            <el-radio :value="'汽修厂'">汽修厂</el-radio>
            <el-radio :value="'代理人'">代理人</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item prop="address" label="地址">
          <el-input
          v-model="downstreamInfo.address"
          class="responsive-input"
          placeholder="请输入地址"
          />
        </el-form-item>
        <el-form-item prop="bank" label="开户银行">
          <el-input
          v-model="downstreamInfo.bank"
          class="responsive-input"
          placeholder="请输入开户银行"
          />
        </el-form-item>
        <el-form-item prop="bankCardNum" label="银行卡号">
          <el-input
          v-model="downstreamInfo.bankCardNum"
          class="responsive-input"
          placeholder="请输入银行卡号"
          />
        </el-form-item>
        <!-- <el-form-item prop="channel" label="所属渠道">
          <el-select
            style="width: 100%;"
            v-model="downstreamInfo.channel" 
            :options="channelOptions" 
            clearable 
            placeholder="请选择所属渠道"
          />
        </el-form-item> -->
        <!-- <el-form-item prop="businessArea" label="业务区域">
          <AreaSelect style="width: 100%;" v-model="downstreamInfo.businessArea" placeholder="请选择业务区域" :multi="true"></AreaSelect>
        </el-form-item>
        <el-form-item prop="defaultAreaCode" label="默认区域">
          <el-cascader
            style="width: 100%;"
            v-model="downstreamInfo.defaultAreaCode" 
            :options="defaultAreaOption" 
            clearable 
            placeholder="请选择默认区域"
          />
        </el-form-item> -->
      </el-form>
    <div class="search_title" style="border-bottom: 1px solid #DCDFE6; padding-bottom: 10px;" v-if="type=='add'">默认联系人信息</div>
    <el-form
        ref="userRef"
        style="max-width: 700px; margin: 20px auto;"
        :model="userInfo"
        status-icon
        :rules="userRules"
        label-width="auto"
        class="login_form"
        v-if="type=='add'"
      >
        <el-form-item prop="username" label="手机号码">
          <el-input
          v-model="userInfo.username"
          class="responsive-input"
          placeholder="请输入手机号码"
          />
        </el-form-item>
        <el-form-item label="用户名称" prop="name">
          <el-input
          v-model="userInfo.name"
          class="responsive-input"
          placeholder="请输入用户名称"
          />
        </el-form-item>
      </el-form>
      <div>
        <el-button type="primary" @click="handleSubmit(editRef, userRef)">{{ type=='add'?'新增':'更新' }}</el-button>
        <el-button @click="handleReset" v-if="type=='update'">重置</el-button>
        <el-button @click="handleBack">返回</el-button>
      </div>
  </div>

</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import AreaSelect from './AreaSelect.vue';
import { useRoute } from 'vue-router';
import { validateBankCard, validateText, validatePhoneNumber, validateIdNum } from '@/utils/validate';
// import { buildSelectorOptionByArray } from '@/utils/ChinaCitys';
import { insertDownstream, selectDownstreamById, updateDownstream } from '@/api/downstream';
import Message from '@/utils/message';
import { getCascadeAreaCode } from '@/utils/ChinaCitys';
import router from '@/router';
import Loading from '@/utils/loading';

const route = useRoute();
const type = route.query.type;
const id = route.query.id;

const editRef = ref();
const userRef = ref();
// const channelOptions = reactive([
//   {
//     label: '车爵士',
//     value: '车爵士'
//   },
//   {
//     label: '洋芋好车',
//     value: '洋芋好车'
//   },
//   {
//     label: '易车-车销通',
//     value: '易车-车销通'
//   },
// ]);

const rules = reactive({
  name: [
    { required: true, message: '请输入机构名称', trigger: 'blur' },
    { min: 1, max: 100, message: '输入内容过长', trigger: 'blur' },
    { validator: validateText, trigger: 'blur' }
  ],
  location: [
    { required: true, message: '请选择所在地区', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择机构类型', trigger: 'blur' }
  ],
  address: [
    { min: 1, max: 100, message: '输入内容过长', trigger: 'blur' },
    { validator: validateText, trigger: 'blur' }
  ],
  // businessArea: [
  //   { required: true, message: '请选择业务区域', trigger: 'blur' }
  // ],
  defaultAreaCode: [
    { required: true, message: '请设置默认区域', trigger: 'blur' }
  ],
  bank: [
    { required: true, message: '请输入开户银行', trigger: 'blur' },
    { min: 1, max: 100, message: '输入内容过长', trigger: 'blur' },
    { validator: validateText, trigger: 'blur' }
  ],
  bankCardNum: [
    { required: true, message: '请输入银行卡号', trigger: 'blur' },
    { validator: validateBankCard, trigger: 'blur' }
  ]
});

const userRules = reactive({
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
  ]
});

const downstreamInfo = reactive({
  name: '',
  location: [],
  type: '车商店铺',
  address: '',
  bank: '',
  bankCardNum: '',
  // businessArea: [],
  // defaultAreaCode: []
});

const userInfo = reactive({
  username: '',
  name: '',
  idNum: ''
});

const oriInfo = ref({});

// const defaultAreaOption = computed(() => buildSelectorOptionByArray(downstreamInfo.businessArea));

onMounted(() => {
  if (type == 'update'){
    getDataById();
  }
});

const handleSubmit = (formEl1, formEl2) => {
  if (!formEl1) return;
  formEl1.validate((valid) => {
    if(valid){
      if (type == 'add'){
        if(!formEl2) return;
        formEl2.validate((valid) => {
          if(valid){
            addSubmit();
          }
        });
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
    data.user = {...userInfo};
    await insertDownstream(data).then(res=>{
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
  const data = { ...downstreamInfo };
  data.location = downstreamInfo.location[1];
  // data.businessArea = [];
  // downstreamInfo.businessArea.forEach(array => {
  //   data.businessArea.push(array[1]);
  // });
  // data.defaultAreaCode = downstreamInfo.defaultAreaCode[1];
  return data;
}

const buildDownstreamInfo = () => {
  downstreamInfo.name = oriInfo.value.name;
  downstreamInfo.type = oriInfo.value.type;
  downstreamInfo.address = oriInfo.value.address;
  downstreamInfo.bank = oriInfo.value.bank;
  downstreamInfo.bankCardNum = oriInfo.value.bankCardNum;
  downstreamInfo.location = getCascadeAreaCode(oriInfo.value.location);
  // downstreamInfo.businessArea = [];
  // oriInfo.value.businessArea.forEach(item=>{
  //   downstreamInfo.businessArea.push(getCascadeAreaCode(item));
  // });
  // downstreamInfo.defaultAreaCode = getCascadeAreaCode(oriInfo.value.defaultAreaCode);
}

const getDataById = async () => {
  try{
    Loading.open();
    await selectDownstreamById(id).then(res=>{
      res = res.data;
      if(res.code == 200){
        oriInfo.value = res.data;
        buildDownstreamInfo();
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
    data.code = oriInfo.value.code;
    console.log(data);
    await updateDownstream(data).then(res => {
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
  buildDownstreamInfo();
}

const handleBack = () => {
  router.push('/home/downstreamMerchant');
}
</script>


<style scoped>

</style>
