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

const tableLoading = ref(false);

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

onMounted(() => {
  getData();
});

const handleReset = () => {
  selectParams.blurParam = '';
  selectParams.location = [];
  selectParams.businessArea = [];
  selectParams.businessChannel = [];

}

const handlePaginationChange = () => {
  getData();
};

const handleEdit = (index, row) => {
  router.push({
    path: '/home/editUpstream',
    query: {
      type: 'update',
      id: row.id
    }
  });
};

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

const handleSearch = () => {
  // console.log(selectParams);
  page.pageNum = 1;
  getData();
}

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

const handleAdd = () => {
  router.push({
    path: '/home/editUpstream',
    query: {
      type: 'add',
      id: 0
    }
  });
}

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
