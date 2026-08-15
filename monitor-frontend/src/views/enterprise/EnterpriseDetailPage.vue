<template><div><PageHeader eyebrow="Enterprise Detail" :title="enterprise?.name||'企业概览'" :description="enterprise?`企业编码 ${enterprise.code} · 创建于 ${enterprise.createdAt.slice(0,10)}`:'正在加载企业信息'"/><EnterpriseNav/><div v-if="loading" class="panel loading-state">正在加载企业概览…</div><template v-else-if="enterprise"><section class="panel"><div class="summary-strip"><div class="summary-item"><span>企业状态</span><strong>{{statusText}}</strong></div><div class="summary-item"><span>当前余额</span><strong>{{money(enterprise.balance)}}</strong></div><div class="summary-item"><span>当前套餐</span><strong>{{enterprise.planName||'未订阅'}}</strong></div><div class="summary-item"><span>套餐有效期</span><strong>{{enterprise.subscriptionEndDate||'—'}}</strong></div></div><div class="panel-body action-row"><button class="layui-btn monitor-primary" @click="openBalance">调整余额</button><button class="layui-btn monitor-secondary" @click="openSubscription">设置套餐</button><button class="layui-btn monitor-danger" :disabled="!enterprise.planId" @click="cancelModal=true">取消套餐</button><span class="helper">所有资金和套餐操作都会写入审计日志</span></div></section><section class="metrics-grid detail-metrics"><article v-for="item in todayMetrics" :key="item.label" class="panel metric-card"><span class="metric-label">{{item.label}}</span><div class="metric-value">{{format(item.value)}}</div><span class="metric-note">当前统计日累计</span></article></section><section class="chart-grid"><article class="panel"><header class="panel-header"><div><h2>企业用量趋势</h2><p>仅展示聚合统计，不提供业务工单明细</p></div><select v-model.number="rangeDays" class="layui-select mini-select" @change="loadUsage"><option :value="14">近 14 天</option><option :value="30">近 30 天</option></select></header><div class="panel-body"><LineChart :labels="labels" :series="series" /></div></article><article class="panel"><header class="panel-header"><div><h2>企业基本信息</h2><p>客户联系与归属信息</p></div></header><dl class="detail-list"><div><dt>企业负责人</dt><dd>{{enterprise.contactName}}</dd></div><div><dt>联系电话</dt><dd>{{enterprise.contactPhone}}</dd></div><div><dt>企业成员</dt><dd>{{enterprise.memberCount}} / {{enterprise.memberLimit}} 人</dd></div><div><dt>企业来源</dt><dd>{{enterprise.source}}</dd></div><div><dt>最近活跃</dt><dd>{{enterprise.lastActiveAt}}</dd></div><div><dt>客户备注</dt><dd>{{enterprise.remark||'—'}}</dd></div></dl></article></section></template><AppModal v-model="balanceModal" title="调整企业余额" confirm-text="确认调整" :loading="submitting" @confirm="adjustBalance"><div class="field"><label>调整金额（元）</label><input v-model.number="balanceForm.amount" type="number" class="layui-input" placeholder="增加输入正数，扣减输入负数"></div><div class="field"><label>操作原因 *</label><textarea v-model.trim="balanceForm.reason" class="layui-textarea" maxlength="500"></textarea><p v-if="formError" class="error-text">{{formError}}</p></div></AppModal><AppModal v-model="subscriptionModal" title="设置企业套餐" confirm-text="确认设置" :loading="submitting" @confirm="setSubscription"><div class="field"><label>套餐 *</label><select v-model.number="subscriptionForm.planId" class="layui-select"><option :value="null" disabled>请选择套餐</option><option v-for="plan in plans.filter(p=>p.status===1)" :key="plan.id" :value="plan.id">{{plan.name}}</option></select></div><div class="field"><label>到期日期 *</label><input v-model="subscriptionForm.endDate" type="date" class="layui-input"></div><div class="field"><label>操作原因 *</label><textarea v-model.trim="subscriptionForm.reason" class="layui-textarea"></textarea><p v-if="formError" class="error-text">{{formError}}</p></div></AppModal><AppModal v-model="cancelModal" title="取消企业套餐" confirm-text="立即取消" danger :loading="submitting" @confirm="cancelSubscription"><p>取消后企业将立即失去套餐权益，此操作不会删除历史订阅订单。</p><div class="field"><label>操作原因 *</label><textarea v-model.trim="cancelReason" class="layui-textarea"></textarea><p v-if="formError" class="error-text">{{formError}}</p></div></AppModal><AppToast :message="toastMessage" :type="toastType" /></div></template>
<script>
import PageHeader from '@/components/PageHeader.vue';
import LineChart from '@/components/LineChart.vue';
import AppModal from '@/components/AppModal.vue';
import AppToast from '@/components/AppToast.vue';
import EnterpriseNav from './EnterpriseNav.vue';
import feedback from '@/mixins/feedback';
import { enterpriseApi, planApi } from '@/api/monitor';

export default {
  name: 'EnterpriseDetailPage',
  components: { PageHeader, LineChart, AppModal, AppToast, EnterpriseNav },
  mixins: [feedback],
  /**
   * 保存企业详情、用量趋势、套餐选项，以及余额调账、套餐变更和取消订阅三个平台操作的表单与弹窗状态。
   */
  data: () => ({
    enterprise: null, usage: { points: [] }, plans: [], rangeDays: 14, loading: true, submitting: false,
    balanceModal: false, subscriptionModal: false, cancelModal: false,
    balanceForm: { amount: null, reason: '' }, subscriptionForm: { planId: null, endDate: '', reason: '' },
    cancelReason: '', formError: ''
  }),
  computed: {
    /**
     * 将路由企业标识转换为所有详情操作使用的数字 ID。
     */
    id() { return Number(this.$route.params.id); },
    /**
     * 将企业正常、欠费限制和停用状态转换为详情页文案。
     */
    statusText() { return this.enterprise.status === 1 ? '正常' : this.enterprise.status === 2 ? '欠费限制' : '已停用'; },
    /**
     * 从用量统计日期截取月日作为趋势横轴。
     */
    labels() { return this.usage.points.map(item => item.statDate.slice(5)); },
    /**
     * 组合系统调用、OCR 和出单量三组趋势；次要序列关闭面积填充以保持多线清晰。
     */
    series() {
      return [
        { name: '系统调用', data: this.usage.points.map(item => item.requestCount) },
        { name: 'OCR 使用', data: this.usage.points.map(item => item.ocrCount), area: false },
        { name: '出单量', data: this.usage.points.map(item => item.workorderCount), area: false }
      ];
    },
    /**
     * 将统计序列最后一个数据点解释为当前统计日，生成今日业务指标卡。
     */
    todayMetrics() {
      const today = this.usage.points.at(-1) || {};
      return [{ label: '今日出单', value: today.workorderCount }, { label: '今日系统调用', value: today.requestCount }, { label: '今日 OCR 使用', value: today.ocrCount }];
    }
  },
  /**
   * 并行加载企业详情、两周用量和套餐选项，所有初始化任务结束后再移除页面骨架状态。
   */
  mounted() {
    Promise.all([this.loadDetail(), this.loadUsage(), planApi.list().then(data => { this.plans = data; })]).finally(() => { this.loading = false; });
  },
  methods: {
    /**
     * 以中文千分位展示业务用量。
     */
    format: value => Number(value || 0).toLocaleString('zh-CN'),
    /**
     * 以人民币两位小数展示钱包余额。
     */
    money: value => `¥ ${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`,
    /**
     * 读取企业资料并更新详情快照，供初始化和取消订阅后复用。
     */
    loadDetail() { return enterpriseApi.detail(this.id).then(data => { this.enterprise = data; }); },
    /**
     * 按当前天数读取企业系统调用、OCR 和出单统计。
     */
    loadUsage() { return enterpriseApi.usage(this.id, { days: this.rangeDays }).then(data => { this.usage = data; }); },
    /**
     * 打开余额调账前重建空表单并清除旧错误，避免金额或原因残留。
     */
    openBalance() { this.formError = ''; this.balanceForm = { amount: null, reason: '' }; this.balanceModal = true; },
    /**
     * 打开套餐配置时以企业当前套餐和到期日作为初值，并要求重新填写变更原因。
     */
    openSubscription() {
      this.formError = '';
      this.subscriptionForm = { planId: this.enterprise.planId, endDate: this.enterprise.subscriptionEndDate || '', reason: '' };
      this.subscriptionModal = true;
    },
    /**
     * 校验非零金额与原因后执行人工调账，成功时直接同步接口返回余额并关闭弹窗。
     */
    async adjustBalance() {
      if (!this.balanceForm.amount || !this.balanceForm.reason) { this.formError = '请输入非零调整金额并填写操作原因'; return; }
      this.submitting = true;
      try {
        const data = await enterpriseApi.adjustBalance(this.id, this.balanceForm);
        this.enterprise.balance = data.balance; this.balanceModal = false; this.notify('企业余额已调整');
      } catch (error) { this.errorMessage(error); } finally { this.submitting = false; }
    },
    /**
     * 要求套餐、到期日和原因齐全后变更企业订阅，并用后端完整响应替换详情快照。
     */
    async setSubscription() {
      if (!this.subscriptionForm.planId || !this.subscriptionForm.endDate || !this.subscriptionForm.reason) { this.formError = '请选择套餐、到期日期并填写操作原因'; return; }
      this.submitting = true;
      try { this.enterprise = await enterpriseApi.setSubscription(this.id, this.subscriptionForm); this.subscriptionModal = false; this.notify('企业套餐已更新'); }
      catch (error) { this.errorMessage(error); } finally { this.submitting = false; }
    },
    /**
     * 填写原因后取消当前订阅，并重新读取企业详情确认套餐状态和到期信息已更新。
     */
    async cancelSubscription() {
      if (!this.cancelReason) { this.formError = '请填写取消原因'; return; }
      this.submitting = true;
      try {
        await enterpriseApi.cancelSubscription(this.id, { reason: this.cancelReason });
        await this.loadDetail(); this.cancelModal = false; this.notify('企业套餐已取消');
      } catch (error) { this.errorMessage(error); } finally { this.submitting = false; }
    }
  }
};
</script>
<style scoped>.detail-metrics{grid-template-columns:repeat(3,1fr);margin-top:16px}.mini-select{width:110px}.detail-list{margin:0;padding:8px 18px}.detail-list div{display:flex;justify-content:space-between;gap:20px;padding:12px 0;border-bottom:1px solid var(--border)}.detail-list div:last-child{border:0}.detail-list dt{color:var(--muted)}.detail-list dd{margin:0;text-align:right;font-weight:600}@media(max-width:620px){.detail-metrics{grid-template-columns:1fr}}</style>
