<template><div><PageHeader eyebrow="Customer Operations" title="企业列表" description="集中查询和维护平台内全部企业、余额与套餐状态。"><button class="layui-btn monitor-secondary" @click="exportCsv">导出列表</button></PageHeader><section class="panel filter-panel"><form class="filter-grid enterprise-filter" @submit.prevent="search"><div class="field"><label for="entKeyword">企业名称或编码</label><input id="entKeyword" v-model.trim="query.keyword" class="layui-input" placeholder="输入企业名称、编码"></div><div class="field"><label for="entStatus">企业状态</label><select id="entStatus" v-model="query.status" class="layui-select"><option value="">全部状态</option><option value="1">正常</option><option value="2">欠费限制</option><option value="0">已停用</option></select></div><div class="field"><label for="plan">当前套餐</label><select id="plan" v-model="query.planId" class="layui-select"><option value="">全部套餐</option><option v-for="plan in plans" :key="plan.id" :value="plan.id">{{plan.name}}</option></select></div><div class="field"><label for="expire">到期范围</label><select id="expire" v-model="query.expireDays" class="layui-select"><option value="">全部</option><option value="7">7 天内到期</option><option value="30">30 天内到期</option></select></div><div class="action-row"><button class="layui-btn monitor-primary" :disabled="loading">查询</button><button type="button" class="layui-btn monitor-secondary" @click="reset">重置</button></div></form></section><section class="panel"><header class="panel-header"><div><h2>全部企业</h2><p>当前筛选共 {{result.total}} 家企业</p></div><span class="tag success">{{loading?'更新中':'数据已更新'}}</span></header><div v-if="loading" class="loading-state">正在查询企业…</div><div v-else-if="!result.list.length" class="empty-state">没有符合条件的企业，请调整筛选条件。</div><template v-else><div class="table-wrap"><table class="layui-table monitor-table"><thead><tr><th>企业</th><th>状态</th><th>员工数</th><th>账户余额</th><th>当前套餐</th><th>套餐到期</th><th>今日出单</th><th>今日调用</th><th>操作</th></tr></thead><tbody><tr v-for="item in result.list" :key="item.id"><td><strong>{{item.name}}</strong><br><small>{{item.code}}</small></td><td><span :class="['tag',statusMeta(item.status).class]">{{statusMeta(item.status).text}}</span></td><td>{{item.memberCount}}</td><td class="number">{{money(item.balance)}}</td><td>{{item.planName||'未订阅'}}</td><td>{{item.subscriptionEndDate||'—'}}</td><td>{{format(item.todayUsage.workorderCount)}}</td><td>{{format(item.todayUsage.requestCount)}}</td><td><router-link class="layui-btn layui-btn-xs monitor-primary" :to="`/enterprises/${item.id}/overview`">查看</router-link></td></tr></tbody></table></div><AppPagination :page-no="result.pageNo" :page-size="result.pageSize" :total="result.total" @change="changePage" /></template></section><AppToast :message="toastMessage" :type="toastType" /></div></template>
<script>
import PageHeader from '@/components/PageHeader.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppToast from '@/components/AppToast.vue';
import feedback from '@/mixins/feedback';
import { enterpriseApi, planApi } from '@/api/monitor';

export default {
  name: 'EnterpriseListPage',
  components: { PageHeader, AppPagination, AppToast },
  mixins: [feedback],
  /**
   * 保存企业关键字、状态、套餐、到期范围筛选和分页结果，同时加载套餐选项供筛选器使用。
   */
  data: () => ({ query: { keyword: '', status: '', planId: '', expireDays: '', pageNo: 1, pageSize: 10 }, result: { list: [], pageNo: 1, pageSize: 10, total: 0 }, plans: [], loading: false }),
  /**
   * 页面挂载后并行加载企业列表和套餐筛选选项，任一初始化异常通过统一反馈展示。
   */
  mounted() { Promise.all([this.load(), planApi.list().then(data => { this.plans = data; })]).catch(this.errorMessage); },
  methods: {
    /**
     * 以中文千分位显示成员等数量。
     */
    format: value => Number(value || 0).toLocaleString('zh-CN'),
    /**
     * 以人民币两位小数显示企业余额。
     */
    money: value => `¥ ${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`,
    /**
     * 将企业正常、欠费限制和停用状态映射为文案与标签样式。
     */
    statusMeta: status => status === 1 ? { text: '正常', class: 'success' } : status === 2 ? { text: '欠费限制', class: 'danger' } : { text: '已停用', class: 'muted' },
    /**
     * 按当前筛选和分页读取企业列表，异常时保留现有结果并显示提示。
     */
    async load() {
      this.loading = true;
      try { this.result = await enterpriseApi.list(this.query); } catch (error) { this.errorMessage(error); } finally { this.loading = false; }
    },
    /**
     * 应用企业筛选时重置到第一页。
     */
    search() { this.query.pageNo = 1; this.load(); },
    /**
     * 清空业务筛选条件，保留每页条数并重新加载首页。
     */
    reset() { Object.assign(this.query, { keyword: '', status: '', planId: '', expireDays: '', pageNo: 1 }); this.load(); },
    /**
     * 响应分页页码变化并查询对应企业页。
     */
    changePage(pageNo) { this.query.pageNo = pageNo; this.load(); },
    /**
     * 将当前筛选页导出为带 BOM 的 CSV，下载后释放对象 URL 并显示完成提示。
     */
    exportCsv() {
      const rows = [['企业编码', '企业名称', '状态', '员工数', '余额', '套餐', '到期日'], ...this.result.list.map(item => [item.code, item.name, this.statusMeta(item.status).text, item.memberCount, item.balance, item.planName || '', item.subscriptionEndDate || ''])];
      const blob = new Blob(['\ufeff' + rows.map(row => row.join(',')).join('\n')], { type: 'text/csv;charset=utf-8' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob); link.download = '企业列表.csv'; link.click(); URL.revokeObjectURL(link.href);
      this.notify('已导出当前筛选页');
    }
  }
};
</script>
