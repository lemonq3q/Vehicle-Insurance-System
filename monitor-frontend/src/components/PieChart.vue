<template><div ref="chart" class="pie-chart" role="img" :aria-label="ariaLabel"></div></template>

<script>
import * as echarts from 'echarts/core';
import { PieChart } from 'echarts/charts';
import { LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([PieChart, LegendComponent, TitleComponent, TooltipComponent, CanvasRenderer]);

export default {
  name: 'PieChart',
  props: {
    data: { type: Array, required: true },
    ariaLabel: { type: String, default: '企业用量占比饼图' },
    title: { type: String, default: '总调用量' },
    seriesName: { type: String, default: '系统调用量' }
  },
  /**
   * 初始化饼图并监听容器尺寸，使仪表盘响应式布局变化后图形自动重排。
   */
  mounted() {
    this.chart = echarts.init(this.$refs.chart);
    this.render();
    this.resizeObserver = new ResizeObserver(() => this.chart?.resize());
    this.resizeObserver.observe(this.$refs.chart);
  },
  /**
   * 离开页面时断开尺寸观察并销毁图表实例，防止 Canvas 和事件处理器泄漏。
   */
  beforeUnmount() {
    this.resizeObserver?.disconnect();
    this.chart?.dispose();
  },
  watch: { data: { deep: true, handler() { this.render(); } } },
  methods: {
    /**
     * 汇总各业务系统调用量作为圆环中心数字，并构建带百分比、图例和悬浮明细的用量占比图。
     */
    render() {
      const total = this.data.reduce((sum, item) => sum + Number(item.value || 0), 0);
      this.chart.setOption({
        color: ['#2563eb', '#d97706', '#059669', '#7c3aed', '#dc2626'],
        tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 次（{d}%）', backgroundColor: '#0f172a', borderWidth: 0, textStyle: { color: '#fff' } },
        title: {
          text: total.toLocaleString('zh-CN'), subtext: this.title, left: 'center', top: '32%',
          textStyle: { color: '#0f172a', fontSize: 22, fontWeight: 700 },
          subtextStyle: { color: '#64748b', fontSize: 12, lineHeight: 22 }
        },
        legend: { bottom: 2, left: 'center', icon: 'circle', itemWidth: 9, itemHeight: 9, itemGap: 16, textStyle: { color: '#475569' } },
        series: [{
          name: this.seriesName, type: 'pie', radius: ['48%', '76%'], center: ['50%', '43%'],
          avoidLabelOverlap: true,
          padAngle: 2,
          itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 6 },
          label: { show: true, position: 'inside', color: '#fff', fontWeight: 700, formatter: '{d}%', textBorderColor: 'rgba(15,23,42,.35)', textBorderWidth: 2 },
          labelLine: { show: false },
          emphasis: { scaleSize: 6, itemStyle: { shadowBlur: 12, shadowColor: 'rgba(15,23,42,.2)' } },
          data: this.data
        }]
      });
    }
  }
};
</script>

<style scoped>.pie-chart { width: 100%; height: 286px; }</style>
