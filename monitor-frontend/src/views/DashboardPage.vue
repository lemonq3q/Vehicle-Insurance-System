<template>
  <div>
    <PageHeader eyebrow="Operations Overview" title="运营仪表盘" description="全平台经营与系统用量概览，统计数据按自然日归档。">
      <button class="layui-btn monitor-primary" :disabled="refreshing" @click="refreshAll"><i class="layui-icon layui-icon-refresh" :class="{ 'layui-anim layui-anim-rotate layui-anim-loop': refreshing }" aria-hidden="true"></i>{{ refreshing ? '刷新中' : '刷新数据' }}</button>
    </PageHeader>

    <section v-if="summary" class="dashboard-metrics" aria-label="平台本月核心指标">
      <article v-for="item in metrics" :key="item.key" class="panel dashboard-metric">
        <header><span class="metric-icon"><i :class="item.icon" aria-hidden="true"></i></span><small>{{ item.period }}</small></header>
        <p>{{ item.label }}</p>
        <strong :class="{ money: item.money }">{{ item.money ? formatMoney(item.metric.current) : formatNumber(item.metric.current) }}</strong>
        <footer :class="trendClass(item.metric.comparison)"><span>{{ trendText(item.metric.comparison) }}</span><small>上月 {{ item.money ? formatMoney(item.metric.previous) : formatNumber(item.metric.previous) }}</small></footer>
      </article>
    </section>
    <section v-else class="dashboard-metrics" aria-label="正在加载核心指标"><article v-for="index in 5" :key="index" class="panel dashboard-metric metric-skeleton"><span></span><strong></strong><small></small></article></section>

    <p v-if="summaryError" class="dashboard-error" role="alert">{{ summaryError }}</p>

    <section class="panel dashboard-section revenue-panel">
      <header class="panel-header"><div><h2>系统充值流水</h2><p>近 12 个月已支付充值订单金额，不受其他图表时间范围影响</p></div><span class="tag success">按月统计</span></header>
      <div class="panel-body chart-body"><LineChart v-if="rechargeTrend" :labels="pointLabels(rechargeTrend)" :series="[{ name: '充值流水', data: pointValues(rechargeTrend), area: false }]" type="bar" aria-label="近十二个月系统充值流水柱状图"/><div v-else class="chart-loading">{{ rechargeError || '正在加载充值流水…' }}</div></div>
    </section>

    <section class="usage-grid">
      <article class="panel dashboard-section">
        <header class="panel-header chart-header"><div><h2>系统调用趋势</h2><p>{{ intervalText(requestTrend?.interval) }}汇总企业鉴权业务 API 调用</p></div><select v-model="requestRange" class="layui-select range-select" aria-label="系统调用趋势统计范围" @change="loadRequestTrend"><option v-for="option in rangeOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></header>
        <div class="panel-body chart-body"><LineChart v-if="requestTrend" :labels="pointLabels(requestTrend)" :series="[{ name: '系统调用', data: pointValues(requestTrend), area: false }]" aria-label="系统调用趋势折线图"/><div v-else class="chart-loading">{{ requestError || '正在加载系统调用趋势…' }}</div></div>
      </article>
      <article class="panel dashboard-section">
        <header class="panel-header chart-header"><div><h2>OCR 使用趋势</h2><p>{{ intervalText(ocrTrend?.interval) }}汇总实际发起的 OCR 供应商调用</p></div><select v-model="ocrRange" class="layui-select range-select" aria-label="OCR 使用趋势统计范围" @change="loadOcrTrend"><option v-for="option in rangeOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></header>
        <div class="panel-body chart-body"><LineChart v-if="ocrTrend" :labels="pointLabels(ocrTrend)" :series="[{ name: 'OCR 用量', data: pointValues(ocrTrend), area: false }]" aria-label="OCR 使用趋势折线图"/><div v-else class="chart-loading">{{ ocrError || '正在加载 OCR 趋势…' }}</div></div>
      </article>
    </section>

    <section class="panel dashboard-section ranking-panel">
      <header class="panel-header ranking-header"><div><h2>企业用量排行</h2><p>{{ selectedRangeLabel(rankingRange) }}系统调用量 Top {{ rankingTop }}</p></div><div class="ranking-filters"><label>统计范围<select v-model="rankingRange" class="layui-select range-select" @change="loadRanking"><option v-for="option in rangeOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select></label><label>排行数量<select v-model.number="rankingTop" class="layui-select top-select" @change="loadRanking"><option :value="5">Top 5</option><option :value="10">Top 10</option><option :value="20">Top 20</option></select></label></div></header>
      <div v-if="ranking" class="table-wrap"><table class="layui-table monitor-table ranking-table"><thead><tr><th>排名</th><th>企业</th><th>区间系统调用量</th><th>操作</th></tr></thead><tbody><tr v-for="(row,index) in ranking.items" :key="row.enterpriseId"><td><span class="rank-number" :class="`rank-${index + 1}`">{{ index + 1 }}</span></td><td>{{ row.enterpriseName }}</td><td class="number"><strong>{{ formatNumber(row.requestCount) }}</strong></td><td><router-link class="layui-btn layui-btn-xs monitor-secondary" :to="`/enterprises/${row.enterpriseId}/overview`">查看企业</router-link></td></tr><tr v-if="!ranking.items.length"><td colspan="4" class="empty-cell">当前范围暂无企业调用数据</td></tr></tbody></table></div>
      <div v-else class="ranking-loading">{{ rankingError || '正在加载企业排行…' }}</div>
    </section>
  </div>
</template>

<script>
import PageHeader from '@/components/PageHeader.vue';
import LineChart from '@/components/LineChart.vue';
import { dashboardApi } from '@/api/monitor';

/**
 * 运营仪表盘按可独立刷新的区域维护状态：卡片、固定十二月充值、系统调用、OCR 与企业排行。
 * 三个范围选择器互不影响，顶部刷新并行请求所有区域，避免串行接口增加页面等待时间。
 */
export default {
  name: 'DashboardPage',
  components: { PageHeader, LineChart },
  data() {
    return {
      summary: null, rechargeTrend: null, requestTrend: null, ocrTrend: null, ranking: null,
      summaryError: '', rechargeError: '', requestError: '', ocrError: '', rankingError: '',
      refreshing: false, requestRange: '30d', ocrRange: '30d', rankingRange: '30d', rankingTop: 5,
      rangeOptions: [
        { value: '7d', label: '近 7 天' }, { value: '15d', label: '近 15 天' },
        { value: '30d', label: '近 30 天' }, { value: '3m', label: '近 3 个月' },
        { value: '6m', label: '近 6 个月' }, { value: '1y', label: '近 1 年' }
      ]
    };
  },
  computed: {
    /** 将后端五项统一指标结构映射为沿用站点蓝色体系的增强卡片。 */
    metrics() {
      if (!this.summary) return [];
      return [
        { key: 'enterprise', label: '当前企业总数', period: '当前', icon: 'layui-icon layui-icon-template-1', metric: this.summary.enterprise },
        { key: 'workorder', label: '本月已处理工单', period: '本月', icon: 'layui-icon layui-icon-form', metric: this.summary.workorder },
        { key: 'recharge', label: '本月充值流水', period: '本月', icon: 'layui-icon layui-icon-rmb', metric: this.summary.recharge, money: true },
        { key: 'request', label: '本月系统调用', period: '本月', icon: 'layui-icon layui-icon-engine', metric: this.summary.request },
        { key: 'ocr', label: '本月 OCR 用量', period: '本月', icon: 'layui-icon layui-icon-camera-fill', metric: this.summary.ocr }
      ];
    }
  },
  mounted() { this.refreshAll(); },
  methods: {
    /** 将计数格式化为中文千分位，后端 BigDecimal 或字符串均可安全转换。 */
    formatNumber(value) { return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 }); },
    /** 将已支付充值额格式化为人民币两位小数。 */
    formatMoney(value) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`; },
    /** 将环比方向转换为包含语义的文字，避免仅依赖颜色表达变化。 */
    trendText(comparison = {}) {
      if (comparison.direction === 'NEW') return '较上月新增';
      if (comparison.direction === 'FLAT') return '与上月持平';
      return `较上月${comparison.direction === 'UP' ? '增加' : '减少'} ${comparison.rate || 0}%`;
    },
    /** 为上涨、下降和中性环比分配状态类，文字本身仍保留方向。 */
    trendClass(comparison = {}) { return { down: comparison.direction === 'DOWN', neutral: ['FLAT', 'NEW'].includes(comparison.direction) }; },
    /** 从标准趋势响应中提取横轴标签。 */
    pointLabels(trend) { return trend.points.map(point => point.label); },
    /** 从标准趋势响应中提取数值序列。 */
    pointValues(trend) { return trend.points.map(point => Number(point.value || 0)); },
    /** 将后端自动选择的粒度转换为图表说明文案。 */
    intervalText(interval) { return ({ DAY: '按日', WEEK: '按周', MONTH: '按月' })[interval] || '自动粒度'; },
    /** 返回时间范围选择器的当前中文标签。 */
    selectedRangeLabel(value) { return this.rangeOptions.find(option => option.value === value)?.label || ''; },
    /**
     * 并行刷新五个独立数据区域。某一区域失败只在对应位置提示，其他已成功区域不会被清空，
     * 避免一次局部数据库异常让整个运营首页不可用。
     */
    async refreshAll() {
      if (this.refreshing) return;
      this.refreshing = true;
      await Promise.allSettled([this.loadSummary(), this.loadRechargeTrend(), this.loadRequestTrend(), this.loadOcrTrend(), this.loadRanking()]);
      this.refreshing = false;
    },
    /** 读取一次聚合 SQL 产生的五项卡片指标。 */
    async loadSummary() { this.summaryError = ''; try { this.summary = await dashboardApi.summary(); } catch (error) { this.summaryError = error.message; } },
    /** 读取固定近十二月充值流水，不接受页面其他范围选择器。 */
    async loadRechargeTrend() { this.rechargeError = ''; try { this.rechargeTrend = await dashboardApi.rechargeTrend(); } catch (error) { this.rechargeError = error.message; } },
    /** 按系统调用专属范围读取后端自动分粒度趋势。 */
    async loadRequestTrend() { this.requestError = ''; try { this.requestTrend = await dashboardApi.usageTrend({ metric: 'request', range: this.requestRange }); } catch (error) { this.requestError = error.message; } },
    /** 按 OCR 专属范围读取后端自动分粒度趋势。 */
    async loadOcrTrend() { this.ocrError = ''; try { this.ocrTrend = await dashboardApi.usageTrend({ metric: 'ocr', range: this.ocrRange }); } catch (error) { this.ocrError = error.message; } },
    /** 按排行专属范围和 Top 数量读取单次数据库聚合结果。 */
    async loadRanking() { this.rankingError = ''; try { this.ranking = await dashboardApi.ranking({ range: this.rankingRange, top: this.rankingTop }); } catch (error) { this.rankingError = error.message; } }
  }
};
</script>

<style scoped>
.dashboard-metrics { display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:14px;margin-bottom:18px }.dashboard-metric { position:relative;min-height:174px;padding:18px;overflow:hidden;border-top:3px solid var(--primary);transition:transform 180ms ease,box-shadow 180ms ease }.dashboard-metric::after { position:absolute;right:-30px;bottom:-38px;width:104px;height:104px;border-radius:50%;background:rgba(37,99,235,.06);content:'' }.dashboard-metric:hover { transform:translateY(-2px);box-shadow:0 15px 32px rgba(15,23,42,.10) }.dashboard-metric header { position:relative;z-index:1;display:flex;align-items:center;justify-content:space-between }.dashboard-metric header small { color:var(--muted);font-weight:600 }.metric-icon { display:grid;place-items:center;width:36px;height:36px;border-radius:9px;color:var(--primary);background:#eff6ff;font-size:18px }.dashboard-metric>p { position:relative;z-index:1;margin:14px 0 5px;color:var(--muted);font-size:13px }.dashboard-metric>strong { position:relative;z-index:1;display:block;font-size:27px;line-height:1.2;font-variant-numeric:tabular-nums }.dashboard-metric>strong.money { font-size:23px }.dashboard-metric footer { position:relative;z-index:1;display:flex;flex-direction:column;gap:1px;margin-top:11px;color:var(--success);font-size:12px }.dashboard-metric footer.down { color:var(--danger) }.dashboard-metric footer.neutral { color:var(--muted) }.dashboard-metric footer small { color:var(--muted) }
.dashboard-section { margin-bottom:18px }.revenue-panel .chart-body { min-height:330px }.usage-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px }.chart-header { align-items:flex-start }.range-select { width:116px;flex:none }.chart-body { min-height:322px }.chart-loading,.ranking-loading { display:grid;place-items:center;min-height:286px;color:var(--muted) }.ranking-header { align-items:flex-end }.ranking-filters { display:flex;align-items:flex-end;gap:10px }.ranking-filters label { display:flex;flex-direction:column;gap:5px;color:var(--muted);font-size:11px }.top-select { width:98px }.ranking-table { margin:0 }.rank-number { display:grid;place-items:center;width:28px;height:28px;border-radius:7px;color:var(--text-secondary);background:var(--surface-soft);font-weight:700 }.rank-1 { color:#92400e;background:#fef3c7 }.rank-2 { color:#475569;background:#e2e8f0 }.rank-3 { color:#9a3412;background:#ffedd5 }.empty-cell { height:120px!important;color:var(--muted);text-align:center!important }.dashboard-error { padding:10px 14px;margin:-7px 0 18px;border:1px solid #fecaca;border-radius:8px;color:var(--danger);background:#fef2f2 }.metric-skeleton>span,.metric-skeleton>strong,.metric-skeleton>small { display:block;border-radius:6px;background:#e8edf4 }.metric-skeleton>span { width:36px;height:36px }.metric-skeleton>strong { width:58%;height:29px;margin-top:22px }.metric-skeleton>small { width:76%;height:14px;margin-top:14px }
@media(max-width:1300px){.dashboard-metrics{grid-template-columns:repeat(3,1fr)}}@media(max-width:900px){.dashboard-metrics{grid-template-columns:repeat(2,1fr)}.usage-grid{grid-template-columns:1fr}}@media(max-width:620px){.dashboard-metrics{grid-template-columns:1fr}.dashboard-metric{min-height:158px}.chart-header,.ranking-header{align-items:stretch;flex-direction:column}.range-select,.top-select{width:100%}.ranking-filters{display:grid;grid-template-columns:1fr 1fr;width:100%}}
</style>
