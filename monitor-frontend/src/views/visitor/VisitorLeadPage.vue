<template>
  <section>
    <PageHeader eyebrow="WEBSITE LEADS" title="游客信息" description="查看官网联系我们表单提交记录，仅支持按完整游客编号查询。" />
    <div class="panel query-panel">
      <div class="query-toolbar">
        <h2>提交记录</h2>
        <form class="query-filters" @submit.prevent="search">
          <label class="visitor-number-field"><span>游客编号</span><input v-model.trim="query" class="layui-input" maxlength="32" placeholder="例如 VL20260828A7K3M9Q2X5" /></label>
          <button class="layui-btn monitor-primary" type="submit" :disabled="loading">查询</button>
          <button class="layui-btn monitor-secondary" type="button" :disabled="loading || !query" @click="reset">重置</button>
        </form>
      </div>
      <div v-if="loading" class="loading-state">正在加载游客信息…</div>
      <div v-else-if="error" class="empty-state" role="alert"><div><p>{{ error }}</p><button class="layui-btn monitor-primary" type="button" @click="load">重新加载</button></div></div>
      <div v-else-if="!rows.length" class="empty-state">没有找到符合条件的游客信息</div>
      <div v-else class="table-wrap">
        <table class="layui-table monitor-table visitor-table">
          <thead><tr><th>游客编号</th><th>姓名</th><th>联系方式</th><th>角色</th><th>预计月单量</th><th>合作诉求</th><th>备注</th><th>提交时间</th></tr></thead>
          <tbody><tr v-for="item in rows" :key="item.id"><td class="number lead-no">{{ item.leadNo }}</td><td>{{ item.name }}</td><td>{{ item.contact }}</td><td>{{ roleLabel(item.roleCode) }}</td><td class="number">{{ item.expectedMonthlyOrders == null ? '—' : Number(item.expectedMonthlyOrders).toLocaleString('zh-CN') }}</td><td><template v-if="item.intentCodes?.length"><span v-for="intent in item.intentCodes" :key="intent" class="tag intent-tag">{{ intentLabel(intent) }}</span></template><span v-else>—</span></td><td class="remark-cell">{{ item.remark || '—' }}</td><td class="number">{{ item.createdAt }}</td></tr></tbody>
        </table>
      </div>
      <AppPagination v-if="!loading && !error" :page-no="pageNo" :page-size="pageSize" :total="total" @change="changePage" />
    </div>
  </section>
</template>

<script>
import { visitorLeadApi } from '@/api/monitor';
import AppPagination from '@/components/AppPagination.vue';
import PageHeader from '@/components/PageHeader.vue';

/**
 * 监控游客信息页面只维护编号精确查询与分页状态，不扩展姓名、联系方式等查询条件，
 * 避免扩大个人信息检索范围，并严格对应当前 API 契约。
 */
export default {
  name: 'VisitorLeadPage', components: { AppPagination, PageHeader },
  data: () => ({ query: '', appliedLeadNo: '', rows: [], loading: false, error: '', pageNo: 1, pageSize: 10, total: 0 }),
  created() { this.load(); },
  methods: {
    /** 按已应用编号读取当前页；请求失败保留查询条件并提供显式重试。 */
    async load() { this.loading = true; this.error = ''; try { const response = await visitorLeadApi.list({ leadNo: this.appliedLeadNo || undefined, pageNo: this.pageNo, pageSize: this.pageSize }); this.rows = response?.list || []; this.total = Number(response?.total || 0); } catch (error) { this.rows = []; this.total = 0; this.error = error?.message || '游客信息加载失败'; } finally { this.loading = false; } },
    /** 应用完整编号并回到第一页，空编号表示恢复全部记录。 */
    search() { this.appliedLeadNo = this.query.toUpperCase(); this.pageNo = 1; this.load(); },
    /** 清空唯一查询条件并重新读取最新提交。 */
    reset() { this.query = ''; this.appliedLeadNo = ''; this.pageNo = 1; this.load(); },
    /** 切换分页并保留当前已应用编号。 */
    changePage(page) { this.pageNo = page; this.load(); },
    /** 将角色编码转换为官网表单使用的中文选项。 */
    roleLabel(code) { return { OPC_AGENT: 'OPC 个人代理', CAR_DEALER: '汽车经销商', INSURANCE_AGENCY: '保险代理机构', OTHER: '其他' }[code] || code || '—'; },
    /** 将合作诉求编码转换为可读标签。 */
    intentLabel(code) { return { TRIAL: '我要试用', DEMO: '预约 Demo', CUSTOM_COOPERATION: '机构定制合作' }[code] || code; },
  },
};
</script>

<style scoped>
.visitor-number-field{display:flex;align-items:center;gap:9px}.visitor-number-field span{color:var(--text-secondary);font-size:12px;font-weight:600;white-space:nowrap}.visitor-number-field .layui-input{width:280px}.visitor-table{min-width:1180px}.lead-no{font-weight:700;color:var(--primary)}.intent-tag{margin:2px 5px 2px 0}.remark-cell{max-width:260px;white-space:normal;word-break:break-word}@media(max-width:720px){.visitor-number-field{width:100%;align-items:flex-start;flex-direction:column}.visitor-number-field .layui-input{width:100%}}
</style>
