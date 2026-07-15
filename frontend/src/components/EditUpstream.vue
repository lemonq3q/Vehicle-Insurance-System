<template>
  <div class="container_body card">
    <div class="search_title" style="border-bottom: 1px solid #DCDFE6; padding-bottom: 10px;">基础信息</div>
      <el-form
        ref="addUpstreamRef"
        style="max-width: 700px; margin: 20px auto;"
        :model="upstreamInfo"
        status-icon
        :rules="rules"
        label-width="auto"
        class="login_form"
      >
        <el-form-item prop="name" label="机构名称">
          <el-input
          v-model="upstreamInfo.name"
          class="responsive-input"
          placeholder="请输入机构名称"
          />
        </el-form-item>
        <el-form-item label="所在地区" prop="location">
          <AreaSelect style="width: 100%;" v-model="upstreamInfo.location" placeholder="请选择所在地区" :multi="false"></AreaSelect>
        </el-form-item>
        <!-- <el-form-item label="机构类型" prop="type">
          <el-radio-group v-model="upstreamInfo.type">
            <el-radio :value="'机构'">机构</el-radio>
            <el-radio :value="'保司'">保司</el-radio>
          </el-radio-group>
        </el-form-item> -->
        <el-form-item prop="address" label="机构地址">
          <el-input
          v-model="upstreamInfo.address"
          class="responsive-input"
          placeholder="请输入机构地址"
          />
        </el-form-item>
        <el-form-item prop="contact" label="联系人">
          <el-input
          v-model="upstreamInfo.contact"
          class="responsive-input"
          placeholder="请输入联系人"
          />
        </el-form-item>
        <el-form-item prop="phone" label="联系电话">
          <el-input
          v-model="upstreamInfo.phone"
          class="responsive-input"
          placeholder="请输入联系电话"
          />
        </el-form-item>
        <!-- <el-form-item prop="businessChannel" label="业务渠道">
          <el-select
            multiple
            style="width: 100%;"
            v-model="upstreamInfo.businessChannel" 
            :options="channelOptions" 
            clearable 
            placeholder="请选择业务渠道"
          />
        </el-form-item> -->
        <el-form-item prop="businessArea" label="业务区域">
          <AreaSelect style="width: 100%;" v-model="upstreamInfo.businessArea" placeholder="请选择业务区域" :multi="true"></AreaSelect>
        </el-form-item>
        <!-- <el-form-item prop="defaultAreaCode" label="默认区域">
          <el-cascader
            style="width: 100%;"
            v-model="upstreamInfo.defaultAreaCode" 
            :options="defaultAreaOption" 
            clearable 
            placeholder="请选择默认区域"
          />
        </el-form-item> -->
        <div>
          <el-button type="primary" @click="handleSubmit(addUpstreamRef)">{{ type=='add'?'新增':'更新' }}</el-button>
          <el-button @click="handleReset" v-if="type=='update'">重置</el-button>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </el-form>
  </div>

</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import AreaSelect from './AreaSelect.vue';
import { useRoute } from 'vue-router';
import { validatePhoneNumber, validateText } from '@/utils/validate';
// import { buildSelectorOptionByArray } from '@/utils/ChinaCitys';
import { insertUpstream, selectUpstreamById, updateUpstream } from '@/api/upstream';
import Message from '@/utils/message';
import { getCascadeAreaCode } from '@/utils/ChinaCitys';
import router from '@/router';
import Loading from '@/utils/loading';

const route = useRoute();
const type = route.query.type;
const id = route.query.id;

const addUpstreamRef = ref();

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
  contact: [
    { required: true, message: '请输入联系人', trigger: 'blur' },
    { min: 1, max: 100, message: '输入内容过长', trigger: 'blur' },
    { validator: validateText, trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { min: 1, max: 20, message: '输入内容过长', trigger: 'blur' },
    { validator: validatePhoneNumber, trigger: 'blur' }
  ],
  businessArea: [
    { required: true, message: '请选择业务区域', trigger: 'blur' }
  ],
  defaultAreaCode: [
    { required: true, message: '请设置默认区域', trigger: 'blur' }
  ]
});

const upstreamInfo = reactive({
  name: '',
  location: [],
  type: '机构',
  address: '',
  contact: '',
  phone: '',
  businessArea: [],
  // defaultAreaCode: []
});

const oriUpstreamInfo = ref({});

// const defaultAreaOption = computed(() => buildSelectorOptionByArray(upstreamInfo.businessArea));

onMounted(() => {
  if (type == 'update'){
    getDataById();
  }
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
    await insertUpstream(data).then(res=>{
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
  const data = { ...upstreamInfo };
  data.location = upstreamInfo.location[1];
  data.businessArea = [];
  upstreamInfo.businessArea.forEach(array => {
    data.businessArea.push(array[1]);
  });
  
  // data.defaultAreaCode = upstreamInfo.defaultAreaCode[1];
  // data.businessChannel = Object.values(upstreamInfo.businessChannel);
  return data;
}

const buildUpstreamInfo = () => {
  upstreamInfo.name = oriUpstreamInfo.value.name;
  upstreamInfo.type = oriUpstreamInfo.value.type;
  upstreamInfo.address = oriUpstreamInfo.value.address;
  upstreamInfo.contact = oriUpstreamInfo.value.contact;
  upstreamInfo.phone = oriUpstreamInfo.value.phone;
  // upstreamInfo.businessChannel = {};
  // oriUpstreamInfo.value.businessChannel.forEach((item, index) => {
  //   upstreamInfo.businessChannel[index] = item;
  // });
  upstreamInfo.location = getCascadeAreaCode(oriUpstreamInfo.value.location);
  upstreamInfo.businessArea = [];
  oriUpstreamInfo.value.businessArea.forEach(item=>{
    upstreamInfo.businessArea.push(getCascadeAreaCode(item));
  });
  // upstreamInfo.defaultAreaCode = getCascadeAreaCode(oriUpstreamInfo.value.defaultAreaCode);
}

const getDataById = async () => {
  try{
    Loading.open();
    await selectUpstreamById(id).then(res=>{
      res = res.data;
      if(res.code == 200){
        oriUpstreamInfo.value = res.data;
        buildUpstreamInfo();
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
    data.code = oriUpstreamInfo.value.code;
    await updateUpstream(data).then(res => {
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
  buildUpstreamInfo();
}

const handleBack = () => {
  router.push('/home/upstream');
}
</script>


<style scoped>

</style>
