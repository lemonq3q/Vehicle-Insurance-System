<template><div><PageHeader eyebrow="Operations Overview" title="运营仪表盘" description="全平台核心经营与系统用量概览，统计日按每日 04:00 切换。"><select v-model.number="days" class="layui-select mini-select" @change="load"><option :value="7">近 7 天</option><option :value="14">近 14 天</option><option :value="30">近 30 天</option></select><button class="layui-btn monitor-primary" :disabled="loading" @click="load"><i class="layui-icon layui-icon-refresh"></i> {{loading?'加载中':'刷新数据'}}</button></PageHeader><div v-if="error" class="panel empty-state"><div><p>{{error}}</p><button class="layui-btn monitor-primary" @click="load">重新加载</button></div></div><template v-else-if="data"><section class="metrics-grid"><article v-for="item in metrics" :key="item.label" class="panel metric-card"><span class="metric-label">{{item.label}}</span><div class="metric-value">{{item.value}}</div><span class="metric-note">{{item.note}}</span></article></section><section class="chart-grid"><article class="panel"><header class="panel-header"><div><h2>系统调用趋势</h2><p>每日业务后端调用次数</p></div><span class="tag success">更新于 {{data.updatedAt.slice(11,16)}}</span></header><div class="panel-body"><LineChart :labels="labels" :series="requestSeries" /></div></article><article class="panel"><header class="panel-header"><div><h2>OCR 使用趋势</h2><p>供应商实际调用次数</p></div><span class="tag">日统计</span></header><div class="panel-body"><LineChart :labels="labels" :series="ocrSeries" type="bar" /></div></article></section><section class="panel"><header class="panel-header"><div><h2>企业用量排行</h2><p>当前统计日系统调用量 Top 5</p></div><router-link to="/enterprises/compare">查看对比</router-link></header><div class="table-wrap"><table class="layui-table monitor-table"><thead><tr><th>排名</th><th>企业</th><th>调用量</th><th>操作</th></tr></thead><tbody><tr v-for="(row,index) in data.ranking" :key="row.enterpriseId"><td>{{index+1}}</td><td>{{row.enterpriseName}}</td><td class="number">{{formatNumber(row.requestCount)}}</td><td><router-link class="layui-btn layui-btn-xs monitor-secondary" :to="`/enterprises/${row.enterpriseId}/overview`">查看企业</router-link></td></tr></tbody></table></div></section></template><div v-else class="panel loading-state">正在加载运营数据…</div></div></template>
<script>
import PageHeader from '@/components/PageHeader.vue';
import LineChart from '@/components/LineChart.vue';
import { dashboardApi } from '@/api/monitor';

export default {
  name: 'DashboardPage',
  components: { PageHeader, LineChart },
  /**
   * 保存统计天数、平台总览响应及加载错误。默认查看两周数据，在趋势信息量和图表可读性之间取得平衡。
   */
  data: () => ({ days: 14, data: null, loading: false, error: '' }),
  computed: {
    /**
     * 将企业、用户、充值及当日系统用量组合为仪表盘指标卡，并统一数字和金额格式。
     */
    metrics() {
      return [
        { label: '当前企业总数', value: this.formatNumber(this.data.enterpriseCount), note: '每日 04:00 更新快照' },
        { label: '当前用户总数', value: this.formatNumber(this.data.userCount), note: '企业登录用户总数' },
        { label: '本月充值流水', value: this.formatMoney(this.data.monthRechargeAmount), note: '仅统计已支付充值' },
        { label: '今日系统调用', value: this.formatNumber(this.data.today.requestCount), note: '当前统计日累计' },
        { label: '今日 OCR 用量', value: this.formatNumber(this.data.today.ocrCount), note: '供应商实际调用' }
      ];
    },
    /**
     * 从每日统计日期中截取月日作为趋势图横轴标签。
     */
    labels() { return this.data.trends.map(item => item.statDate.slice(5)); },
    /**
     * 将每日系统调用量转换为趋势图序列。
     */
    requestSeries() { return [{ name: '系统调用', data: this.data.trends.map(item => item.requestCount) }]; },
    /**
     * 将每日 OCR 供应商实际调用量转换为柱状图序列。
     */
    ocrSeries() { return [{ name: 'OCR 用量', data: this.data.trends.map(item => item.ocrCount) }]; }
  },
  /**
   * 页面挂载后立即读取默认时间范围的运营数据。
   */
  mounted() { this.load(); },
  methods: {
    /**
     * 按中文千分位格式显示数量，空值按零处理。
     */
    formatNumber: value => Number(value || 0).toLocaleString('zh-CN'),
    /**
     * 按人民币两位小数格式显示监控金额。
     */
    formatMoney: value => `¥ ${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`,
    /**
     * 按当前统计天数刷新仪表盘。失败时保留明确错误供重试区展示，并始终恢复按钮加载状态。
     */
    async load() {
      this.loading = true;
      this.error = '';
      try {
        this.data = await dashboardApi.get({ days: this.days });
      } catch (error) {
        this.error = error.message;
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>
<style scoped>.mini-select{width:110px}</style>
