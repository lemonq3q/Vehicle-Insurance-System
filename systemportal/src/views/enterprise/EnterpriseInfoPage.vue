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
          <li><span>加入时间</span><strong>{{ member?.joinedAt || '-' }}</strong></li>
          <li><span>当前余额</span><strong>¥{{ money(wallet?.balanceAmount) }}</strong></li>
          <li><span>订阅套餐</span><strong>{{ subscriptionName }}</strong></li>
        </ul>
        <button v-if="canExitEnterprise" class="layui-btn layui-btn-primary layui-border-red portal-btn exit-enterprise-button" type="button" @click="openExitDialog">
          <i class="layui-icon layui-icon-logout"></i>
          退出当前企业
        </button>
      </aside>

      <InviteManagementPanel v-if="canManageInvites" />
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

  </section>
</template>

<script>
import { createEnterprise, exitEnterprise, getEnterpriseCurrent, joinEnterpriseByInvite, updateEnterprise } from '@/api/portal';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import InviteManagementPanel from '@/components/InviteManagementPanel.vue';
import { getRoleName } from '@/utils/portalLabels';

export default {
  name: 'EnterpriseInfoPage',
  components: { ConfirmDialog, InviteManagementPanel },
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
      inviteForm: { code: '' }
    };
  },
  async created() {
    await this.loadData();
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
    },
    canManageInvites() {
      return this.$store.getters.canManageEnterprise;
    },
    subscriptionName() {
      if (Number(this.subscription?.status) !== 1) return '暂未订阅';
      return this.subscription?.plan?.name || '套餐信息缺失';
    }
  },
  watch: {
    activeRoleCode() {
      if (!this.canEditEnterprise && this.isEditingEnterprise) this.cancelEnterpriseEdit();
    }
  },
  methods: {
    roleName: getRoleName,
    money(value) {
      return Number(value || 0).toFixed(2);
    },
    async loadData() {
      const response = await getEnterpriseCurrent();
      const data = response.data || {};
      this.enterprise = data.enterprise || null;
      this.member = data.member || null;
      this.wallet = data.wallet || { balanceAmount: 0, currency: 'CNY' };
      this.subscription = data.subscription || { status: 0, userLimit: 0, plan: {} };
      this.form = this.enterprise ? { ...this.enterprise } : {};
    },
    beginEnterpriseEdit() {
      if (!this.canEditEnterprise) return;
      this.form = { ...this.enterprise };
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
        await exitEnterprise();
        this.exitDialog.visible = false;
        await this.$store.dispatch('loadContext');
        await this.loadData();
      } finally {
        this.exitDialog.loading = false;
      }
    },
    async createNewEnterprise() {
      await createEnterprise(this.createForm);
      await this.$store.dispatch('loadContext');
      await this.loadData();
    },
    async joinByInvite() {
      await joinEnterpriseByInvite(this.inviteForm);
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
