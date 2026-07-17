<template>
  <section>
    <div class="section-title">
      <div>
        <h1>企业人员</h1>
        <p>所有成员可查看人员列表，企业拥有者和管理员可维护邀请码与成员角色。</p>
      </div>
    </div>

    <article v-if="canManage" class="portal-card panel invite-panel">
      <div class="invite-create-area">
        <div class="table-toolbar">
          <h2>邀请码</h2>
          <button class="layui-btn portal-btn portal-btn-primary" @click="createInvite">创建邀请码</button>
        </div>
        <div class="invite-form">
          <div class="form-field">
            <label for="maxUseCount">邀请人数</label>
            <input id="maxUseCount" v-model.number="inviteForm.maxUseCount" class="layui-input" type="number" min="1" />
          </div>
          <div class="form-field">
            <label for="expiresAt">过期时间</label>
            <LayDatePicker v-model="inviteForm.expiresAt" placeholder="请选择邀请码过期时间" />
          </div>
        </div>
      </div>
      <div class="invite-list-area">
        <h3>已创建的邀请码</h3>
        <div class="data-table-wrap">
          <table class="layui-table portal-table">
            <thead>
              <tr>
                <th>邀请码</th>
                <th>默认角色</th>
                <th>使用次数</th>
                <th>过期时间</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in invites" :key="item.id">
                <td>{{ item.code }}</td>
                <td>{{ roleName(item.defaultRoleCode) }}</td>
                <td>{{ item.usedCount }} / {{ item.maxUseCount || '不限' }}</td>
                <td>{{ item.expiresAt }}</td>
                <td><span class="portal-tag">可用</span></td>
                <td>
                  <button class="layui-btn layui-btn-xs layui-btn-primary layui-border-red" @click="openDeleteInviteDialog(item)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <LayPagination :total="inviteTotal" :page-num="inviteQuery.pageNum" :page-size="inviteQuery.pageSize" @change="changeInvitePage" @size-change="changeInvitePageSize" />
      </div>
    </article>

    <article class="portal-card panel members-panel">
      <div class="table-toolbar">
        <h2>企业成员</h2>
        <div class="filters">
          <input v-model="query.keyword" class="layui-input" placeholder="姓名、手机号或账号" @keyup.enter="loadMembers" />
          <select v-model="query.roleCode" class="layui-select" @change="loadMembers">
            <option value="">全部角色</option>
            <option value="OWNER">企业拥有者</option>
            <option value="ADMIN">管理员</option>
            <option value="ISSUER">出单员</option>
          </select>
          <button class="layui-btn portal-btn portal-btn-primary" @click="loadMembers">查询</button>
        </div>
      </div>
      <div class="data-table-wrap">
        <table class="layui-table portal-table members-table">
          <thead>
            <tr>
              <th>姓名</th>
              <th>账号</th>
              <th>手机号</th>
              <th>角色</th>
              <th>状态</th>
              <th>加入时间</th>
              <th>修改角色</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in members" :key="item.id">
              <td>{{ item.realName }}</td>
              <td>{{ item.username }}</td>
              <td>{{ item.phone }}</td>
              <td><span class="portal-tag" :class="{ blue: item.roleCode === 'OWNER', gray: item.roleCode === 'ISSUER' }">{{ roleName(item.roleCode) }}</span></td>
              <td><span class="portal-tag" :class="{ gray: item.status === 0, warn: item.status === 2 }">{{ memberStatusName(item.status) }}</span></td>
              <td>{{ item.joinedAt }}</td>
              <td>
                <div class="member-actions">
                  <select v-if="canManage && item.roleCode !== 'OWNER'" :value="item.roleCode" class="layui-select role-select" @change="changeRole(item, $event.target.value)">
                    <option value="ADMIN">管理员</option>
                    <option value="ISSUER">出单员</option>
                  </select>
                  <span v-else class="text-muted">不可修改</span>
                </div>
              </td>
              <td>
                <div class="member-actions">
                  <button
                    v-if="canManage && item.roleCode !== 'OWNER' && [0, 1].includes(item.status)"
                    class="layui-btn layui-btn-xs layui-btn-primary"
                    :class="item.status === 1 ? 'layui-border-red' : 'layui-border-green'"
                    @click="toggleMemberStatus(item)"
                  >
                    {{ item.status === 1 ? '停用' : '启用' }}
                  </button>
                  <button v-if="isOwner && item.roleCode !== 'OWNER' && item.status === 1" class="layui-btn layui-btn-xs layui-btn-primary layui-border-blue" @click="openTransferDialog(item)">转让拥有者</button>
                  <span v-if="!canManage || item.roleCode === 'OWNER'" class="text-muted">不可操作</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <LayPagination :total="memberTotal" :page-num="query.pageNum" :page-size="query.pageSize" @change="changeMemberPage" @size-change="changeMemberPageSize" />
    </article>

    <ConfirmDialog
      :visible="transferDialog.visible"
      title="转让企业拥有者"
      :message="transferDialogMessage"
      confirm-text="确认转让"
      :loading="transferDialog.loading"
      @cancel="closeTransferDialog"
      @confirm="confirmTransfer"
    />

    <ConfirmDialog
      :visible="deleteInviteDialog.visible"
      title="删除邀请码"
      :message="deleteInviteDialogMessage"
      confirm-text="确认删除"
      :loading="deleteInviteDialog.loading"
      @cancel="closeDeleteInviteDialog"
      @confirm="confirmDeleteInvite"
    />

    <p v-if="memberStatusError" class="message error" role="alert">{{ memberStatusError }}</p>
    <p class="message" aria-live="polite">{{ message }}</p>
  </section>
</template>

<script>
import { createInviteCode, deleteInviteCode, getInviteCodes, getMembers, transferOwner, updateMemberRole, updateMemberStatus } from '@/api/portal';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import LayDatePicker from '@/components/LayDatePicker.vue';
import LayPagination from '@/components/LayPagination.vue';
import { getRoleName } from '@/utils/portalLabels';

export default {
  name: 'EnterpriseMembersPage',
  components: { ConfirmDialog, LayDatePicker, LayPagination },
  data() {
    return {
      members: [],
      invites: [],
      memberTotal: 0,
      inviteTotal: 0,
      query: { pageNum: 1, pageSize: 5, keyword: '', roleCode: '' },
      inviteQuery: { pageNum: 1, pageSize: 5 },
      inviteForm: { maxUseCount: 5, expiresAt: '2026-08-01' },
      transferDialog: { visible: false, member: null, loading: false },
      deleteInviteDialog: { visible: false, invite: null, loading: false },
      memberStatusError: '',
      message: ''
    };
  },
  computed: {
    canManage() {
      return this.$store.getters.canManageEnterprise;
    },
    isOwner() {
      return this.$store.getters.isOwner;
    },
    transferDialogMessage() {
      const memberName = this.transferDialog.member?.realName || '';
      return `确定将企业拥有者转让给“${memberName}”吗？转让后你的角色将变更为管理员，请谨慎操作。`;
    },
    deleteInviteDialogMessage() {
      const inviteCode = this.deleteInviteDialog.invite?.code || '';
      return `确定删除邀请码“${inviteCode}”吗？删除后尚未使用该邀请码的用户将无法加入企业。`;
    }
  },
  async created() {
    await Promise.all([this.loadMembers(), this.loadInvites()]);
  },
  methods: {
    roleName: getRoleName,
    memberStatusName(status) {
      return { 0: '已停用', 1: '已启用', 2: '待审核', 3: '已退出' }[status] || '未知';
    },
    async loadMembers() {
      const response = await getMembers(this.query);
      this.members = response.data.table;
      this.memberTotal = Number(response.data.total || 0);
    },
    async loadInvites() {
      const response = await getInviteCodes(this.inviteQuery);
      this.invites = response.data.table;
      this.inviteTotal = Number(response.data.total || 0);
    },
    changeMemberPage(pageNum) {
      this.query.pageNum = pageNum;
      this.loadMembers();
    },
    changeMemberPageSize(pageSize) {
      this.query.pageNum = 1;
      this.query.pageSize = pageSize;
      this.loadMembers();
    },
    changeInvitePage(pageNum) {
      this.inviteQuery.pageNum = pageNum;
      this.loadInvites();
    },
    changeInvitePageSize(pageSize) {
      this.inviteQuery.pageNum = 1;
      this.inviteQuery.pageSize = pageSize;
      this.loadInvites();
    },
    async createInvite() {
      const response = await createInviteCode(this.inviteForm);
      this.message = response.msg;
      this.inviteQuery.pageNum = 1;
      await this.loadInvites();
    },
    openDeleteInviteDialog(invite) {
      this.deleteInviteDialog.invite = invite;
      this.deleteInviteDialog.visible = true;
    },
    closeDeleteInviteDialog() {
      if (this.deleteInviteDialog.loading) return;
      this.deleteInviteDialog.visible = false;
      this.deleteInviteDialog.invite = null;
    },
    async confirmDeleteInvite() {
      const invite = this.deleteInviteDialog.invite;
      if (!invite) return;

      this.deleteInviteDialog.loading = true;
      try {
        const response = await deleteInviteCode({ id: invite.id });
        this.message = response.msg;
        await this.loadInvites();
        if (!this.invites.length && this.inviteQuery.pageNum > 1) {
          this.inviteQuery.pageNum -= 1;
          await this.loadInvites();
        }
        this.deleteInviteDialog.visible = false;
        this.deleteInviteDialog.invite = null;
      } finally {
        this.deleteInviteDialog.loading = false;
      }
    },
    async changeRole(item, roleCode) {
      const response = await updateMemberRole({ memberId: item.id, roleCode });
      this.message = response.msg;
      await this.loadMembers();
    },
    async toggleMemberStatus(item) {
      this.memberStatusError = '';
      try {
        const response = await updateMemberStatus({
          memberId: item.id,
          status: item.status === 1 ? 0 : 1
        });
        this.message = response.msg;
        await this.loadMembers();
      } catch (error) {
        this.memberStatusError = error.message || '成员状态更新失败';
      }
    },
    openTransferDialog(item) {
      this.transferDialog.member = item;
      this.transferDialog.visible = true;
    },
    closeTransferDialog() {
      if (this.transferDialog.loading) return;
      this.transferDialog.visible = false;
      this.transferDialog.member = null;
    },
    async confirmTransfer() {
      const member = this.transferDialog.member;
      if (!member) return;

      this.transferDialog.loading = true;
      try {
        const response = await transferOwner({ toMemberId: member.id });
        this.message = response.msg;
        await this.$store.dispatch('loadContext');
        await this.loadMembers();
        this.transferDialog.visible = false;
        this.transferDialog.member = null;
      } finally {
        this.transferDialog.loading = false;
      }
    }
  }
};
</script>

<style scoped>
section {
  display: flex;
  flex-direction: column;
}

.section-title {
  order: 0;
}

.members-panel {
  order: 1;
}

.invite-panel {
  order: 2;
}

section > :not(.section-title):not(.members-panel):not(.invite-panel) {
  order: 3;
}

.panel {
  padding: 22px;
  margin-bottom: 18px;
}

.panel h2 {
  margin: 0 0 16px;
  font-size: 18px;
}

.table-toolbar {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.invite-create-area {
  padding-bottom: 22px;
}

.invite-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 14px;
}

.invite-action-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.invite-list-area {
  padding-top: 20px;
  border-top: 1px solid var(--portal-border);
}

.invite-list-area h3 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 700;
}

.filters {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.filters .layui-input {
  width: 220px;
}

.filters .layui-select,
.role-select {
  min-width: 130px;
  height: 38px;
  border: 1px solid var(--portal-border);
  border-radius: 4px;
}

.members-table th:nth-last-child(2),
.members-table td:nth-last-child(2) {
  width: 160px;
  text-align: center;
}

.members-table th:last-child,
.members-table td:last-child {
  width: 220px;
  text-align: center;
}

.member-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
}

.member-actions .layui-btn {
  margin: 0;
  white-space: nowrap;
}

.text-muted {
  color: var(--portal-muted);
}

.message {
  color: #166534;
}

.message.error {
  color: var(--portal-danger);
}

@media (max-width: 720px) {
  .invite-form {
    grid-template-columns: 1fr;
  }

  .invite-action-row .portal-btn {
    width: 100%;
  }
}
</style>
