<template>
  <div class="container_body card">
    <div class="search_title">查询筛选</div>
    <div class="search">
      <div class="params_container">
        <div class="param_item">
          <span class="form_title">机构名称/编号</span>
          <div class="form_content">
            <el-input v-model="selectParams.blurParam" placeholder="请输入机构名称、编号"/>
          </div>
        </div>
        <div class="param_item">
          <span class="form_title">所在地区</span>
          <div class="form_content">
            <AreaSelect v-model="selectParams.location" placeholder="请选择所在地区" :multi="false"></AreaSelect>
          </div>
        </div>
        <!-- <div class="param_item">
          <span class="form_title">业务区域</span>
          <div class="form_content">
            <AreaSelect v-model="selectParams.businessArea" placeholder="请选择业务区域" :multi="false"></AreaSelect>
          </div>
        </div> -->
        <!-- <div class="param_item">
          <span class="form_title">所属渠道</span>
          <div class="form_content">
            <el-select
              style="width: 100%;"
              clearable
              v-model="selectParams.channel"
              placeholder="请选所属渠道"
            >
              <el-option
                v-for="item in channelOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
        </div> -->
      </div>
      <div class="operation_container">
        <el-button type="primary" @click="handleAdd" v-if="isHasPerm('merchant:update')">添加下游商家</el-button>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>
    <div class="table_header">
      <div class="search_title">
        <span>查询结果</span>
      </div>
      <div class="header_right_button">
        <div class="output_button" @click="batchImportRef.open()" v-if="isHasPerm('merchant:update')">
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
      <el-table
      v-loading="tableLoading" 
      :data="tableData" 
      stripe border 
      style="width: 100%;" 
      table-layout="auto"
      :header-cell-style="{ textAlign: 'center' }"
      :cell-style="{ textAlign: 'center' }">
        <el-table-column prop="code" label="商家编号"/>
        <el-table-column prop="name" label="商家名称"/>
        <el-table-column prop="type" label="商家类型"/>
        <el-table-column prop="location" label="所在地区"/>
        <el-table-column prop="address" label="商家地址"/>
        <el-table-column prop="bankAndCardNum" label="银行卡"/>
        <!-- <el-table-column prop="businessArea" label="业务区域" show-overflow-tooltip /> -->
        <!-- <el-table-column prop="channel" label="所属渠道"/> -->
        <el-table-column prop="storeManager" label="联系人"/>
        <el-table-column prop="createTime" label="创建时间"/>
        <el-table-column label="操作" v-if="isHasPerm('merchant:update')">
          <template #default="scope">
            <div class="table_button_container">
              <el-button size="small" type="primary" @click="handleEdit(scope.$index, scope.row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete(scope.$index, scope.row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:page-num="page.pageNum" v-model:page-size="page.pageSize"
        :total="page.total" @change="handlePaginationChange" />
    </div>
    <MerchantBatchImportDialog ref="batchImportRef" business-type="downstream" @success="handleImportSuccess" />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import AreaSelect from './AreaSelect.vue';
import { selectDownstream, deleteDownstream, getDownstreamExcel } from '@/api/downstream';
import { getCascadeArea } from '@/utils/ChinaCitys';
import { formatSecondTimestamp } from '@/utils/time';
import Message from '@/utils/message';
import { ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { isHasPerm } from '@/utils/authenticate';
import Loading from '@/utils/loading';
import AppPagination from '@/components/common/AppPagination.vue';
import MerchantBatchImportDialog from '@/components/common/MerchantBatchImportDialog.vue';

const tableLoading = ref(false);
const batchImportRef = ref();

const router = useRouter();
const page = reactive({
  pageSize: 10,
  pageNum: 1,
  total: 1000
});

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

const tableData = ref([]);

const selectParams = reactive({
  blurParam: '',
  location: [],
  businessArea: [],
  channel: []
});

/**

 * * 页面挂载后加载当前企业的下游机构首页数据。

 */
onMounted(() => {
  getData();
});

/**

 * * 清空下游机构的关键字、地区、业务区域和渠道筛选条件。

 */
const handleReset = () => {
  selectParams.blurParam = '';
  selectParams.location = [];
  selectParams.businessArea = [];
  selectParams.channel = [];

}

/**

 * * 保留当前筛选条件，根据分页器最新状态重新查询机构列表。

 */
const handlePaginationChange = () => {
  getData();
};

/**

 * * 携带机构 ID 进入下游机构编辑页，并明确使用更新模式。

 */
const handleEdit = (index, row) => {
  router.push({
    path: '/home/editDownstreamMerchant',
    query: {
      type: 'update',
      id: row.id
    }
  });
};

/**

 * * 二次确认后删除下游机构；成功后刷新列表，取消操作不产生任何状态变化。

 */
const handleDelete = (index, row) => {
  ElMessageBox.confirm(
    '确认要删除此数据?',
    '警告',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
  .then(async () => {
    try{
      Loading.open();
      await deleteDownstream(row.id).then(res => {
        res = res.data;
        if(res.code == 200){
          Message.success("删除成功");
          getData();
        }
      });
    }
    finally{
      Loading.close();
    }
  })
  .catch(() => {

  })
};

/**

 * * 将页码重置为第一页后按最新筛选条件查询下游机构。

 */
const handleSearch = () => {
  // console.log(selectParams);
  page.pageNum = 1;
  getData();
}

/**

 * * 获取下游机构分页结果、同步总数并转换表格字段，始终在结束时解除表格加载态。

 */
const getData = async () => {
  try{
    tableLoading.value = true;
    let params = buildSearchParams();
    await selectDownstream(params).then(res => {
      res = res.data;
      if(res.code == 200){
        page.total = res.data.total;
        buildTableData(res.data.table);
      }
    });
  }
  finally{
    tableLoading.value = false;
  }
}

/**

 * * 提取地区级联选择中的市级编码，并与渠道条件和分页状态组装为接口参数。

 */
const buildSearchParams = () => {
  return {
    blurParam: selectParams.blurParam,
    // 选择器默认返回数组，0为一级菜单，1为二级菜单
    location: selectParams.location.length>0?selectParams.location[1]:null,
    businessArea: selectParams.businessArea.length>0?selectParams.businessArea[1]:null,
    channel: selectParams.channel,
    pageSize: page.pageSize,
    pageNum: page.pageNum
  }
}

/**

 * * 将地区和创建时间转成展示文本，并把银行名称与卡号合并为同一表格单元。

 */
const buildTableData = (data) => {
  data.forEach(item => {
    item.location = getCascadeArea(item.location);
    item.createTime = formatSecondTimestamp(item.createTime);
    // item.defaultAreaCode = getCascadeArea(item.defaultAreaCode);
    // item.businessArea = item.businessArea.map(areaCode => getCascadeArea(areaCode));
    // item.businessArea = item.businessArea.join(' , ');
    item.bankAndCardNum = (item.bank || '') + '\n' + (item.bankCardNum || '');
  });
  tableData.value = data;
}

/**

 * * 进入下游机构新增页，不传入已有记录 ID。

 */
const handleAdd = () => {
  router.push({
    path: '/home/editDownstreamMerchant',
    query: {
      type: 'add',
      id: 0
    }
  });
}

/**
 * 下游或商户人员成功写入后刷新机构首页，确保弹窗关闭前后列表数据保持一致。
 */
const handleImportSuccess = () => {
  page.pageNum = 1;
  getData();
}

/**

 * * 按当前机构筛选条件导出 Excel，并用全局遮罩覆盖整个异步下载阶段。

 */
const handleExport = async () => {
  try{
    Loading.open();
    let params = buildSearchParams();
    await getDownstreamExcel(params);
  }
  finally{
    Loading.close();
  }
}

</script>


<style scoped>
.container_body :deep(.cell) {
  white-space: pre-line !important;
}
</style>
