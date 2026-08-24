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
        <div class="param_item">
          <span class="form_title">业务区域</span>
          <div class="form_content">
            <AreaSelect v-model="selectParams.businessArea" placeholder="请选择业务区域" :multi="false"></AreaSelect>
          </div>
        </div>
        <!-- <div class="param_item">
          <span class="form_title">业务渠道</span>
          <div class="form_content">
            <el-select
              style="width: 100%;"
              clearable
              v-model="selectParams.businessChannel"
              placeholder="请选业务渠道"
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
        <el-button type="primary" @click="handleAdd" v-if="isHasPerm('merchant:update')">添加上游机构</el-button>
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
        <el-table-column prop="code" label="机构编号"/>
        <el-table-column prop="name" label="机构名称"/>
        <el-table-column prop="type" label="类型"/>
        <el-table-column prop="location" label="所在地区" show-overflow-tooltip />
        <el-table-column prop="address" label="机构地址" show-overflow-tooltip />
        <el-table-column prop="businessArea" label="业务区域" show-overflow-tooltip />
        <!-- <el-table-column prop="businessChannel" label="业务渠道"/> -->
        <el-table-column prop="contact" label="联系人"/>
        <el-table-column prop="phone" label="联系电话"/>
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
    <MerchantBatchImportDialog ref="batchImportRef" business-type="upstream" @success="handleImportSuccess" />
  </div>

</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import AreaSelect from './AreaSelect.vue';
import { selectUpstream, deleteUpstream, getUpstreamExcel } from '@/api/upstream';
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
  businessChannel: []
});

/**

 * * 页面首次进入时按默认分页和空筛选条件加载上游渠道。

 */
onMounted(() => {
  getData();
});

/**

 * * 清空渠道关键字、所在地、业务区域和业务渠道筛选，但等待用户重新发起查询。

 */
const handleReset = () => {
  selectParams.blurParam = '';
  selectParams.location = [];
  selectParams.businessArea = [];
  selectParams.businessChannel = [];

}

/**

 * * 分页器改变页码或每页条数后，使用当前筛选条件重新获取列表。

 */
const handlePaginationChange = () => {
  getData();
};

/**

 * * 携带上游记录 ID 进入编辑页，使编辑页按 update 模式加载完整渠道资料。

 */
const handleEdit = (index, row) => {
  router.push({
    path: '/home/editUpstream',
    query: {
      type: 'update',
      id: row.id
    }
  });
};

/**

 * * 用户确认后删除上游渠道；请求期间展示全局遮罩，成功后刷新当前列表。

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
      await deleteUpstream(row.id).then(res => {
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

 * * 从第一页执行新筛选，避免沿用旧页码导致筛选结果看起来为空。

 */
const handleSearch = () => {
  // console.log(selectParams);
  page.pageNum = 1;
  getData();
}

/**

 * * 查询上游分页数据并转换为表格展示结构，finally 确保加载态在异常时也能恢复。

 */
const getData = async () => {
  try{
    tableLoading.value = true;
    let params = buildSearchParams();
    await selectUpstream(params).then(res => {
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

 * * 将级联选择器路径转换成后端需要的市级编码，并合并渠道筛选与分页参数。

 */
const buildSearchParams = () => {
  return {
    blurParam: selectParams.blurParam,
    // 选择器默认返回数组，0为一级菜单，1为二级菜单
    location: selectParams.location.length>0?selectParams.location[1]:null,
    businessArea: selectParams.businessArea.length>0?selectParams.businessArea[1]:null,
    businessChannel: selectParams.businessChannel,
    pageSize: page.pageSize,
    pageNum: page.pageNum
  }
}

/**

 * * 将地区编码、秒级创建时间及数组字段转换为用户可读文本后写入表格数据源。

 */
const buildTableData = (data) => {
  data.forEach(item => {
    item.location = getCascadeArea(item.location);
    item.createTime = formatSecondTimestamp(item.createTime);
    item.defaultAreaCode = getCascadeArea(item.defaultAreaCode);
    item.businessArea = item.businessArea.map(areaCode => getCascadeArea(areaCode));
    item.businessArea = item.businessArea.join(' , ');
    item.businessChannel = item.businessChannel.join(' , ');
  });
  tableData.value = data;
}

/**

 * * 以 add 模式进入上游编辑页，新建记录不携带现有渠道数据。

 */
const handleAdd = () => {
  router.push({
    path: '/home/editUpstream',
    query: {
      type: 'add',
      id: 0
    }
  });
}

/**
 * 批量导入至少写入一条上游机构后刷新第一页，使用户立即看到新增记录。
 */
const handleImportSuccess = () => {
  page.pageNum = 1;
  getData();
}

/**

 * * 使用与列表相同的筛选条件导出上游 Excel，并在下载完成或失败后关闭全局遮罩。

 */
const handleExport = async () => {
  try{
    Loading.open();
    let params = buildSearchParams();
    await getUpstreamExcel(params);
  }
  finally{
    Loading.close();
  }
}
</script>


<style scoped>

</style>
