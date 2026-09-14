<template><div><PageHeader eyebrow="Product Configuration" :title="isNew?'新建套餐':`编辑${form.name||''}套餐`" :description="isNew?'创建新的可订阅套餐':`套餐编码 ${form.code||'—'} · 最近更新于 ${form.updatedAt||'—'}`"><router-link class="layui-btn monitor-secondary" to="/plans">返回列表</router-link></PageHeader><div v-if="loading" class="panel loading-state">正在加载套餐…</div><form v-else class="panel plan-form" @submit.prevent="save"><section class="form-section"><h2>基础信息</h2><div class="form-grid"><div class="field" :class="{invalid:errors.name}"><label for="planName">套餐名称 *</label><input id="planName" v-model.trim="form.name" class="layui-input" maxlength="100"><p v-if="errors.name" class="error-text">{{errors.name}}</p></div><div class="field" :class="{invalid:errors.code}"><label for="planCode">套餐编码 *</label><input id="planCode" v-model.trim="form.code" class="layui-input" maxlength="50" :disabled="!isNew"><p class="helper">创建后不可修改，仅支持大写字母、数字和下划线</p><p v-if="errors.code" class="error-text">{{errors.code}}</p></div><div class="field full" :class="{invalid:errors.description}"><label for="description">套餐描述 *</label><textarea id="description" v-model.trim="form.description" class="layui-textarea" maxlength="500"></textarea><p v-if="errors.description" class="error-text">{{errors.description}}</p></div></div></section><section class="form-section"><h2>计费与权益</h2><div class="form-grid"><div class="field"><label>计费周期 *</label><select v-model="form.billingCycle" class="layui-select"><option value="YEAR">按年</option><option value="MONTH">按月</option><option value="DAY">按天</option></select></div><div v-for="field in numberFields" :key="field.key" class="field" :class="{invalid:errors[field.key]}"><label>{{field.label}} *</label><input v-model.number="form[field.key]" class="layui-input" type="number" :min="field.min" :step="field.step"><p v-if="errors[field.key]" class="error-text">{{errors[field.key]}}</p></div></div></section><section class="form-section"><h2>发布状态</h2><div class="radio-cards"><label class="radio-card"><input v-model.number="form.status" type="radio" :value="1"><span><strong>立即上架</strong><br><small>企业可以购买或续费</small></span></label><label class="radio-card"><input v-model.number="form.status" type="radio" :value="0"><span><strong>暂时下架</strong><br><small>禁止新购但保留已有订阅</small></span></label></div><div class="field reason-field"><label>变更原因 *</label><textarea v-model.trim="form.reason" class="layui-textarea" maxlength="500"></textarea><p v-if="errors.reason" class="error-text">{{errors.reason}}</p></div></section><footer class="form-footer"><router-link class="layui-btn monitor-secondary" to="/plans">取消</router-link><button class="layui-btn monitor-primary" :disabled="submitting">{{submitting?'保存中…':'保存修改'}}</button></footer></form><AppToast :message="toastMessage" :type="toastType" /></div></template>
<script>
import PageHeader from '@/components/PageHeader.vue';
import AppToast from '@/components/AppToast.vue';
import feedback from '@/mixins/feedback';
import { planApi } from '@/api/monitor';

/**
 * 为新建套餐创建独立初始表单，统一默认计费周期、时长、成员额度、排序和状态。
 */
const empty = () => ({ code: '', name: '', description: '', billingCycle: 'YEAR', durationDays: 365, price: 0, listPrice: 0, memberLimit: 10, workorderLimit: 1000, sortOrder: 10, status: 0, reason: '' });

export default {
  name: 'PlanEditPage',
  components: { PageHeader, AppToast },
  mixins: [feedback],
  /**
   * 保存套餐表单、字段错误和数值字段元数据；模板据元数据统一生成同类输入项。
   */
  data: () => ({
    form: empty(), errors: {}, submitting: false, loading: false,
    numberFields: [
      { key: 'durationDays', label: '套餐时长（天）', min: 1, step: 1 }, { key: 'price', label: '销售价格（元）', min: 0, step: 0.01 },
      { key: 'listPrice', label: '划线价格（元）', min: 0, step: 0.01 }, { key: 'memberLimit', label: '成员上限（人）', min: 1, step: 1 },
      { key: 'workorderLimit', label: '免费工单存储额度（份）', min: 0, step: 1 },
      { key: 'sortOrder', label: '展示排序', min: 0, step: 1 }
    ]
  }),
  computed: {
    /**
     * 特殊路由参数 new 表示新增模式，其他 ID 表示编辑已有套餐。
     */
    isNew() { return this.$route.params.id === 'new'; }
  },
  /**
   * 编辑模式加载套餐快照并清空原因，要求本次修改重新填写审计说明。
   */
  async mounted() {
    if (this.isNew) return;
    this.loading = true;
    try { this.form = { ...(await planApi.detail(this.$route.params.id)), reason: '' }; }
    catch (error) { this.errorMessage(error); }
    finally { this.loading = false; }
  },
  methods: {
    /**
     * 校验必填文本、套餐编码及数值边界。时长和成员上限必须为正，金额与排序可为零但不能为负，
     * 所有平台配置修改都必须附带操作原因。
     */
    validate() {
      const errors = {};
      if (!this.form.name) errors.name = '请输入套餐名称';
      if (!/^[A-Z0-9_]+$/.test(this.form.code)) errors.code = '请输入有效套餐编码';
      if (!this.form.description) errors.description = '请输入套餐描述';
      ['durationDays', 'memberLimit'].forEach(key => { if (!Number.isInteger(Number(this.form[key])) || Number(this.form[key]) <= 0) errors[key] = '必须为大于 0 的整数'; });
      ['workorderLimit', 'sortOrder'].forEach(key => { if (!Number.isInteger(Number(this.form[key])) || Number(this.form[key]) < 0) errors[key] = '必须为不小于 0 的整数'; });
      ['price', 'listPrice'].forEach(key => { if (Number(this.form[key]) < 0) errors[key] = '不能小于 0'; });
      if (!errors.listPrice && Number(this.form.listPrice) > 0 && Number(this.form.listPrice) < Number(this.form.price)) errors.listPrice = '划线价格不能低于销售价格';
      if (!this.form.reason) errors.reason = '请填写操作原因';
      this.errors = errors;
      return !Object.keys(errors).length;
    },
    /**
     * 校验后按模式创建或更新套餐，成功提示后返回列表，失败则显示服务端业务信息。
     */
    async save() {
      if (!this.validate()) return;
      this.submitting = true;
      try {
        if (this.isNew) await planApi.create(this.form);
        else await planApi.update(this.$route.params.id, this.form);
        this.notify('套餐已保存');
        setTimeout(() => this.$router.push('/plans'), 500);
      } catch (error) { this.errorMessage(error); } finally { this.submitting = false; }
    }
  }
};
</script>
<style scoped>.plan-form{max-width:920px}.plan-form .layui-select{width:100%}.form-footer{display:flex;justify-content:flex-end;gap:8px;padding:16px 20px;border-top:1px solid var(--border)}.reason-field{margin-top:18px}</style>
