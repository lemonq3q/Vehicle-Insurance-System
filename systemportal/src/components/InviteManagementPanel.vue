<template>
  <article class="portal-card panel invite-panel">
    <div class="invite-create-area">
      <div class="table-toolbar">
        <h2>企业邀请码</h2>
        <button class="layui-btn portal-btn portal-btn-primary" :disabled="creating" @click="createInvite">
          {{ creating ? '创建中' : '创建邀请码' }}
        </button>
      </div>
      <div class="invite-form">
        <div class="form-field">
          <label for="maxUseCount">邀请人数</label>
          <input id="maxUseCount" v-model.number="form.maxUseCount" class="layui-input" type="number" min="1" step="1" @blur="validateCount" />
          <p v-if="errors.maxUseCount" class="form-error">{{ errors.maxUseCount }}</p>
        </div>
        <div class="form-field">
          <label for="expiresAt">过期日期</label>
          <LayDatePicker v-model="form.expiresAt" placeholder="请选择邀请码过期日期" />
          <p v-if="errors.expiresAt" class="form-error">{{ errors.expiresAt }}</p>
        </div>
      </div>
    </div>

    <div class="invite-list-area">
      <h3>已创建的邀请码</h3>
      <div class="data-table-wrap">
        <table class="layui-table portal-table">
          <thead><tr><th>邀请码</th><th>默认角色</th><th>使用次数</th><th>过期日期</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in rows" :key="item.id">
              <td>{{ item.code }}</td>
              <td>{{ roleName(item.defaultRoleCode) }}</td>
              <td>{{ item.usedCount }} / {{ item.maxUseCount || '不限' }}</td>
              <td>{{ item.expiresAt }}</td>
              <td><span class="portal-tag">可用</span></td>
              <td><button class="layui-btn layui-btn-xs layui-btn-primary layui-border-red" @click="openDeleteDialog(item)">删除</button></td>
            </tr>
            <tr v-if="!rows.length"><td class="empty-cell" colspan="6">暂无邀请码</td></tr>
          </tbody>
        </table>
      </div>
      <LayPagination :total="total" :page-num="query.pageNum" :page-size="query.pageSize" @change="changePage" @size-change="changePageSize" />
    </div>

    <ConfirmDialog
      :visible="deleteDialog.visible"
      title="删除邀请码"
      :message="`确定删除邀请码“${deleteDialog.invite?.code || ''}”吗？删除后该邀请码将无法继续使用。`"
      confirm-text="确认删除"
      :loading="deleteDialog.loading"
      @cancel="closeDeleteDialog"
      @confirm="confirmDelete"
    />
  </article>
</template>

<script>
import { createInviteCode, deleteInviteCode, getInviteCodes } from '@/api/portal';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import LayDatePicker from '@/components/LayDatePicker.vue';
import LayPagination from '@/components/LayPagination.vue';
import { notifyWarning } from '@/utils/notification';
import { getRoleName } from '@/utils/portalLabels';

export default {
  name: 'InviteManagementPanel',
  components: { ConfirmDialog, LayDatePicker, LayPagination },
  /**
   * 管理邀请码分页列表、创建表单校验状态和删除确认框状态。创建与删除分别设置独立 loading，
   * 避免网络请求期间重复提交或关闭正在执行的危险操作。
   */
  data() {
    return {
      rows: [],
      total: 0,
      query: { pageNum: 1, pageSize: 5 },
      form: { maxUseCount: 5, expiresAt: '' },
      errors: { maxUseCount: '', expiresAt: '' },
      creating: false,
      deleteDialog: { visible: false, invite: null, loading: false }
    };
  },
  /**
   * 组件挂载到具备管理权限的企业页面后，加载当前页邀请码数据。
   */
  created() {
    this.loadData();
  },
  methods: {
    roleName: getRoleName,
    /**
     * 按当前分页条件读取企业邀请码，并将后端总数显式转换为数字供分页组件计算。
     */
    async loadData() {
      const response = await getInviteCodes(this.query);
      this.rows = response.data.table;
      this.total = Number(response.data.total || 0);
    },
    /**
     * 邀请人数必须是正整数，既限制无效额度，也避免小数经后端隐式取整造成实际可用次数歧义。
     */
    validateCount() {
      const count = Number(this.form.maxUseCount);
      this.errors.maxUseCount = Number.isInteger(count) && count > 0 ? '' : '邀请人数必须为大于 0 的整数';
      return !this.errors.maxUseCount;
    },
    /**
     * 联合校验邀请人数和过期日期。日期按所选当天 23:59:59 计算，使用户选择的日期整天有效，
     * 同时拒绝已经过期或无法解析的日期。
     */
    validateForm() {
      const countValid = this.validateCount();
      const expiresAt = String(this.form.expiresAt || '').trim();
      const expiresTime = expiresAt ? new Date(`${expiresAt}T23:59:59`).getTime() : NaN;
      this.errors.expiresAt = Number.isFinite(expiresTime) && expiresTime > Date.now() ? '' : '过期日期必须晚于当前时间';
      return countValid && !this.errors.expiresAt;
    },
    /**
     * 校验通过后创建邀请码，并回到第一页重新加载，确保新创建记录在默认排序下可立即看到。
     */
    async createInvite() {
      if (!this.validateForm()) {
        notifyWarning(this.errors.maxUseCount || this.errors.expiresAt);
        return;
      }
      this.creating = true;
      try {
        await createInviteCode(this.form);
        this.query.pageNum = 1;
        await this.loadData();
      } finally {
        this.creating = false;
      }
    },
    /**
     * 记录待删除的邀请码对象并打开确认框，确认文案可据此展示具体邀请码。
     */
    openDeleteDialog(invite) {
      this.deleteDialog = { visible: true, invite, loading: false };
    },
    /**
     * 删除请求进行中禁止关闭弹窗，防止同一邀请码被重复操作。
     */
    closeDeleteDialog() {
      if (!this.deleteDialog.loading) this.deleteDialog = { visible: false, invite: null, loading: false };
    },
    /**
     * 删除选中邀请码后刷新列表；若删除的是非首页最后一条记录，则回退一页再加载，避免停留在空白页。
     */
    async confirmDelete() {
      this.deleteDialog.loading = true;
      try {
        await deleteInviteCode({ id: this.deleteDialog.invite.id });
        await this.loadData();
        if (!this.rows.length && this.query.pageNum > 1) {
          this.query.pageNum -= 1;
          await this.loadData();
        }
        this.deleteDialog = { visible: false, invite: null, loading: false };
      } finally {
        this.deleteDialog.loading = false;
      }
    },
    /**
     * 切换页码后按原每页条数重新查询邀请码列表。
     */
    changePage(pageNum) {
      this.query.pageNum = pageNum;
      this.loadData();
    },
    /**
     * 改变每页条数时重置到第一页，避免原页码在新分页规模下超出总页数。
     */
    changePageSize(pageSize) {
      this.query = { pageNum: 1, pageSize };
      this.loadData();
    }
  }
};
</script>

<style scoped>
.panel {
  grid-column: 1 / -1;
  padding: 22px;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.table-toolbar h2,
.invite-list-area h3 {
  margin: 0;
}

.invite-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.invite-create-area {
  padding-bottom: 22px;
}

.invite-list-area {
  padding-top: 20px;
  border-top: 1px solid var(--portal-border);
}

.invite-list-area h3 {
  margin-bottom: 12px;
  font-size: 15px;
}

.form-error {
  margin: 6px 0 0;
  color: #dc2626;
  font-size: 13px;
}

.empty-cell {
  color: var(--portal-muted);
  text-align: center;
}

@media (max-width: 720px) {
  .invite-form {
    grid-template-columns: 1fr;
  }
}
</style>
