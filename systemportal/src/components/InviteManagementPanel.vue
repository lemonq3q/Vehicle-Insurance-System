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
  created() {
    this.loadData();
  },
  methods: {
    roleName: getRoleName,
    async loadData() {
      const response = await getInviteCodes(this.query);
      this.rows = response.data.table;
      this.total = Number(response.data.total || 0);
    },
    validateCount() {
      const count = Number(this.form.maxUseCount);
      this.errors.maxUseCount = Number.isInteger(count) && count > 0 ? '' : '邀请人数必须为大于 0 的整数';
      return !this.errors.maxUseCount;
    },
    validateForm() {
      const countValid = this.validateCount();
      const expiresAt = String(this.form.expiresAt || '').trim();
      const expiresTime = expiresAt ? new Date(`${expiresAt}T23:59:59`).getTime() : NaN;
      this.errors.expiresAt = Number.isFinite(expiresTime) && expiresTime > Date.now() ? '' : '过期日期必须晚于当前时间';
      return countValid && !this.errors.expiresAt;
    },
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
    openDeleteDialog(invite) {
      this.deleteDialog = { visible: true, invite, loading: false };
    },
    closeDeleteDialog() {
      if (!this.deleteDialog.loading) this.deleteDialog = { visible: false, invite: null, loading: false };
    },
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
    changePage(pageNum) {
      this.query.pageNum = pageNum;
      this.loadData();
    },
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
