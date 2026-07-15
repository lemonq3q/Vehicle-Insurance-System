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
        <div class="param_item">
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
        </div>
        <div class="radio_param_item">
          <span class="radio_form_title">状态</span>
          <div class="radio_form_content">
            <el-radio-group v-model="selectParams.status">
              <el-radio value="">全部</el-radio>
              <el-radio value="1">有效</el-radio>
              <el-radio value="0">无效</el-radio>
            </el-radio-group>
          </div>
        </div>
      </div>
      <div class="operation_container">
        <el-button type="primary" @click="handleAdd">添加用户</el-button>
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
        <el-table-column label="状态">
          <template #default="scope">
            <el-tag
              :type="scope.row.status == 1 ? 'success' : 'info'"
              disable-transitions
              >{{ scope.row.status == 1 ? '有效' : '无效' }}</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间"/>
        <el-table-column label="操作">
          <template #default="scope">
            <div class="table_button_container">
              <el-button size="small" type="primary" @click="handleEdit(scope.$index, scope.row)">编辑</el-button>
              <el-button size="small" @click="handleChangeStatus(scope.$index, scope.row)">{{ scope.row.status == 1 ? '禁用':'启用' }}</el-button>
              <el-button size="small" type="danger" @click="handleDelete(scope.$index, scope.row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:page-num="page.pageNum" v-model:page-size="page.pageSize"
        :total="page.total" @change="handlePaginationChange" />
    </div>
  </div>

</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { selectAllRole } from '@/api/role';
import { selectSystemUser, deleteUser,updateUser } from '@/api/user';
import { formatSecondTimestamp } from '@/utils/time';
import Message from '@/utils/message';
import { ElMessageBox } from 'element-plus';
import Loading from '@/utils/loading';
import AppPagination from '@/components/common/AppPagination.vue';

const tableLoading = ref(false);

const router = useRouter();

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
  status: ''
});

const loading = ref(false);

const handleChangeStatus = async (index, row) => {
  try{
    Loading.open();
    let updateData = {
      id: row.id,
      status: row.status == 1?0:1
    };
    await updateUser(updateData).then(res=>{
      res = res.data;
      if (res.code == 200){
        Message.success(`${row.status == 1?'禁用':'启用'}成功`);
        getData();
      }
    });
  }
  finally{
    Loading.close();
  }
}

const handlePaginationChange = () => {
  getData();
};

const handleEdit = (index, row) => {
  router.push({
    path: '/home/editSystemUser',
    query: {
      type: 'update',
      id: row.id
    }
  });
};

const handleAdd = () => {
  router.push({
    path: '/home/editSystemUser',
    query: {
      type: 'add',
    }
  });
}

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
    // console.log(params);
    await selectSystemUser(params).then(res=>{
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

onMounted(()=>{
  getRoleOption();
  getData();
});
</script>


<style scoped>

</style>
