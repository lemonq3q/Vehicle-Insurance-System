<template>
  <div class="container_body card" style="margin-bottom: 10px;">
    <div class="search_title">查询筛选</div>
    <div class="search" style="border: 0;">
      <div class="params_container">
        <div class="param_item">
          <span class="form_title">用户名称/手机号</span>
          <div class="form_content">
            <el-input v-model="selectParams.blurParam" placeholder="请输入名称名称、手机号"/>
          </div>
        </div>
        <!-- <div class="param_item">
          <span class="form_title">用户角色</span>
          <div class="form_content">
            <el-select
              style="width: 100%;"
              clearable
              v-model="selectParams.roleId"
              placeholder="请选择用户角色"
              :loading="loading"
            >
              <el-option
                v-for="item in roleOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
        </div> -->
      </div>
      <div class="operation_container">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>
  </div>

  <div class="container_body card">
    <div class="table_header">
      <div class="search_title">
        <span>查询结果</span>
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
        <el-table-column prop="name" label="用户名称"/>
        <el-table-column prop="username" label="手机号码"/>
        <el-table-column prop="email" label="邮箱"/>
        <el-table-column prop="roleName" label="用户角色"/>
        <el-table-column prop="createTime" label="创建时间"/>
        <el-table-column label="操作">
          <template #default="scope">
            <div class="table_button_container">
              <el-button size="small" type="primary" @click="handleApproval(scope.$index, scope.row)">审核通过</el-button>
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
import { selectAllRole } from '@/api/role';
import { selectNotApprovalUser, deleteUser, approvalUser} from '@/api/user';
import { formatSecondTimestamp } from '@/utils/time';
import Message from '@/utils/message';
import { ElMessageBox } from 'element-plus';
import Loading from '@/utils/loading';

const tableLoading = ref(false);

const page = reactive({
  pageSize: 10,
  pageNum: 1,
  total: 1000
});

const roleOptions = ref([]);

const tableData = ref([]);

const selectParams = ref({
  blurParam: '',
  roleId: undefined,
});

const loading = ref(false);

const handlePaginationChange = () => {
  getData();
};


const handleReset = () => {
  selectParams.value = {
    blurParam: '',
    merchantId: undefined,
    roleId: undefined,
    status: ''
  };
}


const getRoleOption = async () => {
  try{
    loading.value = true;
    await selectAllRole().then(res=>{
      res = res.data;
      if (res.code == 200){
        roleOptions.value = res.data.map(item => {
          return {
            label: item.name,
            value: item.id
          }
        });
        roleOptions.value = roleOptions.value.filter(item => item.label == 'admin' || item.label == '出单员');
      }
    });
  }
  finally{
    loading.value = false;
  }
}

const handleSearch = () => {
  page.pageNum = 1;
  getData();
}

const buildTableData = (data) => {
  tableData.value = data.map(element => {
    element.createTime = formatSecondTimestamp(element.createTime);
    element.gender = element.gender == 1 ? '男' : '女';
    return element;
  });
}

const buildSelectParams = () => {
  let data = selectParams.value;
  data.pageSize = page.pageSize;
  data.pageNum = page.pageNum;
  return data;
}

const getData = async () => {
  try{
    tableLoading.value = true;
    let params = buildSelectParams();
    await selectNotApprovalUser(params).then(res=>{
      res = res.data;
      if (res.code == 200){
        buildTableData(res.data.table);
        page.total = res.data.total;
      }
    });
  }
  finally{
    tableLoading.value = false;
  }
}

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
      await deleteUser(row.id).then(res => {
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

const handleApproval = async (index, row) => {
  try{
    Loading.open();
    await approvalUser(row.id).then(res=>{
      res = res.data;
      if (res.code == 200){
        Message.success("审核成功");
        getData();
      }
    });
  }
  finally{
    Loading.close();
  }
}

onMounted(()=>{
  getRoleOption();
  getData();
});
</script>


<style scoped>

</style>
