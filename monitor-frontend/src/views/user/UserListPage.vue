<template><div><PageHeader eyebrow="Platform Settings" title="用户管理" description="仅管理运营监控平台账号；企业员工账号不在此处维护。"><button class="layui-btn monitor-primary" @click="openCreate">创建用户</button></PageHeader><section class="panel filter-panel"><form class="filter-grid user-filter" @submit.prevent="search"><div class="field"><label>姓名、账号或联系方式</label><input v-model.trim="query.keyword" class="layui-input" placeholder="输入关键词"></div><div class="field"><label>平台角色</label><select v-model="query.roleCode" class="layui-select"><option value="">全部角色</option><option value="ADMIN">管理员</option><option value="CUSTOMER_SERVICE">售后客服</option></select></div><div class="field"><label>账号状态</label><select v-model="query.status" class="layui-select"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select></div><div class="action-row"><button class="layui-btn monitor-primary">查询</button><button type="button" class="layui-btn monitor-secondary" @click="reset">重置</button></div></form></section><section class="panel"><header class="panel-header"><div><h2>平台用户</h2><p>共 {{result.total}} 个账号</p></div><span class="tag warning">仅管理员可操作</span></header><div v-if="loading" class="loading-state">正在加载平台用户…</div><div v-else-if="!result.list.length" class="empty-state">没有符合条件的用户。</div><template v-else><div class="table-wrap"><table class="layui-table monitor-table"><thead><tr><th>用户</th><th>登录账号</th><th>联系方式</th><th>角色</th><th>状态</th><th>最后登录</th><th>创建时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in result.list" :key="item.id"><td><strong>{{item.realName}}</strong></td><td>{{item.username}}</td><td>{{item.phone||'—'}}<br><small>{{item.email||'—'}}</small></td><td><span class="tag">{{item.roleName}}</span></td><td><span :class="['tag',item.status?'success':'muted']">{{item.status?'启用':'停用'}}</span></td><td>{{item.lastLoginAt||'从未登录'}}</td><td>{{item.createdAt}}</td><td><div class="action-row"><router-link class="layui-btn layui-btn-xs monitor-secondary" :to="`/platform-users/${item.id}`">编辑</router-link><button class="layui-btn layui-btn-xs" :class="item.status?'monitor-danger':'monitor-primary'" :disabled="item.current" @click="openStatus(item)">{{item.status?'停用':'启用'}}</button></div></td></tr></tbody></table></div><AppPagination :page-no="result.pageNo" :page-size="result.pageSize" :total="result.total" @change="changePage" /></template></section><AppModal v-model="createModal" title="创建平台用户" confirm-text="创建账号" :loading="submitting" @confirm="createUser"><div v-for="field in createFields" :key="field.key" class="field"><label>{{field.label}} *</label><input v-model.trim="createForm[field.key]" :type="field.type||'text'" class="layui-input"></div><div class="field"><label>平台角色 *</label><select v-model="createForm.roleCode" class="layui-select"><option value="CUSTOMER_SERVICE">售后客服</option><option value="ADMIN">管理员</option></select></div><p v-if="formError" class="error-text">{{formError}}</p></AppModal><AppModal v-model="statusModal" :title="targetUser?.status?'停用用户':'启用用户'" :confirm-text="targetUser?.status?'确认停用':'确认启用'" :danger="Boolean(targetUser?.status)" :loading="submitting" @confirm="changeStatus"><p>确认{{targetUser?.status?'停用':'启用'}}账号“{{targetUser?.username}}”？</p><div class="field"><label>操作原因 *</label><textarea v-model.trim="reason" class="layui-textarea"></textarea><p v-if="formError" class="error-text">{{formError}}</p></div></AppModal><AppModal v-model="passwordModal" title="账号创建成功" confirm-text="我已保存" @confirm="passwordModal=false"><p>初始密码：<strong class="number">{{initialPassword}}</strong></p><p class="helper">请通过安全渠道交付给用户，并要求首次登录后修改。</p></AppModal><AppToast :message="toastMessage" :type="toastType" /></div></template>
<script>
import PageHeader from '@/components/PageHeader.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppModal from '@/components/AppModal.vue';
import AppToast from '@/components/AppToast.vue';
import feedback from '@/mixins/feedback';
import { userApi } from '@/api/monitor';

/**
 * 创建新的后台账号表单，默认角色为客服且不复用上一次弹窗输入。
 */
const blank = () => ({ username: '', realName: '', phone: '', email: '', roleCode: 'CUSTOMER_SERVICE' });

export default {
  name: 'UserListPage',
  components: { PageHeader, AppPagination, AppModal, AppToast },
  mixins: [feedback],
  /**
   * 保存账号筛选分页，以及创建、状态变更和初始密码展示弹窗状态。
   */
  data: () => ({
    query: { keyword: '', roleCode: '', status: '', pageNo: 1, pageSize: 10 },
    result: { list: [], pageNo: 1, pageSize: 10, total: 0 },
    loading: false, submitting: false, createModal: false, statusModal: false, passwordModal: false,
    targetUser: null, reason: '', formError: '', initialPassword: '', createForm: blank(),
    createFields: [{ key: 'username', label: '登录账号' }, { key: 'realName', label: '真实姓名' }, { key: 'phone', label: '手机号码', type: 'tel' }, { key: 'email', label: '电子邮箱', type: 'email' }]
  }),
  /**
   * 页面挂载后加载第一页后台账号。
   */
  mounted() { this.load(); },
  methods: {
    /**
     * 按关键字、角色、状态和分页查询后台账号，并统一处理接口错误。
     */
    async load() {
      this.loading = true;
      try { this.result = await userApi.list(this.query); } catch (error) { this.errorMessage(error); } finally { this.loading = false; }
    },
    /**
     * 应用账号筛选时从第一页开始。
     */
    search() { this.query.pageNo = 1; this.load(); },
    /**
     * 清空账号筛选并保留每页条数后重新查询。
     */
    reset() { Object.assign(this.query, { keyword: '', roleCode: '', status: '', pageNo: 1 }); this.load(); },
    /**
     * 响应分页变化并读取对应账号页。
     */
    changePage(pageNo) { this.query.pageNo = pageNo; this.load(); },
    /**
     * 重建空白创建表单并清除旧错误，防止敏感账号信息在弹窗间残留。
     */
    openCreate() { this.createForm = blank(); this.formError = ''; this.createModal = true; },
    /**
     * 校验最小账号资料后创建用户，服务端返回的初始密码只在专用弹窗展示，并刷新账号列表。
     */
    async createUser() {
      if (!this.createForm.username || !this.createForm.realName) { this.formError = '登录账号和真实姓名为必填项'; return; }
      this.submitting = true;
      try {
        const data = await userApi.create({ ...this.createForm, reason: '管理员创建账号' });
        this.createModal = false;
        this.initialPassword = data.initialPassword;
        this.passwordModal = true;
        await this.load();
      } catch (error) { this.formError = error.message; } finally { this.submitting = false; }
    },
    /**
     * 记录待启停账号并清理上一次操作原因和错误。
     */
    openStatus(item) { this.targetUser = item; this.reason = ''; this.formError = ''; this.statusModal = true; },
    /**
     * 要求填写原因后反转账号状态，成功后刷新服务端列表并提示操作结果。
     */
    async changeStatus() {
      if (!this.reason) { this.formError = '请填写操作原因'; return; }
      this.submitting = true;
      try {
        await userApi.updateStatus(this.targetUser.id, { status: this.targetUser.status ? 0 : 1, reason: this.reason });
        this.statusModal = false;
        await this.load();
        this.notify('账号状态已更新');
      } catch (error) { this.formError = error.message; } finally { this.submitting = false; }
    }
  }
};
</script>
<style scoped>.user-filter{grid-template-columns:2fr 1fr 1fr auto}</style>
