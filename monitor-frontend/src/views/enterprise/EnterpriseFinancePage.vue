<template><div><PageHeader eyebrow="Enterprise Detail" title="订单与流水" :description="`${enterprise?.name||'企业'} · 财务记录统一查询`"><button class="layui-btn monitor-secondary" @click="exportCsv">导出当前记录</button></PageHeader><EnterpriseNav/><section class="panel"><div class="summary-strip"><div class="summary-item"><span>账户余额</span><strong>{{money(summary.balance)}}</strong></div><div class="summary-item"><span>累计充值</span><strong>{{money(summary.totalRecharge)}}</strong></div><div class="summary-item"><span>累计套餐支出</span><strong>{{money(summary.totalSubscriptionExpense)}}</strong></div><div class="summary-item"><span>本月流水笔数</span><strong>{{summary.monthTransactionCount}}</strong></div></div></section><div class="finance-tabs" role="tablist"><button v-for="tab in tabs" :key="tab.key" :class="{active:active===tab.key}" @click="switchTab(tab.key)">{{tab.label}}</button></div><section class="panel"><header class="panel-header"><div><h2>{{activeTab.label}}</h2><p>财务信息可查看，不提供修改订单功能</p></div><span class="tag success">共 {{result.total}} 条</span></header><div v-if="loading" class="loading-state">正在加载财务记录…</div><div v-else-if="!result.list.length" class="empty-state">暂无记录。</div><template v-else><div class="table-wrap"><table class="layui-table monitor-table"><thead><tr><th>业务编号</th><th>业务类型</th><th>金额</th><th>状态/余额</th><th>关联信息</th><th>发生时间</th></tr></thead><tbody><tr v-for="item in result.list" :key="item.id"><td>{{item.orderNo||item.transactionNo}}</td><td>{{typeText(item)}}</td><td>{{money(item.amount)}}</td><td>{{statusText(item)}}</td><td>{{item.channel||item.referenceNo||`${item.startedAt} 至 ${item.endedAt}`}}</td><td>{{item.paidAt||item.createdAt}}</td></tr></tbody></table></div><AppPagination :page-no="result.pageNo" :page-size="result.pageSize" :total="result.total" @change="changePage" /></template></section></div></template>
<script>
import PageHeader from '@/components/PageHeader.vue';
import AppPagination from '@/components/AppPagination.vue';
import EnterpriseNav from './EnterpriseNav.vue';
import { enterpriseApi } from '@/api/monitor';

export default {
  name: 'EnterpriseFinancePage',
  components: { PageHeader, AppPagination, EnterpriseNav },
  /**
   * 保存企业资料、财务汇总和当前明细标签分页，充值、订阅与钱包流水共用同一查询及导出容器。
   */
  data: () => ({
    enterprise: null,
    summary: {},
    tabs: [
      { key: 'recharge-orders', label: '充值订单' },
      { key: 'subscription-orders', label: '订阅订单' },
      { key: 'wallet-transactions', label: '钱包流水' }
    ],
    active: 'recharge-orders',
    result: { list: [], pageNo: 1, pageSize: 10, total: 0 },
    loading: false
  }),
  computed: {
    /**
     * 将路由中的企业 ID 转为财务接口使用的数字标识。
     */
    id() { return Number(this.$route.params.id); },
    /**
     * 获取当前标签配置，为标题和导出文件名提供一致文案。
     */
    activeTab() { return this.tabs.find(item => item.key === this.active); }
  },
  /**
   * 挂载后分别加载企业资料、财务汇总以及默认的充值订单分页。
   */
  mounted() {
    enterpriseApi.detail(this.id).then(data => { this.enterprise = data; });
    enterpriseApi.financeSummary(this.id).then(data => { this.summary = data; });
    this.load();
  },
  methods: {
    /**
     * 以人民币两位小数显示金额，负值符号放在货币符号之前。
     */
    money: value => `${Number(value) < 0 ? '- ' : ''}¥ ${Math.abs(Number(value || 0)).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`,
    /**
     * 优先展示套餐名称；钱包业务按类型映射，缺失类型时使用账户充值兜底。
     */
    typeText(item) { return item.planName || ({ RECHARGE: '账户充值', BUY_PLAN: '购买套餐', ADJUST: '后台调整' }[item.type]) || '账户充值'; },
    /**
     * 流水记录展示交易后余额，订单记录则展示完成或处理中的业务状态。
     */
    statusText(item) { return item.balanceAfter !== undefined ? `余额 ${this.money(item.balanceAfter)}` : item.status === 2 ? '已完成' : '处理中'; },
    /**
     * 按当前财务资源类型及分页参数读取企业明细。
     */
    async load() {
      this.loading = true;
      try {
        this.result = await enterpriseApi.finance(this.id, this.active, { pageNo: this.result.pageNo, pageSize: this.result.pageSize });
      } finally { this.loading = false; }
    },
    /**
     * 切换财务标签时回到第一页，避免沿用另一资源的分页位置。
     */
    switchTab(key) { this.active = key; this.result.pageNo = 1; this.load(); },
    /**
     * 更新当前明细页码并重新查询。
     */
    changePage(pageNo) { this.result.pageNo = pageNo; this.load(); },
    /**
     * 将当前页明细转换为带 UTF-8 BOM 的 CSV 并触发下载，确保中文在表格软件中正确识别。
     */
    exportCsv() {
      const rows = [['业务编号', '类型', '金额', '发生时间'], ...this.result.list.map(item => [item.orderNo || item.transactionNo, this.typeText(item), item.amount, item.paidAt || item.createdAt])];
      const blob = new Blob(['\ufeff' + rows.map(row => row.join(',')).join('\n')], { type: 'text/csv' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = `${this.activeTab.label}.csv`;
      link.click();
      URL.revokeObjectURL(link.href);
    }
  }
};
</script>
<style scoped>.finance-tabs{display:flex;gap:4px;margin:16px 0 8px}.finance-tabs button{min-height:42px;padding:0 18px;border:0;border-bottom:2px solid transparent;color:var(--muted);background:transparent}.finance-tabs button.active{border-color:var(--primary);color:var(--primary);font-weight:700}</style>
