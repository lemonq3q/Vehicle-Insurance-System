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
          <span class="form_title">日期类型</span>
          <div class="form_content" style="display: flex;">
            <div class="form_content_left">
              <el-select
                class="left_select"
                style="width: 100%;"
                v-model="selectParams.dateType" 
                :options="dateTyepOptions" 
                placeholder=""
              />
            </div>
            <div class="form_content_right">
              <el-date-picker
               class="right_select"
                style="width: 100%;"
                v-model="selectParams.dateRange"
                type="daterange"
                unlink-panels
                range-separator="到"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                :shortcuts="shortcuts"
                :clearable="true"
              />
              <!-- <el-date-picker
                class="right_select"
                style="width: 100%;"
                v-model="selectParams.dateRange[1]"
                type="date"
                placeholder="选择一个时间（查询前30天工单）"
                :disabled-date="disabledDate"
                :size="size"
              /> -->
            </div>
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
        <!-- <div class="param_item">
          <span class="form_title">处理机构</span>
          <div class="form_content">
            <el-select
              style="width: 100%;"
              v-model="selectParams.handleMerchantId"
              filterable
              clearable
              remote
              reserve-keyword
              placeholder="输入关键词选择处理机构"
              :remote-method="getHandleMerchantOption"
              :loading="loading.handleMerchant"
            >
              <el-option
                v-for="item in handleMerchantOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
        </div> -->
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
          <span class="radio_form_title">工单状态</span>
          <div class="radio_form_content">
            <el-radio-group v-model="selectParams.status">
              <el-radio value="">全部</el-radio>
              <!-- <el-radio value="1">待处理</el-radio> -->
              <el-radio value="2">待报价</el-radio>
              <!-- <el-radio value="6">待支付</el-radio> -->
              <el-radio value="8">待承保</el-radio>
              <el-radio value="9">承保失败</el-radio>
              <el-radio value="10">承保成功</el-radio>
            </el-radio-group>
          </div>
        </div>
      </div>
      <div class="operation_container">
        <el-button type="primary" @click="handleAdd">新建工单</el-button>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>
    <div class="table_header">
      <div class="search_title">
        <span>查询结果</span>
      </div>
      <div class="header_right_button">
        <div class="output_button" @click="batchImportRef.open()" v-if="isHasPerm('workorder:update')">
          <i class="import_icon"></i>
          <span class="no_select">批量导入</span>
        </div>
        <div class="output_button" @click="handleExport">
          <i class="export_icon"></i>
          <span class="no_select">导出</span>
        </div>
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
      stripe border 
      style="width: 100%;" 
      table-layout="fixed"
      scrollbar-always-on
      :header-cell-style="{ textAlign: 'center' }"
      :cell-style="{ textAlign: 'center' }">
        <el-table-column prop="code" label="工单号" min-width="140"/>
        <el-table-column prop="vehicleId" label="车牌号/车架号" min-width="220"/>
        <el-table-column prop="finialOwnerName" label="车主" min-width="110"/>
        <el-table-column prop="createMerchant" label="提交店铺" min-width="240"/>
        <el-table-column prop="createUser" label="提交人" min-width="110"/>
        <el-table-column prop="areaCode" label="投保区域" min-width="240"/>
        <el-table-column prop="insuranceCompany" label="保险公司" min-width="220"/>
        <el-table-column prop="upstreamPolicyFeeDisplay" label="上游政策费用" min-width="140"/>
        <el-table-column prop="downstreamPolicyFeeDisplay" label="下游政策费用" min-width="140"/>
        <!-- <el-table-column prop="handleMerchant" label="处理机构"/> -->
        <el-table-column prop="handleUserDisplay" label="处理人" min-width="130"/>
        <el-table-column prop="workorderStatus" label="工单状态" min-width="110"/>
        <el-table-column prop="createTime" label="创建时间" min-width="180"/>
        <el-table-column prop="updateTime" label="最后跟进时间" min-width="180"/>
        <el-table-column prop="finishTime" label="承保时间" min-width="180"/>
        <el-table-column label="操作" fixed="right" width="180">
          <template #default="scope">
            <div class="table_button_container">
              <el-button size="small" type="default" @click="handleDetail(scope.$index, scope.row)">详情</el-button>
              <!-- <el-button size="small" type="primary" @click="handleUpdate(scope.$index, scope.row)">修改</el-button> -->
              <el-button size="small" type="success" @click="handleAccept(scope.$index, scope.row)">{{ scope.row.status == 1?'接单':'转移' }}</el-button>
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
  <el-drawer v-model="drawer" :with-header="false" size="40%" v-if="drawer">
    <EditBaseWorkorder :type=type :id="id" @refresh="handleRefresh"></EditBaseWorkorder>
  </el-drawer>
  <MerchantBatchImportDialog ref="batchImportRef" business-type="workorder" @success="handleImportSuccess" />
  <el-dialog v-model="dialog" title="变更处理人员">
    <el-form
      ref="acceptRef"
      style="max-width: 700px; margin: 20px auto;"
      :model="acceptOrderParams"
      status-icon
      :rules="rules"
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
</template>

<script setup>
import { useWorkorderListRefresh } from '@/utils/workorderNavigation';
import { buildWorkorderListDisplay } from '@/utils/workorderDisplay';
import { reactive, ref, onMounted } from 'vue';
import AreaSelect from './AreaSelect.vue';
import MerchantBatchImportDialog from './common/MerchantBatchImportDialog.vue';
import { isHasPerm } from '@/utils/authenticate';
import { selectWorkorder, getWorkorderExcel, acceptWorkorder } from '@/api/workorder';
import { getCascadeArea } from '@/utils/ChinaCitys';
import { formatSecondTimestamp, convertDateToSecondTimestamp } from '@/utils/time';
import {getLastDayRange, getLastWeekRange, getLastMonthRange, getLastYearRange, getTodayRange} from '@/utils/time';
// import { getLastMonthRange } from '@/utils/time';
import { useRouter } from 'vue-router';
import EditBaseWorkorder from './EditBaseWorkorder.vue';
import { selectDownstreamOption } from '@/api/downstream';
// import { selectUpstreamOption } from '@/api/upstream';
import { selectSystemUserOptions } from '@/api/user';
import { selectInusranceCompanyOptions } from '@/api/upstream';
import Message from '@/utils/message';
import Loading from '@/utils/loading';

const batchImportRef = ref();

/**
 * 工单管理页批量导入完成后回到第一页并重查当前工单列表。
 * 保留现有筛选条件，失败明细仍由共用导入弹窗展示，便于用户修正失败行。
 */
const handleImportSuccess = async () => {
  page.pageNum = 1;
  await getData();
};

const type = ref("add");
const id = ref(undefined);

const router = useRouter();
const page = reactive({
  pageSize: 10,
  pageNum: 1,
  total: 1000
});

const loading = reactive({
  createMerchant: false,
  handleMerchant: false,
  handleUser: false,
  insuranceCompany: false,
  acceptUser: false,
  table: false
})

const shortcuts = [
  {
    text: '当天',
    value: getTodayRange
  },
  {
    text: '前一天',
    value: getLastDayRange
  },
  {
    text: '前一周',
    value: getLastWeekRange
  },
  {
    text: '前一月',
    value: getLastMonthRange
  },
  {
    text: '前一年',
    value: getLastYearRange
  },
]

const tableData = ref([]);

const drawer = ref(false);
const dialog = ref(false);

const acceptRef = ref();

const acceptOrderParams = reactive({
  id: undefined,
  handleBy: undefined,
  status: undefined
});

const dateTyepOptions = [
  {
    value: "createTime",
    label: '创建时间',
  },
  {
    value: "updateTime",
    label: '跟进时间',
  },
  {
    value: "finishTime",
    label: '承保时间',
  },
]

const selectParams = reactive({
  blurParam: '',
  dateRange: getLastWeekRange(),
  dateType: "createTime",
  createMerchantId: undefined,
  handleMerchantId: undefined,
  handleUserId: undefined,
  areaCode: [],
  insuranceId: undefined,
  status: ""
});

// // 监听日期范围变化，更新开始日期,适用于单日期默认30天范围
// watch(
//   () => selectParams.dateRange[1],
//   (newVal) => {
//     if (newVal) {
//       const date = new Date(newVal)
//       date.setDate(date.getDate() - 30) // 减30天
//       selectParams.dateRange[0] = date
//     } else {
//       selectParams.dateRange[0] = null
//     }
//   },
//   { immediate: true }
// )

const createMerchantOptions = ref([]);

// const handleMerchantOptions = ref([]);

const handleUserOptions = ref([]);

const acceptUserOptions = ref([]);

const insuranceCompanyOptions = ref([]);



/**



 * * 工单总览挂载后按默认“最近一周”条件加载首屏数据。



 */
onMounted(() => {
  getData();
});

/**

 * * 保留筛选和分页状态刷新列表，供新增抽屉关闭或其他业务操作完成后同步最新工单状态。

 */
const handleRefresh = () => {
  getData();
}

/**

 * * 恢复工单总览默认查询口径：最近一周创建的全部工单，并清空机构、人员、地区和状态条件。

 */
const handleReset = () => {
  selectParams.blurParam = '';
  selectParams.dateRange = getLastWeekRange();
  selectParams.dateType = 'createTime';
  selectParams.createMerchantId = undefined,
  selectParams.handleMerchantId = undefined,
  selectParams.handleUserId = undefined,
  selectParams.areaCode = [],
  selectParams.insuranceId = undefined,
  selectParams.status = ""
}

/**

 * * 分页状态变化后沿用当前工单筛选条件重新查询。

 */
const handlePaginationChange = () => {
  getData();
};

/**

 * * 模糊查询可接单的系统人员，并组合姓名与账号作为弹窗选项；finally 保证远程加载状态复位。

 */
const getAcceptUserOption = async (blurParam) => {
  try{
    loading.acceptUser = true;
    await selectSystemUserOptions(blurParam).then(res=>{
      res = res.data;
      if (res.code == 200){
        acceptUserOptions.value = res.data.map(item => {
          return {
            label: `${item.realName} ${item.username}`,
            value: item.id
          }
        });
      }
    });
  }
  finally{
    loading.acceptUser = false;
  }
}

/**

 * * 打开接单弹窗并建立待提交状态：初始工单推进到状态 2，已处理工单保留当前状态供调整负责人。

 */
const handleAccept = (index, row) => {
  dialog.value = true;
  acceptOrderParams.id = row.id;
  if (row.status == 1){
    acceptOrderParams.handleBy = undefined;
    acceptOrderParams.status = 2;
  }
  else {
    acceptOrderParams.handleBy = row.undefined;
    acceptOrderParams.status = row.status;
  }
}


/**


 * * 模糊查询工单创建机构，使用机构编码和名称构建筛选下拉选项。


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

// const getHandleMerchantOption = async (blurParam) => {
//   loading.handleMerchant = true;
//   await selectUpstreamOption(blurParam).then(res=>{
//     res = res.data;
//     if(res.code == 200){
//       handleMerchantOptions.value = res.data.map(item => {
//         return {
//           label: `${item.code} ${item.name}`,
//           value: item.id
//         }
//       })
//     }
//   })
//   loading.handleMerchant = false;
// }

/**

 * * 模糊查询工单处理人员，供总览列表按负责人筛选。

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

 * * 模糊查询保险公司上游选项，供总览按承保公司限定工单范围。

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

/**

 * * 将详情页设置为可处理模式并缓存工单 ID，再进入统一工单详情页面。

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

 * * 新筛选从第一页开始执行，防止旧页码超过新结果页数。

 */
const handleSearch = () => {
  page.pageNum = 1;
  getData();
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
    console.log(params);
    await selectWorkorder(params).then(res => {
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

 * * 把日期选择器转换为秒级边界、地区级联路径提取为市级编码，并合并机构、人员、状态及分页条件。

 */
const buildSearchParams = () => {
  return {
    blurParam: selectParams.blurParam,
    beginTime: selectParams.dateRange && selectParams.dateRange[0] ? convertDateToSecondTimestamp(selectParams.dateRange[0]) : undefined,
    endTime: selectParams.dateRange && selectParams.dateRange[1] ? convertDateToSecondTimestamp(selectParams.dateRange[1]) : undefined,
    dateType: selectParams.dateType,
    createMerchantId: selectParams.createMerchantId,
    handleMerchantId: selectParams.handleMerchantId,
    handleUserId: selectParams.handleUserId,
    areaCode: selectParams.areaCode[1],
    insuranceId: selectParams.insuranceId,
    status: selectParams.status,
    pageSize: page.pageSize,
    pageNum: page.pageNum
  }
}

/**
 * 工单管理表格保留接口秒级时间的完整精度，并在日期与时分秒之间换行。
 * 空值仍显示为空白；表格单元格已有 pre-line 样式，可直接呈现两行且不改变接口数据。
 * @param {number} timestamp 工单列表接口返回的 Unix 秒时间戳。
 * @returns {string} 两行本地日期时间文本，或空字符串。
 */
const formatWorkorderTableTime = (timestamp) => formatSecondTimestamp(timestamp).replace(' ', '\n');

/**
 * 为工单管理列表组装地区、处理人、费用及三列完整时间的展示值。
 * 原始列表来自工单查询接口；仅转换当前表格行，不修改后端保存的时间精度。
 * @param {Array} data 工单列表接口返回的当前页数据。
 * @returns {void} 更新当前页的表格数据。
 */
const buildTableData = (data) => {
  data.forEach(item => {
    Object.assign(item, buildWorkorderListDisplay(item));
    item.areaCode = getCascadeArea(item.areaCode);
    item.createTime = formatWorkorderTableTime(item.createTime);
    item.updateTime = formatWorkorderTableTime(item.updateTime);
    item.finishTime = formatWorkorderTableTime(item.finishTime);
  });
  tableData.value = data;
}

/**

 * * 以新增模式打开工单抽屉，复用 EditBaseWorkorder 完成资料录入。

 */
const handleAdd = () => {
  type.value = 'add';
  drawer.value = true;
}

// const handleUpdate = (index, row) => {
//   type.value = 'update';
//   id.value = row.id;
//   drawer.value = true;
// }

/**

 * * 按当前列表完整筛选条件导出工单 Excel，并用全局遮罩覆盖文件生成与下载阶段。

 */
const handleExport = async () => {
  try{
    Loading.open();
    let params = buildSearchParams();
    await getWorkorderExcel(params);
  }
  finally{
    Loading.close();
  }
}

/**

 * * 在接单表单校验通过后提交负责人变更，缺少表单实例时直接终止。

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

 * * 保存接单人员和目标状态，成功后刷新总览列表；遮罩在所有结果路径中关闭。

 */
const handleAcceptUpdate = async () => {
  try{
    Loading.open();
    await acceptWorkorder(acceptOrderParams).then(res => {
      res = res.data;
      if(res.code == 200){
        Message.success("更新成功");
        getData();
      }
    });
  }
  finally{
    Loading.close();
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
