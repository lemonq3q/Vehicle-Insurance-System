<template><div><PageHeader eyebrow="Enterprise Detail" title="企业员工" :description="`${enterprise?.name||'企业'} · 只读成员信息`"><router-link class="layui-btn monitor-secondary" :to="`/enterprises/${id}/overview`">返回概览</router-link></PageHeader><EnterpriseNav/><section class="panel filter-panel"><form class="filter-grid member-filter" @submit.prevent="search"><div class="field"><label>姓名、账号或手机</label><input v-model.trim="query.keyword" class="layui-input" placeholder="输入关键词"></div><div class="field"><label>企业角色</label><select v-model="query.roleName" class="layui-select"><option value="">全部角色</option><option>拥有者</option><option>管理员</option><option>出单员</option></select></div><div class="field"><label>成员状态</label><select v-model="query.status" class="layui-select"><option value="">全部状态</option><option value="1">启用</option><option value="0">停用</option></select></div><div class="action-row"><button class="layui-btn monitor-primary">查询</button><button type="button" class="layui-btn monitor-secondary" @click="reset">重置</button></div></form></section><section class="panel"><header class="panel-header"><div><h2>企业成员</h2><p>平台无权修改企业内部账号</p></div><span class="tag success">{{enterprise?.memberCount||0}} / {{enterprise?.memberLimit||0}} 席位</span></header><div v-if="loading" class="loading-state">正在加载成员…</div><div v-else-if="!result.list.length" class="empty-state">没有符合条件的成员。</div><template v-else><div class="table-wrap"><table class="layui-table monitor-table"><thead><tr><th>成员</th><th>登录账号</th><th>手机号码</th><th>企业角色</th><th>状态</th><th>加入时间</th><th>最后登录</th></tr></thead><tbody><tr v-for="item in result.list" :key="item.id"><td><strong>{{item.realName}}</strong></td><td>{{item.username}}</td><td>{{item.phone}}</td><td><span class="tag">{{item.roleName}}</span></td><td><span :class="['tag',item.status?'success':'muted']">{{item.status?'启用':'停用'}}</span></td><td>{{item.joinedAt}}</td><td>{{item.lastLoginAt||'从未登录'}}</td></tr></tbody></table></div><AppPagination :page-no="result.pageNo" :page-size="result.pageSize" :total="result.total" @change="changePage" /></template></section></div></template>
<script>
import PageHeader from '@/components/PageHeader.vue';
import AppPagination from '@/components/AppPagination.vue';
import EnterpriseNav from './EnterpriseNav.vue';
import { enterpriseApi } from '@/api/monitor';

export default {
  name: 'EnterpriseMembersPage',
  components: { PageHeader, AppPagination, EnterpriseNav },
  /**
   * 保存企业摘要、成员筛选条件和分页结果。监控端只查看 SaaS 成员状态，不在此页面修改企业内部角色。
   */
  data: () => ({
    enterprise: null,
    query: { keyword: '', roleName: '', status: '', pageNo: 1, pageSize: 10 },
    result: { list: [], pageNo: 1, pageSize: 10, total: 0 },
    loading: false
  }),
  computed: {
    /**
     * 将路由企业标识转为数字，作为所有成员接口的租户范围参数。
     */
    id() { return Number(this.$route.params.id); }
  },
  /**
   * 页面挂载时读取企业标题资料并加载第一页成员。
   */
  mounted() {
    enterpriseApi.detail(this.id).then(data => { this.enterprise = data; });
    this.load();
  },
  methods: {
    /**
     * 按关键字、角色、状态及分页条件查询指定企业成员，并可靠恢复加载状态。
     */
    async load() {
      this.loading = true;
      try { this.result = await enterpriseApi.members(this.id, this.query); } finally { this.loading = false; }
    },
    /**
     * 应用筛选时回到第一页，避免旧页码超出筛选后的结果范围。
     */
    search() { this.query.pageNo = 1; this.load(); },
    /**
     * 清空成员筛选但保留每页条数，并重新查询首页。
     */
    reset() { Object.assign(this.query, { keyword: '', roleName: '', status: '', pageNo: 1 }); this.load(); },
    /**
     * 响应分页组件页码变化并加载对应成员页。
     */
    changePage(pageNo) { this.query.pageNo = pageNo; this.load(); }
  }
};
</script>
<style scoped>.member-filter{grid-template-columns:repeat(3,1fr) auto}</style>
