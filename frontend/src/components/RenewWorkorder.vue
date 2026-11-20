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
      <!--
        使用组件原生列最小宽度和固定布局，列宽总和超出容器时由表格内部横向滚动；
        操作列固定右侧，保留原有单元格换行与业务按钮，不再压缩姓名、区域和时间列。
      -->
      <el-table
        v-loading="loading.table"
        :data="tableData"
        stripe
        border
        style="width: 100%;"
        table-layout="fixed"
        scrollbar-always-on
        :header-cell-style="{ textAlign: 'center' }"
        :cell-style="{ textAlign: 'center' }"
      >
        <el-table-column prop="code" label="工单号" min-width="140"/>
        <el-table-column prop="vehicleId" label="车牌号/车架号" min-width="220"/>
        <el-table-column prop="finialOwnerName" label="车主" min-width="110"/>
        <el-table-column prop="createMerchant" label="提交店铺" min-width="240"/>
        <el-table-column prop="createUser" label="提交人" min-width="110"/>
        <!-- <el-table-column prop="areaCode" label="投保区域" show-overflow-tooltip /> -->
        <el-table-column prop="insuranceCompany" label="保险公司" min-width="220"/>
        <!-- <el-table-column prop="handleMerchant" label="处理机构"/> -->
        <el-table-column prop="handleUserDisplay" label="处理人" min-width="130"/>
        <el-table-column prop="createTime" label="创建时间" min-width="180"/>
        <el-table-column prop="updateTime" label="最后跟进时间" min-width="180"/>
        <el-table-column prop="finishTime" label="承保时间" min-width="180"/>
        <el-table-column label="提醒种类" min-width="190">
          <template #default="scope">
            <el-tag v-if="scope.row.commercialReminder" type="primary">商业险续保提醒</el-tag>
            <el-tag v-if="scope.row.compulsoryReminder" type="warning">交强险续保提醒</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="商业险状态" min-width="110">
          <template #default="scope">
            <el-tag :type="renewalStatusTagType(scope.row.commercialRenewalStatus)">{{ renderRemindStatus(scope.row.commercialRenewalStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="交强险状态" min-width="110">
          <template #default="scope">
            <el-tag :type="renewalStatusTagType(scope.row.compulsoryRenewalStatus)">{{ renderRemindStatus(scope.row.compulsoryRenewalStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="followUpRes" label="续保备注" min-width="200"/>
        <el-table-column label="操作" fixed="right" width="330">
          <template #default="scope">
            <div class="table_button_container">
              <el-button size="small" type="default" @click="handleDetail(scope.$index, scope.row)">详情</el-button>
              <el-button size="small" type="success" @click="handleFollow(scope.$index, scope.row)">跟进</el-button>
              <el-button size="small" type="danger" plain @click="handleDisableReminder(scope.row)">不再提醒</el-button>
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
      <el-form-item v-if="followParams.commercialInCycle" prop="commercialStatus" label="商业险处理">
        <el-radio-group v-model="followParams.commercialStatus">
          <el-radio :value="0">未处理</el-radio>
          <el-radio :value="1">已续保</el-radio>
          <el-radio :value="2">已流失</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="followParams.compulsoryInCycle" prop="compulsoryStatus" label="交强险处理">
        <el-radio-group v-model="followParams.compulsoryStatus">
          <el-radio :value="0">未处理</el-radio>
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
import { useWorkorderListRefresh } from '@/utils/workorderNavigation';
import { buildWorkorderListDisplay } from '@/utils/workorderDisplay';
import { buildRenewalFollowUpState, buildRenewalFollowUpRequest } from '@/utils/renewalFollowUp';
import { reactive, ref, onMounted, nextTick } from 'vue';
import AreaSelect from './AreaSelect.vue';
import { selectRenew, acceptWorkorder, updateRenewFollowUp, selectRenewCount, disableRenewReminder } from '@/api/workorder';
import { getCascadeArea } from '@/utils/ChinaCitys';
import { formatSecondTimestamp } from '@/utils/time';
import { useRouter } from 'vue-router';
import { selectDownstreamOption } from '@/api/downstream';
import { selectSystemUserOptions } from '@/api/user';
import { selectInusranceCompanyOptions } from '@/api/upstream';
import Message from '@/utils/message';
import Loading from '@/utils/loading';
import { useStore } from 'vuex';
import { ElMessageBox } from 'element-plus';

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
  commercialInCycle: false,
  compulsoryInCycle: false,
  commercialStatus: undefined,
  compulsoryStatus: undefined,
  followUpRes: '',
  originalCommercialStatus: undefined,
  originalCompulsoryStatus: undefined,
  originalFollowUpRes: ''
});

const selectParams = reactive({
  blurParam: '',
  createMerchantId: undefined,
  handleMerchantId: undefined,
  handleUserId: undefined,
  areaCode: [],
  insuranceId: undefined,
  remindStatus: "0"
});

const createMerchantOptions = ref([]);
const handleUserOptions = ref([]);
const acceptUserOptions = ref([]);
const insuranceCompanyOptions = ref([]);

const acceptRules = {
  handleBy: [{ required: true, message: '请选择处理人员', trigger: 'change' }]
};

const followRules = {
  commercialStatus: [{ required: true, message: '请选择商业险处理结果', trigger: 'change' }],
  compulsoryStatus: [{ required: true, message: '请选择交强险处理结果', trigger: 'change' }]
};

/**

 * * 将续保处理状态编码转换为列表文案；除已续保和已流失外统一视为未处理。

 */
const renderRemindStatus = (val) => {
  if (val === 1) return '已续保';
  if (val === 2) return '已流失';
  return '未处理';
}

/**
 * 按续保处理结果选择与提醒标签一致的状态标签色，未知值按未处理展示。
 * @param {number} val 服务端返回的险种状态。
 * @returns {string} Element Plus 标签类型。
 */
const renewalStatusTagType = (val) => val === 1 ? 'success' : val === 2 ? 'danger' : 'info';

/**

 * * 续保页面挂载后加载当前续保窗口内的工单首屏数据。

 */
onMounted(() => {
  getData();
});

/**

 * * 页码或每页条数变化时，使用当前续保筛选条件重新查询。

 */
const handlePaginationChange = () => {
  getData();
};

/**

 * * 从第一页执行新的续保客户筛选。

 */
const handleSearch = () => {
  page.pageNum = 1;
  getData();
}

/**

 * * 清空续保关键字、机构、负责人、地区、保险公司和处理状态，并立即重新查询首屏。

 */
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

/**

 * * 将地区级联值、续保处理状态和分页信息整理为续保专用查询参数；空状态不传给后端。

 */
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

/*
 * 记录最近一次实际提交的查询，返回后刷新时不把尚未点击查询的输入误当作新筛选。
 * 参数已由原buildSearchParams规范化，只保存当前页面内存副本，不写入浏览器存储。
 */
let lastSearchParams = null;
/**
 * 使用现有接口刷新当前分页，正常查询读取表单，详情保存后的恢复查询使用最近已提交的参数。
 * @param {boolean|number} restoreQuery 仅显式true复用参数，分页组件传入的数值事件仍按新页码查询。
 * @returns {Promise<void>} 查询及表格更新结束后完成，不清空未提交筛选输入或重置当前页码。
 */
const getData = async (restoreQuery = false) => {
  try{
    loading.table = true;
    let params = restoreQuery === true && lastSearchParams ? { ...lastSearchParams } : buildSearchParams();
    lastSearchParams = { ...params };
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

/**

 * * 将续保工单的地区与三个关键业务时间转换为列表可读文本。

 */
const buildTableData = (data) => {
  data.forEach(item => {
    Object.assign(item, buildWorkorderListDisplay(item));
    item.areaCode = getCascadeArea(item.areaCode);
    item.createTime = formatSecondTimestamp(item.createTime, 'YYYY-MM-DD');
    item.updateTime = formatSecondTimestamp(item.updateTime, 'YYYY-MM-DD');
    item.finishTime = formatSecondTimestamp(item.finishTime, 'YYYY-MM-DD');
  });
  tableData.value = data;
}

/**

 * * 缓存工单 ID 和可处理模式后进入详情页，续保人员可继续查看完整历史流程。

 */
const handleDetail = (index, row) => {
  sessionStorage.setItem('workorderDetailType', 'handle');
  sessionStorage.setItem('workorderId', row.id);
  router.push({
    path: '/home/detailWorkorder',
    query: { id: String(row.id), returnTo: router.currentRoute.value.fullPath }
  });
};


/**


 * * 校验续保工单负责人选择，只有表单通过时才执行接单更新。


 */
const handleAccpetSubmit = (formEl) => {
  if (!formEl) return;
  formEl.validate((valid) => {
    if(valid){
      handleAcceptUpdate();
    }
  });
}

/**

 * * 更新续保工单负责人和状态，成功后关闭弹窗并刷新列表。

 */
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

/**

 * * 按当前周期显示险种，已处理的本期结果也回填以便复核或修改；未到周期的险种不出现。

 */
const handleFollow = (index, row) => {
  const state = buildRenewalFollowUpState(row);
  if (!state.commercialInCycle && !state.compulsoryInCycle) {
    Message.warning('当前工单尚未进入续保提醒周期');
    return;
  }
  followDialog.value = true;
  Object.assign(followParams, state);
  nextTick(() => followRef.value?.clearValidate());
}

/**

 * * 校验续保结果必填规则，通过后保存跟进状态和说明。

 */
const handleFollowSubmit = (formEl) => {
  if (!formEl) return;
  formEl.validate((valid) => {
    if(valid){
      handleFollowUpdate();
    }
  });
}

/**

 * * 一次提交当前提醒周期内的各险种结果及备注，周期由后端根据起期计算；成功后同步列表和角标。

 */
const handleFollowUpdate = async () => {
  const request = buildRenewalFollowUpRequest(followParams);
  if (!request) {
    Message.info('续保状态和备注未发生变化');
    return;
  }
  try{
    Loading.open();
    await updateRenewFollowUp(followParams.id, request).then(res => {
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

/**
 * 经明确二次确认后永久关闭该工单后续周期的续保提醒。
 * 取消确认被视为正常退出；成功后同时刷新当前列表和页头统计，其他异常继续抛出。
 */
const handleDisableReminder = async (row) => {
  try {
    await ElMessageBox.confirm(
      `关闭后，工单“${row.code}”的商业险和交强险以后都不会再进入续保提醒，是否继续？`,
      '确认不再提醒',
      {
        confirmButtonText: '不再提醒',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
    Loading.open();
    const res = await disableRenewReminder(row.id);
    if (res.data.code === 200) {
      Message.success('已关闭该工单的续保提醒');
      await getData();
      await refreshRenewCount();
    }
  }
  catch (error) {
    if (error !== 'cancel' && error !== 'close') throw error;
  }
  finally {
    Loading.close();
  }
}

/**
 * 重新计算个人及企业续保数量，并按是否仍有待办新增、替换或移除全局续保通知。
 * 统计请求失败不会覆盖已有通知，也不会阻断当前续保操作结果展示。
 */
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
          let content = `你有 ${selfCount} 个工单进入续保窗口`;
          if (allCount != null) {
            content = `我的工单：${selfCount} 个；全部工单：${allCount} 个（当前续保窗口）`;
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

/**

 * * 远程查询续保工单创建机构，组合机构编码与名称作为筛选选项。

 */
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

/**

 * * 远程查询当前企业处理人员，供续保列表按负责人筛选。

 */
const getHandleUserOpton = async (blurParam) => {
  try{
    loading.handleUser = true;
    await selectSystemUserOptions(blurParam).then(res=>{
      res = res.data;
      if(res.code == 200){
        handleUserOptions.value = res.data.map(item => {
          return {
            label: `${item.username} ${item.realName}`,
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

/**

 * * 远程查询可被指定为续保负责人的系统用户，供接单弹窗选择。

 */
const getAcceptUserOption = async (blurParam) => {
  try{
    loading.acceptUser = true;
    await selectSystemUserOptions(blurParam).then(res=>{
      res = res.data;
      if(res.code == 200){
        acceptUserOptions.value = res.data.map(item => {
          return {
            label: `${item.username} ${item.realName}`,
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

/**

 * * 远程查询承保公司选项，供续保列表按原保险公司过滤。

 */
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
/*
 * 详情只读返回直接复用缓存；详情成功保存后仅重查最后已提交的筛选和页码，并恢复滚动位置。
 */
useWorkorderListRefresh(() => getData(true));
</script>

<style scoped>
.container_body :deep(.cell) {
  white-space: pre-line !important;
}
</style>
