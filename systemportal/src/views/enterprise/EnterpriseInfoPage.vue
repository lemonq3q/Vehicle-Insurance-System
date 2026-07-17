<template>
  <section>
    <div class="section-title">
      <div>
        <h1>企业信息</h1>
        <p>根据当前账号是否加入企业展示加入、创建或企业详情。</p>
      </div>
      <span v-if="member" class="portal-tag">{{ roleName(activeRoleCode) }}</span>
    </div>

    <div v-if="enterprise" class="enterprise-grid">
      <article class="portal-card panel">
        <div class="panel-title-row">
          <h2>企业资料</h2>
          <button v-if="canEditEnterprise && !isEditingEnterprise" class="layui-btn layui-btn-primary layui-border-green portal-btn" type="button" @click="beginEnterpriseEdit">
            <i class="layui-icon layui-icon-edit"></i>
            修改信息
          </button>
        </div>

        <dl v-if="!isEditingEnterprise" class="info-grid">
          <div class="full"><dt>企业名称</dt><dd>{{ enterprise.name || '-' }}</dd></div>
          <div><dt>企业编码</dt><dd>{{ enterprise.code || '-' }}</dd></div>
          <div><dt>联系人</dt><dd>{{ enterprise.contactName || '-' }}</dd></div>
          <div><dt>联系电话</dt><dd>{{ enterprise.contactPhone || '-' }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ enterprise.createdAt || '-' }}</dd></div>
        </dl>

        <form v-else class="form-grid" @submit.prevent="saveEnterprise">
          <div class="form-field full">
            <label for="enterpriseName">企业名称</label>
            <input id="enterpriseName" v-model="form.name" class="layui-input" />
          </div>
          <div class="form-field">
            <label for="enterpriseCode">企业编码</label>
            <input id="enterpriseCode" v-model="form.code" class="layui-input readonly-input" disabled title="企业编码由系统生成，不可修改" />
          </div>
          <div class="form-field">
            <label for="contactName">联系人</label>
            <input id="contactName" v-model="form.contactName" class="layui-input" />
          </div>
          <div class="form-field">
            <label for="contactPhone">联系电话</label>
            <input id="contactPhone" v-model="form.contactPhone" class="layui-input" />
          </div>
          <div class="action-row full">
            <button class="layui-btn layui-btn-primary portal-btn" type="button" @click="cancelEnterpriseEdit">取消</button>
            <button class="layui-btn portal-btn portal-btn-primary" type="submit">保存企业信息</button>
          </div>
        </form>
      </article>

      <aside class="portal-card panel">
        <h2>当前身份</h2>
        <ul class="summary-list">
          <li><span>企业状态</span><strong>正常</strong></li>
          <li><span>我的角色</span><strong>{{ roleName(activeRoleCode) }}</strong></li>
          <li><span>加入时间</span><strong>{{ member.joinedAt }}</strong></li>
          <li><span>当前余额</span><strong>¥{{ wallet.balanceAmount }}</strong></li>
          <li><span>订阅套餐</span><strong>{{ subscription.plan.name }}</strong></li>
        </ul>
        <button v-if="canExitEnterprise" class="layui-btn layui-btn-primary layui-border-red portal-btn exit-enterprise-button" type="button" @click="openExitDialog">
          <i class="layui-icon layui-icon-logout"></i>
          退出当前企业
        </button>
      </aside>

      <article class="portal-card panel transfer-history-panel">
        <div class="panel-title-row">
          <h2>企业拥有者转让记录</h2>
          <span class="history-caption">共 {{ transferTotal }} 条</span>
        </div>
        <div class="data-table-wrap">
          <table class="layui-table portal-table">
            <thead>
              <tr>
                <th>原拥有者</th>
                <th>新拥有者</th>
                <th>转让时间</th>
                <th>状态</th>
                <th>备注</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in transferLogs" :key="item.id">
                <td>{{ item.fromUserName || '-' }}</td>
                <td>{{ item.toUserName || '-' }}</td>
                <td>{{ item.transferredAt || '-' }}</td>
                <td><span class="portal-tag" :class="{ gray: item.status !== 1 }">{{ transferStatusName(item.status) }}</span></td>
                <td>{{ item.remark || '-' }}</td>
              </tr>
              <tr v-if="!transferLogs.length">
                <td colspan="5" class="empty-cell">暂无转让记录</td>
              </tr>
            </tbody>
          </table>
        </div>
        <LayPagination :total="transferTotal" :page-num="transferQuery.pageNum" :page-size="transferQuery.pageSize" @change="changeTransferPage" @size-change="changeTransferPageSize" />
      </article>
    </div>

    <div v-else class="onboarding-grid">
      <article class="portal-card panel">
        <h2>创建企业</h2>
        <form class="form-grid" @submit.prevent="createNewEnterprise">
          <div class="form-field full">
            <label for="newEnterpriseName">企业名称</label>
            <input id="newEnterpriseName" v-model="createForm.name" class="layui-input" />
          </div>
          <div class="form-field">
            <label for="newContactName">联系人</label>
            <input id="newContactName" v-model="createForm.contactName" class="layui-input" />
          </div>
          <div class="form-field">
            <label for="newContactPhone">联系电话</label>
            <input id="newContactPhone" v-model="createForm.contactPhone" class="layui-input" />
          </div>
          <button class="layui-btn portal-btn portal-btn-primary full">创建并成为企业拥有者</button>
        </form>
      </article>

      <article class="portal-card panel">
        <h2>通过邀请码加入</h2>
        <form @submit.prevent="joinByInvite">
          <div class="form-field">
            <label for="inviteCode">邀请码</label>
            <input id="inviteCode" v-model="inviteForm.code" class="layui-input" />
          </div>
          <button class="layui-btn portal-btn portal-btn-primary join-enterprise-button">加入企业</button>
        </form>
      </article>
    </div>

    <ConfirmDialog
      :visible="exitDialog.visible"
      title="退出当前企业"
      message="退出后将无法继续访问该企业的成员、订阅和业务数据。如需再次加入，必须重新使用邀请码。"
      confirm-text="确认退出"
      :loading="exitDialog.loading"
      @cancel="closeExitDialog"
      @confirm="confirmExitEnterprise"
    />

    <p class="message" aria-live="polite">{{ message }}</p>
  </section>
</template>

<script>
import { createEnterprise, exitEnterprise, getEnterpriseCurrent, getOwnerTransferLogs, joinEnterpriseByInvite, updateEnterprise } from '@/api/portal';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import LayPagination from '@/components/LayPagination.vue';
import { getRoleName } from '@/utils/portalLabels';

export default {
  name: 'EnterpriseInfoPage',
  components: { ConfirmDialog, LayPagination },
  data() {
    return {
      enterprise: null,
      member: null,
      wallet: {},
      subscription: { plan: {} },
      form: {},
      isEditingEnterprise: false,
      exitDialog: { visible: false, loading: false },
      createForm: { name: '', contactName: '', contactPhone: '' },
      inviteForm: { code: '' },
      transferLogs: [],
      transferTotal: 0,
      transferQuery: { pageNum: 1, pageSize: 5 },
      message: ''
    };
  },
  async created() {
    await this.loadData();
    if (this.enterprise) await this.loadTransferLogs();
  },
  computed: {
    activeRoleCode() {
      return this.$store.getters.roleCode || this.member?.roleCode || '';
    },
    canEditEnterprise() {
      return this.$store.getters.isOwner;
    },
    canExitEnterprise() {
      return ['ADMIN', 'ISSUER'].includes(this.activeRoleCode);
    }
  },
  watch: {
    activeRoleCode() {
      if (!this.canEditEnterprise && this.isEditingEnterprise) this.cancelEnterpriseEdit();
    }
  },
  methods: {
    roleName: getRoleName,
    transferStatusName(status) {
      return { 1: '成功', 2: '已撤销', 3: '失败' }[status] || '未知';
    },
    async loadData() {
      const response = await getEnterpriseCurrent();
      Object.assign(this, response.data);
      this.form = this.enterprise ? { ...this.enterprise } : {};
    },
    async loadTransferLogs() {
      const response = await getOwnerTransferLogs(this.transferQuery);
      this.transferLogs = response.data.table;
      this.transferTotal = Number(response.data.total || 0);
    },
    changeTransferPage(pageNum) {
      this.transferQuery.pageNum = pageNum;
      this.loadTransferLogs();
    },
    changeTransferPageSize(pageSize) {
      this.transferQuery.pageNum = 1;
      this.transferQuery.pageSize = pageSize;
      this.loadTransferLogs();
    },
    beginEnterpriseEdit() {
      if (!this.canEditEnterprise) return;
      this.form = { ...this.enterprise };
      this.message = '';
      this.isEditingEnterprise = true;
    },
    cancelEnterpriseEdit() {
      this.form = { ...this.enterprise };
      this.isEditingEnterprise = false;
    },
    async saveEnterprise() {
      if (!this.canEditEnterprise) return;
      const response = await updateEnterprise({
        name: this.form.name,
        contactName: this.form.contactName,
        contactPhone: this.form.contactPhone
      });
      this.enterprise = response.data;
      this.form = { ...response.data };
      this.isEditingEnterprise = false;
      this.message = response.msg;
      await this.$store.dispatch('loadContext');
    },
    openExitDialog() {
      if (!this.canExitEnterprise) return;
      this.exitDialog.visible = true;
    },
    closeExitDialog() {
      if (!this.exitDialog.loading) this.exitDialog.visible = false;
    },
    async confirmExitEnterprise() {
      if (!this.canExitEnterprise) return;
      this.exitDialog.loading = true;
      try {
        const response = await exitEnterprise();
        this.message = response.msg;
        this.exitDialog.visible = false;
        await this.$store.dispatch('loadContext');
        await this.loadData();
      } finally {
        this.exitDialog.loading = false;
      }
    },
    async createNewEnterprise() {
      const response = await createEnterprise(this.createForm);
      this.message = response.msg;
      await this.$store.dispatch('loadContext');
      await this.loadData();
    },
    async joinByInvite() {
      const response = await joinEnterpriseByInvite(this.inviteForm);
      this.message = response.msg;
      await this.$store.dispatch('loadContext');
      await this.loadData();
    }
  }
};
</script>

<style scoped>
.enterprise-grid,
.onboarding-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 18px;
}

.panel {
  padding: 22px;
}

.transfer-history-panel {
  grid-column: 1 / -1;
}

.history-caption {
  color: var(--portal-muted);
  font-size: 13px;
}

.empty-cell {
  color: var(--portal-muted);
  text-align: center;
}

.panel h2 {
  margin: 0;
  font-size: 18px;
}

.panel > h2 {
  margin-bottom: 18px;
}

.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.panel-title-row .layui-icon {
  margin-right: 5px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 28px;
  margin: 0;
}

.info-grid div {
  min-width: 0;
  padding: 16px 0;
  border-bottom: 1px solid var(--portal-border);
}

.info-grid .full {
  grid-column: 1 / -1;
}

.info-grid dt {
  margin-bottom: 5px;
  color: var(--portal-muted);
  font-size: 13px;
}

.info-grid dd {
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--portal-text);
  font-size: 15px;
  font-weight: 600;
}

.readonly-input:disabled {
  cursor: not-allowed;
  color: #64748b;
  background: #f1f5f9;
  opacity: 1;
}

.summary-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.summary-list li {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid var(--portal-border);
}

.summary-list span {
  color: var(--portal-muted);
}

.exit-enterprise-button {
  width: 100%;
  margin-top: 18px;
}

.exit-enterprise-button .layui-icon {
  margin-right: 5px;
}

.join-enterprise-button {
  margin-top: 16px;
}

.message {
  min-height: 24px;
  color: #166534;
}

@media (max-width: 960px) {
  .enterprise-grid,
  .onboarding-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .info-grid {
    grid-template-columns: 1fr;
  }

  .info-grid .full {
    grid-column: auto;
  }
}
</style>
