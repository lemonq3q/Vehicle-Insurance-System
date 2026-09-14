<template>
  <div>
    <PageHeader eyebrow="Usage Comparison" title="企业用量对比" description="按统一指标和统计周期，对最多 20 家企业进行横向分析。" />
    <section class="panel query-panel compare-query-panel">
      <div class="compare-mode-section">
        <div class="mode-switch" role="radiogroup" aria-label="企业选择方式">
          <label :class="['mode-card', { active: mode === 'auto' }]"><input v-model="mode" type="radio" value="auto" @change="changeMode" /><span><strong>系统自动挑选</strong><small>按统计指标挑选 Top 企业，可取消部分企业</small></span></label>
          <label :class="['mode-card', { active: mode === 'custom' }]"><input v-model="mode" type="radio" value="custom" @change="changeMode" /><span><strong>自定义比对</strong><small>搜索企业并按需加入或移除比对范围</small></span></label>
        </div>
      </div>
      <div class="query-toolbar">
        <h2>统计条件</h2>
        <form class="query-filters compare-filters" @submit.prevent="compare">
          <select v-model="metric" class="layui-select" aria-label="统计指标" @change="criteriaChanged"><option value="workorder">出单量</option><option value="request">调用量</option><option value="ocr">OCR 使用</option></select>
          <select v-model="range" class="layui-select" aria-label="统计周期" @change="criteriaChanged"><option value="7d">近 7 天</option><option value="15d">近 15 天</option><option value="30d">近 30 天</option><option value="3m">近 3 个月</option><option value="6m">近 6 个月</option><option value="1y">近 1 年</option></select>
          <input v-if="mode === 'auto'" v-model.number="top" class="layui-input top-input" type="number" min="1" max="20" step="1" aria-label="推荐企业数量" placeholder="Top 数量（1-20）" @change="changeTop" />
          <div v-else class="enterprise-combobox">
            <input id="enterpriseKeyword" v-model="keyword" class="layui-input" type="text" autocomplete="off" placeholder="输入企业名称或编码" aria-label="搜索对比企业" role="combobox" aria-autocomplete="list" :aria-expanded="enterpriseOpen" aria-controls="compare-enterprise-options" @input="onEnterpriseInput" @focus="onEnterpriseFocus" @keydown.esc="enterpriseOpen=false" />
            <i v-if="searching" class="layui-icon layui-icon-loading layui-anim layui-anim-rotate layui-anim-loop" aria-hidden="true"></i>
            <ul v-if="enterpriseOpen" id="compare-enterprise-options" role="listbox"><li v-if="searching" class="option-state">正在搜索…</li><li v-else-if="!availableSearchResults.length" class="option-state">没有可添加的匹配企业</li><li v-for="item in availableSearchResults" :key="item.id" role="option" tabindex="0" @mousedown.prevent="addEnterprise(item)" @keydown.enter.prevent="addEnterprise(item)"><strong>{{ item.name }}</strong><small>{{ item.code }}</small></li></ul>
          </div>
          <button class="layui-btn monitor-primary" :disabled="loading || selecting">{{ loading ? "分析中…" : "开始分析" }}</button>
        </form>
      </div>
      <div class="selection-row">
        <span class="selection-label">已选企业（{{ selectedEnterprises.length }}/20）</span>
        <div v-if="selecting" class="selection-note">正在计算 Top 企业…</div>
        <div v-else-if="mode === 'auto'" class="company-options"><label v-for="item in recommended" :key="item.id"><input v-model="selectedIds" type="checkbox" :value="item.id" />{{ item.name }}<small>{{ format(item.metricValue) }} {{ metricUnit }}</small></label></div>
        <div v-else-if="selectedEnterprises.length" class="selected-list" aria-label="已选企业"><span v-for="item in selectedEnterprises" :key="item.id" class="selected-chip">{{ item.name }}<button type="button" :aria-label="`移除${item.name}`" @click="removeEnterprise(item.id)">×</button></span></div>
        <span v-else class="selection-note">请通过上方输入框搜索并添加企业</span>
      </div>
      <p v-if="selectionError" class="error-text" role="alert">{{ selectionError }}</p>
    </section>
    <div v-if="loading" class="panel loading-state">正在加载企业用量对比…</div>
    <template v-else-if="comparison.enterprises.length">
      <section class="comparison-chart-grid">
        <article class="panel"><header class="panel-header"><div><h2>{{ metricLabel }}趋势</h2><p>{{ rangeLabel }} · 图例可点击隐藏企业</p></div><span class="tag success">{{ comparison.enterprises.length }} 家企业</span></header><div class="panel-body"><LineChart :labels="chartLabels" :series="series" :aria-label="`${metricLabel}企业趋势对比图`" /></div></article>
        <article class="panel"><header class="panel-header"><div><h2>{{ metricLabel }}占比</h2><p>{{ rangeLabel }}内所选企业的{{ metricLabel }}占比</p></div></header><div class="panel-body"><PieChart :data="pieData" :title="`总${metricLabel}`" :series-name="metricLabel" /></div></article>
      </section>
      <section class="equal-grid compare-summary"><article v-for="(item, index) in cards" :key="item.enterpriseId" class="panel panel-body"><div class="compare-card-header"><span class="tag">{{ metricLabel }}第 {{ index + 1 }}</span><router-link class="layui-btn layui-btn-sm monitor-secondary" :to="`/enterprises/${item.enterpriseId}/overview`">查看详情</router-link></div><h3>{{ item.enterpriseName }}</h3><div class="compare-values"><div :class="{ active: metric === 'workorder' }"><span>累计出单</span><strong>{{ format(item.workorders) }}</strong></div><div :class="{ active: metric === 'request' }"><span>系统调用</span><strong>{{ format(item.requests) }}</strong></div><div :class="{ active: metric === 'ocr' }"><span>OCR 使用</span><strong>{{ format(item.ocr) }}</strong></div></div></article></section>
    </template>
    <div v-else class="panel empty-state">请选择至少 1 家企业并开始分析。</div>
    <AppToast :message="toastMessage" :type="toastType" />
  </div>
</template>
<script>
import PageHeader from "@/components/PageHeader.vue";
import LineChart from "@/components/LineChart.vue";
import PieChart from "@/components/PieChart.vue";
import AppToast from "@/components/AppToast.vue";
import feedback from "@/mixins/feedback";
import { enterpriseApi } from "@/api/monitor";

const METRICS = { workorder: { label: "出单量", field: "workorderCount", unit: "单" }, request: { label: "调用量", field: "requestCount", unit: "次" }, ocr: { label: "OCR 使用", field: "ocrCount", unit: "次" } };
const RANGE_LABELS = { "7d": "近 7 天", "15d": "近 15 天", "30d": "近 30 天", "3m": "近 3 个月", "6m": "近 6 个月", "1y": "近 1 年" };

export default {
  name: "EnterpriseComparePage", components: { PageHeader, LineChart, PieChart, AppToast }, mixins: [feedback],
  /** 保存两种企业选择模式、统一统计口径、候选企业和最近一次后端对比结果。 */
  data: () => ({ mode: "auto", metric: "request", range: "30d", top: 5, recommended: [], customEnterprises: [], selectedIds: [], keyword: "", searchResults: [], enterpriseOpen: false, enterpriseSearchTimer: null, selecting: false, searching: false, loading: false, selectionError: "", comparison: { enterprises: [] } }),
  computed: {
    /** 当前模式下解析已选 ID 对应的企业集合，两种选择来源互相隔离。 */
    selectedEnterprises() { const source = this.mode === "auto" ? this.recommended : this.customEnterprises; return this.selectedIds.map(id => source.find(item => item.id === id)).filter(Boolean); },
    /** 搜索下拉隐藏已经加入对比的企业，防止重复选择并保持与提醒页面相同的候选结构。 */
    availableSearchResults() { return this.searchResults.filter(item => !this.isSelected(item.id)); },
    metricMeta() { return METRICS[this.metric]; }, metricLabel() { return this.metricMeta.label; }, metricUnit() { return this.metricMeta.unit; }, rangeLabel() { return RANGE_LABELS[this.range]; },
    /** 根据后端粒度缩短日期显示，但不改变真实数据点顺序。 */
    chartLabels() { return (this.comparison.labels || []).map(label => label.length === 10 ? label.slice(5) : label); },
    /** 所有趋势序列只读取当前统计指标对应字段，避免图表混用统计口径。 */
    series() { return this.comparison.enterprises.map(item => ({ name: item.enterpriseName, data: item.points.map(point => Number(point[this.metricMeta.field] || 0)), area: false })); },
    /** 汇总三项企业用量供详情卡完整展示，同时只使用当前选中指标决定卡片排名。 */
    cards() { return this.comparison.enterprises.map(item => { const totals = item.points.reduce((result, point) => ({ workorders: result.workorders + Number(point.workorderCount || 0), requests: result.requests + Number(point.requestCount || 0), ocr: result.ocr + Number(point.ocrCount || 0) }), { workorders: 0, requests: 0, ocr: 0 }); return { enterpriseId: item.enterpriseId, enterpriseName: item.enterpriseName, ...totals, value: totals[this.metric === "workorder" ? "workorders" : this.metric === "request" ? "requests" : "ocr"] }; }).sort((left, right) => right.value - left.value); },
    pieData() { return this.cards.map(item => ({ name: item.enterpriseName.replace(/有限公司$/, ""), value: item.value })); },
  },
  /** 按默认调用量、近三十天和 Top5 获取推荐企业并完成首轮分析。 */
  mounted() { this.loadTop(true); },
  /** 页面销毁时取消尚未发起的企业联想请求，避免离开页面后继续更新状态。 */
  beforeUnmount() { clearTimeout(this.enterpriseSearchTimer); },
  methods: {
    format: value => Number(value || 0).toLocaleString("zh-CN"), isSelected(id) { return this.selectedIds.includes(id); },
    /** 切换模式时清空前一模式选择和结果，防止企业来源混淆。 */
    changeMode() { this.selectedIds = []; this.comparison = { enterprises: [] }; this.selectionError = ""; this.clearEnterpriseSearch(); if (this.mode === "auto") this.loadTop(true); },
    /** 统计口径变化时移除旧图；自动模式重算 Top，自定义模式保留企业并刷新。 */
    criteriaChanged() { this.comparison = { enterprises: [] }; if (this.mode === "auto") this.loadTop(true); else if (this.selectedIds.length >= 1) this.compare(); },
    /** 读取真实用量排行并默认勾选全部结果，随后允许用户取消企业。 */
    async loadTop(autoCompare = false) { const top = Number(this.top); if (!Number.isInteger(top) || top < 1 || top > 20) { this.selectionError = "Top 数量请输入 1 至 20 的整数"; return; } this.selecting = true; this.selectionError = ""; try { this.recommended = await enterpriseApi.usageTop({ metric: this.metric, range: this.range, top }); this.selectedIds = this.recommended.map(item => item.id); if (autoCompare && this.selectedIds.length >= 1) await this.compare(); } catch (error) { this.errorMessage(error); } finally { this.selecting = false; } },
    /** 输入变化后使用与提醒处理页一致的短防抖查询企业，空关键词不请求企业全集。 */
    onEnterpriseInput() { clearTimeout(this.enterpriseSearchTimer); const keyword = this.keyword.trim(); if (!keyword) { this.searchResults = []; this.enterpriseOpen = false; return; } this.enterpriseSearchTimer = setTimeout(() => this.searchEnterpriseOptions(keyword), 250); },
    /** 输入框重新获得焦点时恢复已有候选，不额外发起重复请求。 */
    onEnterpriseFocus() { if (this.keyword.trim() && this.searchResults.length) this.enterpriseOpen = true; },
    /** 查询最多二十条企业候选，并丢弃输入变化后返回的过期响应。 */
    async searchEnterpriseOptions(keyword) { this.searching = true; this.enterpriseOpen = true; try { const items = await enterpriseApi.options(keyword); if (keyword === this.keyword.trim()) this.searchResults = items; } catch (error) { this.searchResults = []; this.errorMessage(error); } finally { if (keyword === this.keyword.trim()) this.searching = false; } },
    /** 清空搜索输入、候选和下拉状态，但不影响已经加入对比的企业。 */
    clearEnterpriseSearch() { clearTimeout(this.enterpriseSearchTimer); this.keyword = ""; this.searchResults = []; this.enterpriseOpen = false; this.searching = false; },
    /** 将搜索结果加入自定义集合，数量上限与后端校验保持一致。 */
    addEnterprise(item) { if (this.isSelected(item.id) || this.selectedIds.length >= 20) { if (this.selectedIds.length >= 20) this.selectionError = "最多选择 20 家企业"; return; } this.customEnterprises.push(item); this.selectedIds.push(item.id); this.selectionError = ""; this.clearEnterpriseSearch(); },
    /** 将手工输入的 Top 数量限制为 1 至 20 的整数，通过校验后重新读取推荐企业。 */
    changeTop() { const value = Number(this.top); if (!Number.isInteger(value) || value < 1 || value > 20) { this.selectionError = "Top 数量请输入 1 至 20 的整数"; return; } this.top = value; this.loadTop(true); },
    /** 移除企业时保留当前结果，直到用户再次点击分析。 */
    removeEnterprise(id) { this.selectedIds = this.selectedIds.filter(value => value !== id); if (this.mode === "custom") this.customEnterprises = this.customEnterprises.filter(item => item.id !== id); },
    /** 校验数量后请求真实后端，返回结果是趋势、占比和排行的唯一数据源。 */
    async compare() { if (this.selectedIds.length < 1) { this.selectionError = "请至少选择 1 家企业"; return; } this.selectionError = ""; this.loading = true; try { this.comparison = await enterpriseApi.compare({ enterpriseIds: this.selectedIds.join(","), range: this.range }); } catch (error) { this.errorMessage(error); } finally { this.loading = false; } },
  },
};
</script>
<style scoped>
.compare-query-panel{padding-bottom:18px}.compare-mode-section{display:flex;align-items:center;justify-content:space-between;gap:20px;padding-bottom:18px;margin-bottom:16px;border-bottom:1px solid var(--border)}.compare-mode-heading{flex:0 0 auto}.compare-mode-heading h2{margin:0;font-size:18px}.compare-mode-heading p{margin:3px 0 0;color:var(--muted);font-size:12px}.mode-switch{display:grid;grid-template-columns:repeat(2,minmax(220px,280px));gap:10px}.mode-card{display:flex;align-items:flex-start;gap:9px;padding:11px 13px;border:1px solid #cbd5e1;border-radius:7px;cursor:pointer}.mode-card.active{border-color:var(--primary);background:#eff6ff}.mode-card input{margin-top:4px}.mode-card span{display:grid;gap:2px}.mode-card small{color:var(--muted);font-size:12px}.selection-row{display:flex;align-items:flex-start;gap:14px;padding-top:16px}.selection-label{flex:0 0 auto;padding-top:5px;color:var(--text-secondary);font-size:12px;font-weight:600}.selection-note{padding-top:5px;color:var(--muted)}.company-options{display:flex;flex:1;flex-wrap:wrap;gap:8px 16px}.company-options label{display:inline-flex;align-items:center;gap:6px;min-height:30px;white-space:nowrap;cursor:pointer}.company-options small{color:var(--muted)}.enterprise-combobox{position:relative;width:220px}.enterprise-combobox>.layui-input{width:100%;padding-right:36px}.enterprise-combobox>i{position:absolute;z-index:2;top:0;right:0;display:grid;place-items:center;width:36px;height:38px;color:var(--muted)}.enterprise-combobox>ul{position:absolute;z-index:40;top:calc(100% + 5px);right:0;left:0;max-height:260px;margin:0;padding:5px;overflow:auto;border:1px solid var(--border);border-radius:7px;background:#fff;box-shadow:0 12px 28px rgba(15,23,42,.14);list-style:none}.enterprise-combobox li{display:flex;flex-direction:column;min-height:44px;padding:7px 9px;border-radius:5px;cursor:pointer}.enterprise-combobox li:hover,.enterprise-combobox li:focus{outline:none;background:#eff6ff}.enterprise-combobox li small{color:var(--muted)}.enterprise-combobox .option-state{justify-content:center;color:var(--muted);cursor:default}.selected-list{display:flex;flex-wrap:wrap;gap:8px}.selected-chip{display:inline-flex;align-items:center;gap:5px;min-height:30px;padding:2px 5px 2px 10px;border:1px solid var(--border);border-radius:5px;background:#f8fafc;color:var(--text-secondary)}.selected-chip button{display:grid;place-items:center;width:24px;height:24px;padding:0;border:0;background:transparent;color:var(--muted);font-size:17px}.selected-chip button:hover{color:var(--danger)}.compare-query-panel>.error-text{margin:8px 0 0 104px}.comparison-chart-grid{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(340px,1fr);gap:16px;margin-bottom:16px}.compare-summary{grid-template-columns:repeat(3,1fr)}.compare-summary h3{margin:13px 0 6px}.primary-value{display:flex;align-items:baseline;gap:6px}.primary-value strong{font-size:24px}.primary-value span{color:var(--muted)}@media(max-width:1000px){.compare-mode-section{align-items:stretch;flex-direction:column}.mode-switch{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:900px){.comparison-chart-grid,.compare-summary{grid-template-columns:1fr}}@media(max-width:720px){.mode-switch{grid-template-columns:1fr}.compare-filters .enterprise-combobox{width:100%}.selection-row{flex-direction:column;gap:8px}.selection-label{padding-top:0}.compare-query-panel>.error-text{margin-left:0}}
.compare-mode-section{display:block}.compare-mode-heading{display:block}.compare-mode-heading h2{margin-bottom:12px}.mode-switch{width:100%;grid-template-columns:repeat(2,minmax(0,1fr))}.compare-filters .top-input{width:130px;height:38px}
.compare-values{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.compare-values span{display:block;color:var(--muted);font-size:12px}.compare-values strong{display:block;margin-top:5px;font-variant-numeric:tabular-nums}.compare-values>div.active span{color:var(--primary);font-weight:700}
.compare-card-header{display:flex;align-items:center;justify-content:space-between;gap:10px}
@media(max-width:720px){.compare-filters .top-input{width:100%}}
</style>
