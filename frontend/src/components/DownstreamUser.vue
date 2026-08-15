<template>
  <div class="container_body card">
    <div class="search_title">查询筛选</div>
    <div class="search">
      <div class="params_container">
        <div class="param_item">
          <span class="form_title">用户名称/手机号</span>
          <div class="form_content">
            <el-input v-model="selectParams.blurParam" placeholder="请输入名称名称、手机号"/>
          </div>
        </div>
        <div class="param_item">
          <span class="form_title">所属商家</span>
          <div class="form_content">
            <el-select
              style="width: 100%;"
              v-model="selectParams.merchantId"
              filterable
              clearable
              remote
              reserve-keyword
              placeholder="输入关键词选择所属商家"
              :remote-method="getMerchantOption"
              :loading="merchantLoading"
            >
              <el-option
                v-for="item in merchantOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
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
              :loading="roleLoading"
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
        <el-button type="primary" @click="handleAdd" v-if="isHasPerm('merchant:update')">添加用户</el-button>
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
        <el-table-column prop="name" label="用户名称"/>
        <el-table-column prop="username" label="手机号码"/>
        <el-table-column prop="idNum" label="身份证"/>
        <el-table-column prop="roleName" label="用户角色"/>
        <el-table-column prop="merchantName" label="所属商家"/>
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
        <el-table-column label="操作" v-if="isHasPerm('merchant:update')">
          <template #default="scope">
            <div class="table_button_container">
              <el-button size="small" type="primary" @click="handleEdit(scope.$index, scope.row)">编辑</el-button>
              <el-button size="small" @click="handleChangeStatus(scope.$index, scope.row)">{{ scope.row.status == 1 ? '禁用':'启用' }}</el-button>
              <el-button size="small" type="danger" @click="handleDelete(scope.$index, scope.row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination
        v-model:page-num="page.pageNum"
        v-model:page-size="page.pageSize"
        :total="page.total"
        @change="handlePaginationChange"
      />
    </div>
  </div>

</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { selectDownstreamOption } from '@/api/downstream';
import { getUserExcel } from '@/api/user';
import { deleteMerchantStaff, selectMerchantStaff, selectMerchantStaffRoles, updateMerchantStaff } from '@/api/merchantStaff';
import { formatSecondTimestamp } from '@/utils/time';
import Message from '@/utils/message';
import { ElMessageBox } from 'element-plus';
import { isHasPerm } from '@/utils/authenticate';
import Loading from '@/utils/loading';
import AppPagination from '@/components/common/AppPagination.vue';

const tableLoading = ref(false);

const router = useRouter();

const page = reactive({
  pageSize: 10,
  pageNum: 1,
  total: 1000
});

const merchantOptions = ref([]);

const roleOptions = ref([]);

const tableData = ref([]);

const selectParams = ref({
  blurParam: '',
  merchantId: undefined,
  roleId: undefined,
  status: ''
});

const merchantLoading = ref(false);
const roleLoading = ref(false);


/**


 * * 在启用和禁用之间切换机构员工，成功后重新查询列表以使用后端最终状态。


 */
const handleChangeStatus = async (index, row) => {
  try{
    Loading.open();
    let updateData = {
      id: row.id,
      status: row.status == 1?0:1
    };
    await updateMerchantStaff(updateData).then(res=>{
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

/**

 * * 页码或每页条数变化时保留现有机构、角色和状态筛选重新查询。

 */
const handlePaginationChange = () => {
  getData();
};

/**

 * * 将列表当前选中的机构选项写入 sessionStorage，新增或编辑页可直接回显机构而无需再次远程搜索。

 */
const saveMerchantDefaultOption = () => {
  const merchantId = selectParams.value.merchantId;
  if (merchantId) {
    const option = merchantOptions.value.find(item => item.value === merchantId);
    if (option) {
      sessionStorage.setItem("merchant_user_default_option", JSON.stringify(option));
    }
  }
};

/**

 * * 缓存更新模式、员工 ID 和当前机构选项，再进入机构员工编辑页。

 */
const handleEdit = (index, row) => {
  saveMerchantDefaultOption();
  sessionStorage.setItem('merchant_user_type', 'update');
  sessionStorage.setItem('merchant_user_id', row.id);
  router.push({
    path: '/home/editDownstreamUser',
  });
};

/**

 * * 缓存新增模式及列表预选机构后进入员工新增页。

 */
const handleAdd = () => {
  sessionStorage.setItem('merchant_user_type', 'add');
  saveMerchantDefaultOption();
  router.push({
    path: '/home/editDownstreamUser',
  });
}

/**

 * * 清空员工关键字、机构、角色和状态筛选条件。

 */
const handleReset = () => {
  selectParams.value = {
    blurParam: '',
    merchantId: undefined,
    roleId: undefined,
    status: ''
  };
}

/**

 * * 模糊查询下游机构并建立带编码和名称的远程选项。

 */
const getMerchantOption = async (blurParam) => {
  try{
    merchantLoading.value = true;
    await selectDownstreamOption(blurParam).then(res=>{
      res = res.data;
      if(res.code == 200){
        merchantOptions.value = res.data.map(item => {
          return {
            label: `${item.code} ${item.name}`,
            value: item.id
          }
        });
      }
    });
  }
  finally{
    merchantLoading.value = false;
  }
}

/**

 * * 查询机构员工可分配角色并转换为筛选选项。

 */
const getRoleOption = async () => {
  try{
    roleLoading.value = true;
    await selectMerchantStaffRoles().then(res=>{
      res = res.data;
      if (res.code == 200){
        roleOptions.value = res.data.map(item => {
          return {
            label: item.name,
            value: item.id
          }
        });
      }
    });
  }
  finally{
    roleLoading.value = false;
  }
}

/**

 * * 从第一页按最新条件查询机构员工，避免旧页码越界。

 */
const handleSearch = () => {
  page.pageNum = 1;
  getData();
}

/**

 * * 将员工秒级创建时间转换为完整日期时间后写入表格数据源。

 */
const buildTableData = (data) => {
  tableData.value = data.map(element => {
    element.createTime = formatSecondTimestamp(element.createTime);
    return element;
  });
}

/**

 * * 把当前分页状态合并到员工筛选条件中形成接口参数。

 */
const buildSelectParams = () => {
  let data = selectParams.value;
  data.pageSize = page.pageSize;
  data.pageNum = page.pageNum;
  return data;
}

/**

 * * 查询机构员工分页列表，同步总记录数并转换展示字段，异常时仍解除表格加载态。

 */
const getData = async () => {
  try{
    tableLoading.value = true;
    let params = buildSelectParams();
    console.log(params);
    await selectMerchantStaff(params).then(res=>{
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

/**

 * * 按当前员工筛选条件导出用户 Excel，并在下载完成后关闭全局遮罩。

 */
const handleExport = async () => {
  try{
    Loading.open();
    let params = buildSelectParams();
    await getUserExcel(params);
  }
  finally{
    Loading.close();
  }
}

/**

 * * 经二次确认后删除机构员工，成功时刷新列表；取消确认不产生后端请求。

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
      await deleteMerchantStaff(row.id).then(res => {
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

 * * 页面挂载时加载角色选项和员工首屏数据，机构选项按用户输入延迟查询。

 */
onMounted(()=>{
  getRoleOption();
  getData();
});
</script>


<style scoped>

</style>
