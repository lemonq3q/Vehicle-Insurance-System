<template>
  <div class="container_body">
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
                :clearable="false"
              />
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
        <div class="param_item">
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
              placeholder="输入关键词选择处理人员"
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
              <el-radio value="1">待处理</el-radio>
              <el-radio value="2">待报价</el-radio>
              <el-radio value="3">报价失败</el-radio>
              <el-radio value="4">待核保</el-radio>
              <el-radio value="5">核保失败</el-radio>
              <el-radio value="6">待支付</el-radio>
              <el-radio value="7">支付失败</el-radio>
              <el-radio value="8">已承保</el-radio>
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
        <div class="output_button" @click="handleExport">
          <i class="export_icon"></i>
          <span class="no_select">导出</span>
        </div>
      </div>
    </div>
    <div class="table_container">
      <el-table 
      :data="tableData" 
      stripe border 
      style="width: 100%;" 
      table-layout="auto"
      :header-cell-style="{ textAlign: 'center' }"
      :cell-style="{ textAlign: 'center' }">
        <el-table-column prop="code" label="工单号"/>
        <el-table-column prop="vehicleId" label="车牌号/车架号"/>
        <el-table-column prop="finialOwnerName" label="车主"/>
        <el-table-column prop="createMerchant" label="提交店铺"/>
        <el-table-column prop="createUser" label="提交人"/>
        <el-table-column prop="areaCode" label="投保区域" show-overflow-tooltip />
        <el-table-column prop="insuranceCompany" label="保险公司"/>
        <el-table-column prop="upstreamSumAmount" label="上游政策费用"/>
        <el-table-column prop="downstreamSumAmount" label="下游政策费用"/>
        <el-table-column prop="handleMerchant" label="处理机构"/>
        <el-table-column prop="handleUser" label="处理人"/>
        <el-table-column prop="workorderStatus" label="工单状态"/>
        <el-table-column prop="createTime" label="创建时间"/>
        <el-table-column prop="updateTime" label="最后跟进时间"/>
        <el-table-column prop="finishTime" label="承保时间"/>
        <el-table-column label="操作">
          <template #default="scope">
            <div class="table_button_container">
              <el-button size="small" type="default" @click="handleDetail(scope.$index, scope.row)">详情</el-button>
              <el-button size="small" type="primary" @click="handleUpdate(scope.$index, scope.row)">修改</el-button>
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
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import AreaSelect from './AreaSelect.vue';
import { selectWorkorder, getWorkorderExcel } from '@/api/workorder';
import { getCascadeArea } from '@/utils/ChinaCitys';
import { formatSecondTimestamp, convertDateToSecondTimestamp } from '@/utils/time';
import { useRouter } from 'vue-router';
import { getLastYearRange } from '@/utils/time';
import EditBaseWorkorder from './EditBaseWorkorder.vue';
import { selectDownstreamOption } from '@/api/downstream';
import { selectUpstreamOption } from '@/api/upstream';
import { selectUserOption } from '@/api/user';
import { selectInusranceCompanyOptions } from '@/api/upstream';

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
  insuranceCompany: false
})

const shortcuts = [
  {
    text: '前一天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 1)
      return [start, end]
    },
  },
  {
    text: '前一周',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 7)
      return [start, end]
    },
  },
  {
    text: '前一月',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 30)
      return [start, end]
    },
  },
  {
    text: '前一年',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 365)
      return [start, end]
    },
  },
]

const tableData = ref([]);

const drawer = ref(false);

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
  dateRange: getLastYearRange(),
  dateType: "createTime",
  createMerchantId: undefined,
  handleMerchantId: undefined,
  handleUserId: undefined,
  areaCode: [],
  insuranceId: undefined,
  status: ""
});

const createMerchantOptions = ref([]);

const handleMerchantOptions = ref([]);

const handleUserOptions = ref([]);

const insuranceCompanyOptions = ref([]);

onMounted(() => {
  getData();
});

const handleRefresh = () => {
  getData();
}

const handleReset = () => {
  selectParams.blurParam = '';
  selectParams.dateRange = getLastYearRange();
  selectParams.dateType = 'createTime';
  selectParams.createMerchantId = undefined,
  selectParams.handleMerchantId = undefined,
  selectParams.handleUserId = undefined,
  selectParams.areaCode = [],
  selectParams.insuranceId = undefined,
  selectParams.status = ""
}

const handlePaginationChange = () => {
  getData();
};

const getCreateMerchantOption = async (blurParam) => {
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
  loading.createMerchant = false;
}

const getHandleMerchantOption = async (blurParam) => {
  loading.handleMerchant = true;
  await selectUpstreamOption(blurParam).then(res=>{
    res = res.data;
    if(res.code == 200){
      handleMerchantOptions.value = res.data.map(item => {
        return {
          label: `${item.code} ${item.name}`,
          value: item.id
        }
      })
    }
  })
  loading.handleMerchant = false;
}

const getHandleUserOpton = async (blurParam) => {
  loading.handleUser = true;
  await selectUserOption(blurParam).then(res=>{
    res = res.data;
    if(res.code == 200){
      handleUserOptions.value = res.data.map(item => {
        return {
          label: `${item.username} ${item.name}`,
          value: item.id
        }
      })
    }
  })
  loading.handleUser = false;
}

const getInsuranceCompanyOption = async (blurParam) => {
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
  loading.insuranceCompany = false;
}

const handleDetail = (index, row) => {
  sessionStorage.setItem('workorderDetailType', 'look');
  router.push({
    path: '/home/detailWorkorder',
    query: {
      id: row.id
    }
  });
};

const handleSearch = () => {
  page.pageNum = 1;
  getData();
}

const getData = () => {
  let params = buildSearchParams();
  selectWorkorder(params).then(res => {
    res = res.data;
    if(res.code == 200){
      page.total = res.data.total;
      buildTableData(res.data.table);
    }
  });
}

const buildSearchParams = () => {
  return {
    blurParam: selectParams.blurParam,
    beginTime: convertDateToSecondTimestamp(selectParams.dateRange[0]),
    endTime: convertDateToSecondTimestamp(selectParams.dateRange[1]),
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

const buildTableData = (data) => {
  data.forEach(item => {
    item.areaCode = getCascadeArea(item.areaCode);
    item.createTime = formatSecondTimestamp(item.createTime);
    item.updateTime = formatSecondTimestamp(item.updateTime);
    item.finishTime = formatSecondTimestamp(item.finishTime);
  });
  console.log(data);
  tableData.value = data;
}

const handleAdd = () => {
  type.value = 'add';
  drawer.value = true;
}

const handleUpdate = (index, row) => {
  type.value = 'update';
  id.value = row.id;
  drawer.value = true;
}

const handleExport = () => {
  let params = buildSearchParams();
  getWorkorderExcel(params);
}
</script>


<style scoped>
.container_body :deep(.cell) {
  white-space: pre-line !important;
}
</style>
