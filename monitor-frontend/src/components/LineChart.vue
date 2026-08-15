<template><div ref="chart" class="chart" role="img" :aria-label="ariaLabel"></div></template>

<script>
import * as echarts from 'echarts/core';
import { LineChart, BarChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([LineChart, BarChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

export default {
  name: 'LineChart',
  props: {
    labels: { type: Array, required: true },
    series: { type: Array, required: true },
    ariaLabel: { type: String, default: '趋势图表' },
    type: { type: String, default: 'line' }
  },
  /**
   * DOM 就绪后创建 ECharts 实例、完成首次绘制，并通过 ResizeObserver 适配卡片或侧栏宽度变化。
   */
  mounted() {
    this.chart = echarts.init(this.$refs.chart);
    this.render();
    this.resizeObserver = new ResizeObserver(() => this.chart?.resize());
    this.resizeObserver.observe(this.$refs.chart);
  },
  /**
   * 组件销毁时停止尺寸观察并释放 Canvas、事件监听等 ECharts 资源。
   */
  beforeUnmount() {
    this.resizeObserver?.disconnect();
    this.chart?.dispose();
  },
  watch: { series: { deep: true, handler() { this.render(); } } },
  methods: {
    /**
     * 将标签和多组业务序列转换为统一趋势图配置。组件可在折线和柱状模式间复用，并为首序列提供更明显面积层次。
     */
    render() {
      const colors = ['#2563eb', '#d97706', '#059669', '#7c3aed', '#dc2626'];
      this.chart.setOption({
        color: colors,
        tooltip: { trigger: 'axis', backgroundColor: '#0f172a', borderWidth: 0, textStyle: { color: '#fff' } },
        legend: { top: 0, right: 0, icon: 'roundRect', itemWidth: 12, textStyle: { color: '#475569' } },
        grid: { left: 12, right: 12, top: 42, bottom: 8, containLabel: true },
        xAxis: { type: 'category', data: this.labels, boundaryGap: this.type === 'bar', axisLine: { lineStyle: { color: '#cbd5e1' } }, axisLabel: { color: '#64748b' } },
        yAxis: { type: 'value', splitLine: { lineStyle: { color: '#e2e8f0', type: 'dashed' } }, axisLabel: { color: '#64748b' } },
        series: this.series.map((item, index) => ({
          ...item,
          type: item.type || this.type,
          smooth: this.type === 'line',
          symbol: 'circle', symbolSize: 6,
          lineStyle: { width: 2 },
          areaStyle: item.area === false ? undefined : { opacity: index === 0 ? 0.09 : 0.03 },
          barMaxWidth: 28
        }))
      });
    }
  }
};
</script>

<style scoped>.chart { width: 100%; height: 286px; }</style>
