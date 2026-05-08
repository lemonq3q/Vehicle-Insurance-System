<template>
  <div class="container_body card">
    <div class="search_title">查询筛选</div>
    <div class="search">
      <div class="params_container">
        <div class="param_item">
          <span class="form_title">查询条件</span>
          <div class="form_content">
            <el-input v-model="selectParams.blurParam" placeholder="请输入工单编号、号牌号码、车架号或车主"/>
          </div>
        </div>
        <div class="param_item">
          <span class="form_title">出单商铺</span>
          <div class="form_content">
            <el-select
              style="width: 100%;"
              v-model="selectParams.createMerchantId"
              filterable
              clearable
              remote
              reserve-keyword
              placeholder="输入关键词选择出单商铺"
              :remote-method="getCreateMerchantOption"
              :loading="loading.createMerchant"
            >
              <el-option
                v-for="item in createMerchantOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
        </div>
        <div class="param_item">
          <span class="form_title">处理人员</span>
          <div class="form_content">
            <el-select
              style="width: 100%;"
              v-model="selectParams.handleUserId"
              filterable
              clearable
              remote
              reserve-keyword
              placeholder="输入关键词选择处理人员"
              :remote-method="getHandleUserOpton"
              :loading="loading.handleUser"
            >
              <el-option
                v-for="item in handleUserOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
        </div>
        <div class="param_item">
          <span class="form_title">投保区域</span>
          <div class="form_content">
            <AreaSelect v-model="selectParams.areaCode" placeholder="请选择投保区域" :multi="false"></AreaSelect>
          </div>
        </div>
        <div class="param_item">
          <span class="form_title">保险公司</span>
          <div class="form_content">
            <el-select
              style="width: 100%;"
              v-model="selectParams.insuranceId"
              filterable
              clearable
              remote
              reserve-keyword
              placeholder="输入关键词选择保险公司"
              :remote-method="getInsuranceCompanyOption"
              :loading="loading.insuranceCompany"
            >
              <el-option
                v-for="item in insuranceCompanyOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
        </div>
        <div class="radio_param_item">
          <span class="radio_form_title">跟进状态</span>
          <div class="radio_form_content">
            <el-radio-group v-model="selectParams.remindStatus">
              <el-radio value="">全部</el-radio>
              <el-radio value="0">未处理</el-radio>
              <el-radio value="1">已续保</el-radio>
              <el-radio value="2">已流失</el-radio>
            </el-radio-group>
          </div>
        </div>
      </div>
      <div class="operation_container">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>
    <div class="table_header">
      <div class="search_title">
        <span>查询结果</span>
      </div>
    </div>
    <div class="table_container">
      <el-table
        v-loading="loading.table"
        :data="tableData"
        stripe
        border
        style="width: 100%;"
        table-layout="auto"
        :header-cell-style="{ textAlign: 'center' }"
        :cell-style="{ textAlign: 'center' }"
      >
        <el-table-column prop="code" label="工单号"/>
        <el-table-column prop="vehicleId" label="车牌号/车架号"/>
        <el-table-column prop="finialOwnerName" label="车主"/>
        <el-table-column prop="createMerchant" label="提交店铺"/>
        <el-table-column prop="createUser" label="提交人"/>
        <!-- <el-table-column prop="areaCode" label="投保区域" show-overflow-tooltip /> -->
        <el-table-column prop="insuranceCompany" label="保险公司"/>
        <!-- <el-table-column prop="handleMerchant" label="处理机构"/> -->
        <el-table-column prop="handleUser" label="处理人"/>
        <el-table-column prop="createTime" label="创建时间"/>
        <el-table-column prop="updateTime" label="最后跟进时间"/>
        <el-table-column prop="finishTime" label="承保时间"/>
        <el-table-column label="续保状态">
          <template #default="scope">
            <span>{{ renderRemindStatus(scope.row.remindStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="followUpRes" label="续保备注" min-width="140"/>
        <el-table-column label="操作" min-width="240">
          <template #default="scope">
            <div class="table_button_container">
              <el-button size="small" type="default" @click="handleDetail(scope.$index, scope.row)">详情</el-button>
              <el-button size="small" type="success" @click="handleAccept(scope.$index, scope.row)">{{ scope.row.status == 1?'接单':'转移' }}</el-button>
              <el-button size="small" type="warning" @click="handleFollow(scope.$index, scope.row)">跟进</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination_container">
        <el-pagination
          size="small"
          v-model:current-page="page.pageNum"
          v-model:page-size="page.pageSize"
          :page-sizes="[5, 10, 25, 50, 100]"
          :background="true"
          layout="total, prev, pager, next, jumper, sizes"
          :total="page.total"
          @change="handlePaginationChange"
        />
      </div>
    </div>
  </div>

  <el-dialog v-model="dialog" title="变更处理人员">
    <el-form
      ref="acceptRef"
      style="max-width: 700px; margin: 20px auto;"
      :model="acceptOrderParams"
      status-icon
      :rules="acceptRules"
      label-width="auto"
      class="login_form"
    >
      <el-form-item prop="handleBy" label="处理人员">
        <el-select
          style="width: 100%;"
          v-model="acceptOrderParams.handleBy"
          filterable
          clearable
          remote
          reserve-keyword
          placeholder="输入关键词选择处理人员"
          :remote-method="getAcceptUserOption"
          :loading="loading.acceptUser"
        >
          <el-option
            v-for="item in acceptUserOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <div>
        <el-button type="primary" @click="handleAccpetSubmit(acceptRef)">更新</el-button>
      </div>
    </el-form>
  </el-dialog>

  <el-dialog v-model="followDialog" title="续保跟进">
    <el-form
      ref="followRef"
      style="max-width: 700px; margin: 20px auto;"
      :model="followParams"
      status-icon
      :rules="followRules"
      label-width="auto"
    >
      <el-form-item prop="remindStatus" label="续保状态">
        <el-radio-group v-model="followParams.remindStatus">
          <el-radio :value="1">已续保</el-radio>
          <el-radio :value="2">已流失</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item prop="followUpRes" label="续保备注">
        <el-input v-model="followParams.followUpRes" type="textarea" :rows="4" placeholder="可填写跟进备注（选填）" />
      </el-form-item>
      <div>
        <el-button type="primary" @click="handleFollowSubmit(followRef)">更新</el-button>
      </div>
    </el-form>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import AreaSelect from './AreaSelect.vue';
import { selectRenew, acceptWorkorder, updateNoCascade, selectRenewCount } from '@/api/workorder';
import { getCascadeArea } from '@/utils/ChinaCitys';
import { formatSecondTimestamp } from '@/utils/time';
import { useRouter } from 'vue-router';
import { selectDownstreamOption } from '@/api/downstream';
import { selectSystemUserOptions } from '@/api/user';
import { selectInusranceCompanyOptions } from '@/api/upstream';
import Message from '@/utils/message';
import Loading from '@/utils/loading';
import { useStore } from 'vuex';

const router = useRouter();
const store = useStore();

const page = reactive({
  pageSize: 10,
  pageNum: 1,
  total: 0
});

const loading = reactive({
  createMerchant: false,
  handleUser: false,
  insuranceCompany: false,
  acceptUser: false,
  table: false
})

const tableData = ref([]);
const dialog = ref(false);
const followDialog = ref(false);

const acceptRef = ref();
const followRef = ref();

const acceptOrderParams = reactive({
  id: undefined,
  handleBy: undefined,
  status: undefined
});

const followParams = reactive({
  id: undefined,
  remindStatus: undefined,
  followUpRes: ''
});

const selectParams = reactive({
  blurParam: '',
  createMerchantId: undefined,
  handleMerchantId: undefined,
  handleUserId: undefined,
  areaCode: [],
  insuranceId: undefined,
  remindStatus: ""
});

const createMerchantOptions = ref([]);
const handleUserOptions = ref([]);
const acceptUserOptions = ref([]);
const insuranceCompanyOptions = ref([]);

const acceptRules = {
  handleBy: [{ required: true, message: '请选择处理人员', trigger: 'change' }]
};

const followRules = {
  remindStatus: [{ required: true, message: '请选择续保状态', trigger: 'change' }]
};

const renderRemindStatus = (val) => {
  if (val === 1) return '已续保';
  if (val === 2) return '已流失';
  return '未处理';
}

onMounted(() => {
  getData();
});

const handlePaginationChange = () => {
  getData();
};

const handleSearch = () => {
  page.pageNum = 1;
  getData();
}

const handleReset = () => {
  selectParams.blurParam = '';
  selectParams.createMerchantId = undefined;
  selectParams.handleMerchantId = undefined;
  selectParams.handleUserId = undefined;
  selectParams.areaCode = [];
  selectParams.insuranceId = undefined;
  selectParams.remindStatus = "";
  page.pageNum = 1;
  getData();
}

const buildSearchParams = () => {
  return {
    blurParam: selectParams.blurParam,
    createMerchantId: selectParams.createMerchantId,
    handleMerchantId: selectParams.handleMerchantId,
    handleUserId: selectParams.handleUserId,
    areaCode: selectParams.areaCode[1],
    insuranceId: selectParams.insuranceId,
    remindStatus: selectParams.remindStatus === "" ? undefined : selectParams.remindStatus,
    pageSize: page.pageSize,
    pageNum: page.pageNum
  }
}

const getData = async () => {
  try{
    loading.table = true;
    let params = buildSearchParams();
    await selectRenew(params).then(res => {
      res = res.data;
      if(res.code == 200){
        page.total = res.data.total;
        buildTableData(res.data.table);
      }
    });
  }
  finally{
    loading.table = false;
  }
}

const buildTableData = (data) => {
  data.forEach(item => {
    item.areaCode = getCascadeArea(item.areaCode);
    item.createTime = formatSecondTimestamp(item.createTime, 'YYYY-MM-DD');
    item.updateTime = formatSecondTimestamp(item.updateTime, 'YYYY-MM-DD');
    item.finishTime = formatSecondTimestamp(item.finishTime, 'YYYY-MM-DD');
  });
  tableData.value = data;
}

const handleDetail = (index, row) => {
  sessionStorage.setItem('workorderDetailType', 'handle');
  sessionStorage.setItem('workorderId', row.id);
  router.push({
    path: '/home/detailWorkorder',
  });
};

const handleAccept = (index, row) => {
  dialog.value = true;
  acceptOrderParams.id = row.id;
  acceptOrderParams.handleBy = undefined;
  acceptOrderParams.status = row.status;
}

const handleAccpetSubmit = (formEl) => {
  if (!formEl) return;
  formEl.validate((valid) => {
    if(valid){
      handleAcceptUpdate();
    }
  });
}

const handleAcceptUpdate = async () => {
  try{
    Loading.open();
    await acceptWorkorder(acceptOrderParams).then(res => {
      res = res.data;
      if(res.code == 200){
        Message.success("更新成功");
        dialog.value = false;
        getData();
      }
    });
  }
  finally{
    Loading.close();
  }
}

const handleFollow = (index, row) => {
  followDialog.value = true;
  followParams.id = row.id;
  followParams.remindStatus = row.remindStatus === 1 || row.remindStatus === 2 ? row.remindStatus : undefined;
  followParams.followUpRes = row.followUpRes ?? '';
}

const handleFollowSubmit = (formEl) => {
  if (!formEl) return;
  formEl.validate((valid) => {
    if(valid){
      handleFollowUpdate();
    }
  });
}

const handleFollowUpdate = async () => {
  try{
    Loading.open();
    await updateNoCascade(followParams).then(res => {
      res = res.data;
      if(res.code == 200){
        Message.success("更新成功");
        followDialog.value = false;
        getData();
        refreshRenewCount();
      }
    });
  }
  finally{
    Loading.close();
  }
}

const refreshRenewCount = async () => {
  try{
    await selectRenewCount().then(res => {
      res = res.data;
      if (res.code === 200) {
        store.commit('notice/setRenewCount', res.data);
        const selfCount = res.data?.selfCount ?? 0;
        const allCount = res.data?.allCount;
        const hasMsg = selfCount > 0 || (allCount != null && allCount > 0);
        if (hasMsg) {
          let content = `你有 ${selfCount} 个工单30天内到期`;
          if (allCount != null) {
            content = `我的工单：${selfCount} 个；全部工单：${allCount} 个（30天内到期）`;
          }
          store.commit('notice/upsertNotice', {
            key: 'renew',
            title: '续保提醒',
            content,
            route: '/home/renewWorkorder',
            createdAt: Date.now()
          });
        } else {
          store.commit('notice/removeNotice', 'renew');
        }
      }
    });
  }
  catch{
    return;
  }
}

const getCreateMerchantOption = async (blurParam) => {
  try{
    loading.createMerchant = true;
    await selectDownstreamOption(blurParam).then(res=>{
      res = res.data;
      if(res.code == 200){
        createMerchantOptions.value = res.data.map(item => {
          return {
            label: `${item.code} ${item.name}`,
            value: item.id
          }
        });
      }
    });
  }
  finally{
    loading.createMerchant = false;
  }
}

const getHandleUserOpton = async (blurParam) => {
  try{
    loading.handleUser = true;
    await selectSystemUserOptions(blurParam).then(res=>{
      res = res.data;
      if(res.code == 200){
        handleUserOptions.value = res.data.map(item => {
          return {
            label: `${item.username} ${item.name}`,
            value: item.id
          }
        })
      }
    });
  }
  finally{
    loading.handleUser = false;
  }
}

const getAcceptUserOption = async (blurParam) => {
  try{
    loading.acceptUser = true;
    await selectSystemUserOptions(blurParam).then(res=>{
      res = res.data;
      if(res.code == 200){
        acceptUserOptions.value = res.data.map(item => {
          return {
            label: `${item.username} ${item.name}`,
            value: item.id
          }
        })
      }
    });
  }
  finally{
    loading.acceptUser = false;
  }
}

const getInsuranceCompanyOption = async (blurParam) => {
  try{
    loading.insuranceCompany = true;
    await selectInusranceCompanyOptions(blurParam).then(res=>{
      res = res.data;
      if(res.code == 200){
        insuranceCompanyOptions.value = res.data.map(item => {
          return {
            label: `${item.code} ${item.name}`,
            value: item.id
          }
        })
      }
    });
  }
  finally{
    loading.insuranceCompany = false;
  }
}
</script>

<style scoped>
.container_body :deep(.cell) {
  white-space: pre-line !important;
}
</style>

