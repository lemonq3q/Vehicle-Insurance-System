<template><div><PageHeader eyebrow="Enterprise Detail" title="订单与流水" :description="`${enterprise?.name||'企业'} · 财务记录统一查询`"><button class="layui-btn monitor-secondary" @click="exportCsv">导出当前记录</button></PageHeader><EnterpriseNav/><section class="panel"><div class="summary-strip"><div class="summary-item"><span>账户余额</span><strong>{{money(summary.balance)}}</strong></div><div class="summary-item"><span>累计充值</span><strong>{{money(summary.totalRecharge)}}</strong></div><div class="summary-item"><span>累计套餐支出</span><strong>{{money(summary.totalSubscriptionExpense)}}</strong></div><div class="summary-item"><span>本月流水笔数</span><strong>{{summary.monthTransactionCount}}</strong></div></div></section><div class="finance-tabs" role="tablist"><button v-for="tab in tabs" :key="tab.key" :class="{active:active===tab.key}" @click="switchTab(tab.key)">{{tab.label}}</button></div><section class="panel"><header class="panel-header finance-panel-header"><div><h2>{{activeTab.label}}</h2><p>财务信息可查看，不提供修改订单功能</p></div><form class="finance-filters" @submit.prevent="search"><input v-model.trim="query.businessNo" class="layui-input" type="search" maxlength="64" :placeholder="businessNoPlaceholder" aria-label="业务编号"/><div class="finance-date-range"><el-config-provider :locale="elementLocale"><el-date-picker v-model="dateRange" class="monitor-date-range" type="daterange" unlink-panels range-separator="到" start-placeholder="开始日期" end-placeholder="结束日期" format="YYYY/MM/DD" value-format="YYYY-MM-DD" :clearable="true" placement="bottom-start" :fallback-placements="['bottom-start']" popper-class="monitor-date-range-popper" aria-label="财务记录时间范围"/></el-config-provider></div><button class="layui-btn monitor-primary" :disabled="loading">查询</button><button class="layui-btn monitor-secondary" type="button" :disabled="loading" @click="reset">重置</button></form></header><div v-if="loading" class="loading-state">正在加载财务记录…</div><div v-else-if="!result.list.length" class="empty-state">当前筛选条件下暂无记录。</div><template v-else><div class="table-wrap"><table class="layui-table monitor-table"><thead><tr><th>业务编号</th><th>业务类型</th><th>金额</th><th>状态/余额</th><th>关联信息</th><th>发生时间</th></tr></thead><tbody><tr v-for="item in result.list" :key="item.id"><td>{{item.orderNo||item.transactionNo}}</td><td>{{typeText(item)}}</td><td>{{money(item.amount)}}</td><td>{{statusText(item)}}</td><td>{{item.channel||item.referenceNo||`${item.startedAt} 至 ${item.endedAt}`}}</td><td>{{item.paidAt||item.createdAt}}</td></tr></tbody></table></div><AppPagination :page-no="result.pageNo" :page-size="result.pageSize" :total="result.total" @change="changePage" /></template></section></div></template>
<script>
import PageHeader from '@/components/PageHeader.vue';
import AppPagination from '@/components/AppPagination.vue';
import EnterpriseNav from './EnterpriseNav.vue';
import { ElConfigProvider, ElDatePicker } from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import 'element-plus/es/components/date-picker/style/css';
import { enterpriseApi } from '@/api/monitor';

export default {
  name: 'EnterpriseFinancePage',
  components: { PageHeader, AppPagination, EnterpriseNav, ElConfigProvider, ElDatePicker },
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
    elementLocale: zhCn,
    query: { businessNo: '', startDate: '', endDate: '' },
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
    activeTab() { return this.tabs.find(item => item.key === this.active); },
    /** 根据当前页签提示用户输入对应的充值单号、订阅单号或钱包流水号。 */
    businessNoPlaceholder() { return this.active === 'wallet-transactions' ? '请输入钱包流水号' : this.active === 'recharge-orders' ? '请输入充值订单号' : '请输入订阅订单号'; },
    /** 将日期范围组件数组映射为后端稳定的起止日期参数。 */
    dateRange: {
      get() { return this.query.startDate && this.query.endDate ? [this.query.startDate, this.query.endDate] : []; },
      set(value) { this.query.startDate = value?.[0] || ''; this.query.endDate = value?.[1] || ''; }
    }
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
        const params = { ...this.query, pageNo: this.result.pageNo, pageSize: this.result.pageSize };
        this.result = await enterpriseApi.finance(this.id, this.active, Object.fromEntries(Object.entries(params).filter(([, value]) => value !== '')));
      } finally { this.loading = false; }
    },
    /** 应用业务编号和时间范围时回到第一页，确保缩小结果集后页码仍有效。 */
    search() { this.result.pageNo = 1; this.load(); },
    /** 清空当前页签的全部筛选条件并重新读取第一页。 */
    reset() { this.query = { businessNo: '', startDate: '', endDate: '' }; this.result.pageNo = 1; this.load(); },
    /**
     * 切换财务标签时回到第一页，避免沿用另一资源的分页位置。
     */
    switchTab(key) { this.active = key; this.query = { businessNo: '', startDate: '', endDate: '' }; this.result.pageNo = 1; this.load(); },
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
<style scoped>.finance-tabs{display:flex;gap:4px;margin:16px 0 8px}.finance-tabs button{min-height:42px;padding:0 18px;border:0;border-bottom:2px solid transparent;color:var(--muted);background:transparent}.finance-tabs button.active{border-color:var(--primary);color:var(--primary);font-weight:700}
/*
 * 查询表单与当前财务类型标题共享面板头部：桌面端靠右排列，空间不足时整体换到下一行，
 * 避免压缩标题说明或破坏日期范围控件的固定宽度。数量信息由分页区域承载，标题栏不再重复展示。
 */
.finance-panel-header{flex-wrap:wrap}.finance-filters{display:flex;align-items:center;justify-content:flex-end;gap:10px;flex-wrap:wrap;margin-left:auto}.finance-filters>.layui-input{width:220px}.finance-filters .layui-btn{margin:0}
/*
 * 充值订单、订阅订单和钱包流水共用当前筛选区。日期范围控件固定复用审计日志的 320px 桌面宽度，
 * 同时禁止 flex 布局将其拉伸到剩余空间；窄屏下再切换为整行宽度，保证日期内容和操作按钮可用。
 */
.finance-date-range{flex:0 0 320px;width:320px;min-width:320px;max-width:320px}
.finance-date-range :deep(.monitor-date-range.el-date-editor){width:100%!important;min-width:0;max-width:100%;--el-date-editor-width:100%}
@media(max-width:620px){.finance-filters{width:100%;align-items:stretch;justify-content:flex-start;margin-left:0}.finance-filters>.layui-input{width:100%}.finance-date-range{flex:1 1 100%;width:100%;min-width:0;max-width:none}}</style>
