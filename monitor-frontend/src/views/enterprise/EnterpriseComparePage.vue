<template><div><PageHeader eyebrow="Usage Comparison" title="企业用量对比" description="选择 2–8 家企业，对比指定周期内的聚合用量。"><button class="layui-btn monitor-secondary" :disabled="!comparison.enterprises?.length" @click="exportCsv">导出数据</button></PageHeader><section class="panel filter-panel"><div class="compare-filter"><div class="field"><label>选择企业（{{selectedIds.length}}/8）</label><div class="company-options"><label v-for="item in options" :key="item.id"><input v-model="selectedIds" type="checkbox" :value="item.id" :disabled="!selectedIds.includes(item.id)&&selectedIds.length>=8"> {{item.name}}</label></div><p v-if="selectionError" class="error-text">{{selectionError}}</p></div><div class="field"><label>统计周期</label><select v-model.number="days" class="layui-select"><option :value="7">近 7 天</option><option :value="14">近 14 天</option><option :value="30">近 30 天</option></select></div><button class="layui-btn monitor-primary" :disabled="loading" @click="compare">{{loading?'加载中':'开始对比'}}</button></div></section><template v-if="comparison.enterprises?.length"><section class="comparison-chart-grid"><article class="panel"><header class="panel-header"><div><h2>系统调用量趋势</h2><p>图例可点击隐藏企业</p></div><span class="tag success">{{comparison.enterprises.length}} 家企业</span></header><div class="panel-body"><LineChart :labels="labels" :series="series" /></div></article><article class="panel"><header class="panel-header"><div><h2>企业用量占比</h2><p>所选周期内系统调用总量占比</p></div></header><div class="panel-body"><PieChart :data="pieData" /></div></article></section><section class="equal-grid compare-summary"><article v-for="(item,index) in cards" :key="item.enterpriseId" class="panel panel-body"><span class="tag">用量第 {{index+1}}</span><h3>{{item.enterpriseName}}</h3><div class="compare-values"><div><span>累计出单</span><strong>{{format(item.workorders)}}</strong></div><div><span>系统调用</span><strong>{{format(item.requests)}}</strong></div><div><span>OCR 使用</span><strong>{{format(item.ocr)}}</strong></div></div></article></section></template><div v-else class="panel empty-state">请选择至少 2 家企业开始对比。</div></div></template>
<script>
import PageHeader from '@/components/PageHeader.vue';
import LineChart from '@/components/LineChart.vue';
import PieChart from '@/components/PieChart.vue';
import { enterpriseApi } from '@/api/monitor';

export default {
  name: 'EnterpriseComparePage',
  components: { PageHeader, LineChart, PieChart },
  /**
   * 保存可选企业、已选企业和对比周期。默认选取三家企业查看最近三十天用量。
   */
  data: () => ({ options: [], selectedIds: [1, 2, 3], days: 30, comparison: { enterprises: [] }, loading: false, selectionError: '' }),
  computed: {
    /**
     * 将对比日期缩短为月日格式，作为趋势图公共横轴。
     */
    labels() { return this.comparison.dates?.map(date => date.slice(5)) || []; },
    /**
     * 为每家企业构造独立系统调用趋势序列，关闭面积填充避免多序列相互遮挡。
     */
    series() { return this.comparison.enterprises.map(item => ({ name: item.enterpriseName, data: item.points.map(point => point.requestCount), area: false })); },
    /**
     * 汇总每家企业在周期内的出单、系统调用和 OCR 用量，并按系统调用量降序生成排名卡片。
     */
    cards() {
      return this.comparison.enterprises.map(item => ({
        enterpriseId: item.enterpriseId, enterpriseName: item.enterpriseName,
        workorders: item.points.reduce((sum, point) => sum + point.workorderCount, 0),
        requests: item.points.reduce((sum, point) => sum + point.requestCount, 0),
        ocr: item.points.reduce((sum, point) => sum + point.ocrCount, 0)
      })).sort((left, right) => right.requests - left.requests);
    },
    /**
     * 将企业调用总量转换为饼图数据，并移除常见公司后缀以缩短图例。
     */
    pieData() { return this.cards.map(item => ({ name: item.enterpriseName.replace(/有限公司$/, ''), value: item.requests })); }
  },
  /**
   * 先加载企业选项，再使用默认选择执行首次对比，避免请求不存在的展示对象。
   */
  mounted() { enterpriseApi.options().then(data => { this.options = data; this.compare(); }); },
  methods: {
    /**
     * 使用中文千分位展示累计业务量。
     */
    format: value => Number(value || 0).toLocaleString('zh-CN'),
    /**
     * 至少选择两家企业后才请求横向对比，并将企业 ID 序列化为接口约定的逗号分隔参数。
     */
    async compare() {
      if (this.selectedIds.length < 2) { this.selectionError = '请至少选择 2 家企业'; return; }
      this.selectionError = '';
      this.loading = true;
      try { this.comparison = await enterpriseApi.compare({ enterpriseIds: this.selectedIds.join(','), days: this.days }); }
      finally { this.loading = false; }
    },
    /**
     * 导出当前对比汇总为带 BOM 的 CSV，并在下载后释放临时对象 URL。
     */
    exportCsv() {
      const rows = [['企业', '累计出单', '系统调用', 'OCR使用'], ...this.cards.map(item => [item.enterpriseName, item.workorders, item.requests, item.ocr])];
      const blob = new Blob(['\ufeff' + rows.map(row => row.join(',')).join('\n')], { type: 'text/csv' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob); link.download = '企业用量对比.csv'; link.click(); URL.revokeObjectURL(link.href);
    }
  }
};
</script>
<style scoped>.compare-filter{display:grid;grid-template-columns:1fr 160px auto;align-items:end;gap:14px}.company-options{display:flex;gap:12px;flex-wrap:wrap;min-height:40px;padding:8px 10px;border:1px solid #cbd5e1;border-radius:6px}.company-options label{white-space:nowrap}.comparison-chart-grid{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(360px,1fr);gap:16px;margin-bottom:16px}.compare-summary{grid-template-columns:repeat(3,1fr)}.compare-summary h3{margin:13px 0}.compare-values{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.compare-values span{display:block;color:var(--muted);font-size:12px}.compare-values strong{display:block;margin-top:5px}@media(max-width:1100px){.comparison-chart-grid{grid-template-columns:1fr}}@media(max-width:900px){.compare-summary,.compare-filter{grid-template-columns:1fr}}</style>
