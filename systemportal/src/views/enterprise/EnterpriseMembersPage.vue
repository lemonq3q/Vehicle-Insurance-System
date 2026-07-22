<template>
  <section>
    <div class="section-title">
      <div>
        <h1>企业人员</h1>
        <p>查看当前企业成员并追踪人员加入、退出和角色变化。</p>
      </div>
    </div>

    <article class="portal-card panel members-panel">
      <div class="table-toolbar">
        <h2>企业成员</h2>
        <div class="filters">
          <input v-model="query.keyword" class="layui-input" placeholder="姓名、手机号或账号" @keyup.enter="searchMembers" />
          <select v-model="query.roleCode" class="layui-select">
            <option value="">全部角色</option>
            <option value="OWNER">企业拥有者</option>
            <option value="ADMIN">管理员</option>
            <option value="ISSUER">出单员</option>
          </select>
          <button class="layui-btn portal-btn portal-btn-primary" @click="searchMembers">查询</button>
          <button class="layui-btn layui-btn-primary portal-btn" @click="resetMemberQuery">重置</button>
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
                    :class="item.status === 1 ? 'member-disable-button' : 'layui-border-green'"
                    @click="toggleMemberStatus(item)"
                  >
                    {{ item.status === 1 ? '停用' : '启用' }}
                  </button>
                  <button v-if="isOwner && item.roleCode !== 'OWNER' && item.status === 1" class="layui-btn layui-btn-xs layui-btn-primary layui-border-blue" @click="openTransferDialog(item)">转让拥有者</button>
                  <button v-if="canRemoveMember(item)" class="layui-btn layui-btn-xs layui-btn-primary layui-border-red" @click="openRemoveDialog(item)">踢出</button>
                  <span v-if="!canManage || item.roleCode === 'OWNER'" class="text-muted">不可操作</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <LayPagination :total="memberTotal" :page-num="query.pageNum" :page-size="query.pageSize" @change="changeMemberPage" @size-change="changeMemberPageSize" />
    </article>

    <article class="portal-card panel change-log-panel">
      <div class="table-toolbar">
        <h2>企业人员变动记录</h2>
        <div class="filters">
          <select v-model="changeQuery.eventType" class="layui-select">
            <option value="">全部类型</option>
            <option value="JOIN">加入企业</option>
            <option value="EXIT">主动退出</option>
            <option value="KICK">踢出企业</option>
            <option value="ROLE_CHANGE">角色修改</option>
            <option value="OWNER_TRANSFER">拥有者转让</option>
          </select>
          <button class="layui-btn portal-btn portal-btn-primary" @click="searchChanges">查询</button>
          <button class="layui-btn layui-btn-primary portal-btn" @click="resetChangeQuery">重置</button>
        </div>
      </div>
      <div class="data-table-wrap">
        <table class="layui-table portal-table">
          <thead><tr><th>变动类型</th><th>操作人</th><th>相关成员</th><th>角色变化</th><th>发生时间</th><th>说明</th></tr></thead>
          <tbody>
            <tr v-for="item in changeLogs" :key="item.id">
              <td><span class="portal-tag" :class="changeTagClass(item.eventType)">{{ eventName(item.eventType) }}</span></td>
              <td>{{ item.operatorNameSnapshot || '系统' }}</td>
              <td>{{ item.targetNameSnapshot || '-' }}</td>
              <td>{{ roleChangeText(item) }}</td>
              <td>{{ item.occurredAt || '-' }}</td>
              <td>{{ item.remark || '-' }}</td>
            </tr>
            <tr v-if="!changeLogs.length"><td colspan="6" class="empty-cell">暂无人员变动记录</td></tr>
          </tbody>
        </table>
      </div>
      <LayPagination :total="changeTotal" :page-num="changeQuery.pageNum" :page-size="changeQuery.pageSize" @change="changeLogPage" @size-change="changeLogPageSize" />
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
      :visible="removeDialog.visible"
      title="移出企业成员"
      :message="removeDialogMessage"
      confirm-text="确认踢出"
      :loading="removeDialog.loading"
      @cancel="closeRemoveDialog"
      @confirm="confirmRemoveMember"
    />

  </section>
</template>

<script>
import { getMemberChangeLogs, getMembers, removeEnterpriseMember, transferOwner, updateMemberRole, updateMemberStatus } from '@/api/portal';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import LayPagination from '@/components/LayPagination.vue';
import { getRoleName } from '@/utils/portalLabels';

export default {
  name: 'EnterpriseMembersPage',
  components: { ConfirmDialog, LayPagination },
  data() {
    return {
      members: [],
      changeLogs: [],
      memberTotal: 0,
      changeTotal: 0,
      query: { pageNum: 1, pageSize: 5, keyword: '', roleCode: '' },
      changeQuery: { pageNum: 1, pageSize: 5, eventType: '' },
      transferDialog: { visible: false, member: null, loading: false },
      removeDialog: { visible: false, member: null, loading: false }
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
    removeDialogMessage() {
      const memberName = this.removeDialog.member?.realName || '';
      return `确定将“${memberName}”踢出当前企业吗？该成员将立即失去企业数据访问权限，重新加入时需要新的邀请码。`;
    }
  },
  async created() {
    await Promise.all([this.loadMembers(), this.loadChangeLogs()]);
  },
  methods: {
    roleName: getRoleName,
    memberStatusName(status) {
      return { 0: '已停用', 1: '已启用', 2: '待审核' }[status] || '未知';
    },
    eventName(eventType) {
      return {
        JOIN: '加入企业',
        EXIT: '主动退出',
        KICK: '踢出企业',
        ROLE_CHANGE: '角色修改',
        OWNER_TRANSFER: '拥有者转让'
      }[eventType] || eventType || '未知';
    },
    changeTagClass(eventType) {
      return {
        EXIT: 'gray',
        KICK: 'warn',
        OWNER_TRANSFER: 'blue'
      }[eventType] || '';
    },
    roleChangeText(item) {
      if (!item.beforeRoleCode && !item.afterRoleCode) return '-';
      const beforeRole = item.beforeRoleCode ? this.roleName(item.beforeRoleCode) : '-';
      const afterRole = item.afterRoleCode ? this.roleName(item.afterRoleCode) : '-';
      return `${beforeRole} -> ${afterRole}`;
    },
    async loadMembers() {
      const response = await getMembers(this.query);
      this.members = response.data.table;
      this.memberTotal = Number(response.data.total || 0);
    },
    async loadChangeLogs() {
      const response = await getMemberChangeLogs(this.changeQuery);
      this.changeLogs = response.data.table;
      this.changeTotal = Number(response.data.total || 0);
    },
    searchMembers() {
      this.query.pageNum = 1;
      this.loadMembers();
    },
    resetMemberQuery() {
      this.query = { ...this.query, pageNum: 1, keyword: '', roleCode: '' };
      this.loadMembers();
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
    searchChanges() {
      this.changeQuery.pageNum = 1;
      this.loadChangeLogs();
    },
    resetChangeQuery() {
      this.changeQuery = { ...this.changeQuery, pageNum: 1, eventType: '' };
      this.loadChangeLogs();
    },
    changeLogPage(pageNum) {
      this.changeQuery.pageNum = pageNum;
      this.loadChangeLogs();
    },
    changeLogPageSize(pageSize) {
      this.changeQuery.pageNum = 1;
      this.changeQuery.pageSize = pageSize;
      this.loadChangeLogs();
    },
    async changeRole(item, roleCode) {
      await updateMemberRole({ memberId: item.id, roleCode });
      await Promise.all([this.loadMembers(), this.loadChangeLogs()]);
    },
    async toggleMemberStatus(item) {
      try {
        await updateMemberStatus({
          memberId: item.id,
          status: item.status === 1 ? 0 : 1
        });
        await this.loadMembers();
      } catch {
        // Request errors are displayed by the Axios interceptor.
      }
    },
    canRemoveMember(item) {
      return this.canManage
        && item.roleCode !== 'OWNER'
        && Number(item.userId) !== Number(this.$store.state.user?.id);
    },
    openRemoveDialog(item) {
      this.removeDialog.member = item;
      this.removeDialog.visible = true;
    },
    closeRemoveDialog() {
      if (this.removeDialog.loading) return;
      this.removeDialog.visible = false;
      this.removeDialog.member = null;
    },
    async confirmRemoveMember() {
      const member = this.removeDialog.member;
      if (!member) return;
      this.removeDialog.loading = true;
      try {
        await removeEnterpriseMember({ memberId: member.id });
        this.removeDialog.visible = false;
        this.removeDialog.member = null;
        await Promise.all([this.loadMembers(), this.loadChangeLogs()]);
        if (!this.members.length && this.query.pageNum > 1) {
          this.query.pageNum -= 1;
          await this.loadMembers();
        }
      } finally {
        this.removeDialog.loading = false;
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
        await transferOwner({ toMemberId: member.id });
        await this.$store.dispatch('loadContext');
        await Promise.all([this.loadMembers(), this.loadChangeLogs()]);
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

.change-log-panel {
  order: 2;
}

section > :not(.section-title):not(.members-panel):not(.change-log-panel) {
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

.member-actions .member-disable-button {
  border-color: #d9a008;
  color: #9a6700;
  background: #fffbea;
}

.member-actions .member-disable-button:hover {
  border-color: #b77900;
  color: #7a4e00;
  background: #fff4c2;
}

.text-muted {
  color: var(--portal-muted);
}

.empty-cell {
  padding: 28px 16px !important;
  color: var(--portal-muted);
  text-align: center !important;
}

@media (max-width: 720px) {
  .filters .layui-input,
  .filters .layui-select {
    width: 100%;
  }
}
</style>
