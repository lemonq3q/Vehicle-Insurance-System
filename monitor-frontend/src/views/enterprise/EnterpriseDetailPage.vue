<template>
  <div>
    <PageHeader
      eyebrow="Enterprise Detail"
      :title="enterprise?.name || '企业概览'"
      :description="
        enterprise
          ? `企业编码 ${enterprise.code} · 创建于 ${datePart(
              enterprise.createdAt
            )}`
          : '正在加载企业信息'
      "
    >
      <router-link class="layui-btn monitor-secondary" to="/enterprises">返回企业列表</router-link>
    </PageHeader>
    <EnterpriseNav />
    <div v-if="loading" class="panel loading-state">正在加载企业概览…</div>
    <template v-else-if="enterprise">
      <section class="panel">
        <div class="summary-strip">
          <div class="summary-item">
            <span>企业状态</span><strong>{{ statusText }}</strong>
          </div>
          <div class="summary-item">
            <span>当前余额</span
            ><strong>{{ money(enterprise.balance) }}</strong>
          </div>
          <div class="summary-item">
            <span>当前套餐</span
            ><strong>{{ enterprise.planName || "未订阅" }}</strong>
          </div>
          <div class="summary-item">
            <span>套餐有效期</span
            ><strong>{{ enterprise.subscriptionEndDate || "—" }}</strong>
          </div>
        </div>
        <div class="panel-body action-row">
          <button class="layui-btn monitor-primary" @click="openBalance">
            调整余额
          </button>
          <button class="layui-btn monitor-secondary" @click="openSubscription">
            设置套餐
          </button>
          <button
            class="layui-btn monitor-danger"
            :disabled="!enterprise.planId"
            @click="openCancel"
          >
            取消套餐
          </button>
          <span class="helper">套餐设置和取消操作均会写入专用审计记录</span>
        </div>
      </section>
      <section class="metrics-grid detail-metrics">
        <article
          v-for="item in monthMetrics"
          :key="item.label"
          class="panel metric-card"
        >
          <span class="metric-label">{{ item.label }}</span>
          <div class="metric-value">{{ format(item.value) }}</div>
          <span class="metric-note">本月自然日累计</span>
        </article>
      </section>
      <section class="chart-grid">
        <article class="panel">
          <header class="panel-header">
            <div>
              <h2>企业用量趋势</h2>
              <p>{{ intervalText }}，仅展示聚合统计</p>
            </div>
            <select
              v-model="range"
              class="layui-select range-select"
              aria-label="用量趋势时间范围"
              @change="loadUsage"
            >
              <option
                v-for="option in rangeOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </header>
          <div class="panel-body">
            <LineChart :labels="labels" :series="series" />
          </div>
        </article>
        <article class="panel">
          <header class="panel-header">
            <div>
              <h2>企业基本信息</h2>
              <p>客户联系与归属信息</p>
            </div>
          </header>
          <dl class="detail-list">
            <div>
              <dt>企业负责人</dt>
              <dd>{{ enterprise.contactName || "—" }}</dd>
            </div>
            <div>
              <dt>联系电话</dt>
              <dd>{{ enterprise.contactPhone || "—" }}</dd>
            </div>
            <div>
              <dt>企业成员</dt>
              <dd>
                {{ enterprise.memberCount }} / {{ enterprise.memberLimit }} 人
              </dd>
            </div>
            <div>
              <dt>企业来源</dt>
              <dd>{{ enterprise.source }}</dd>
            </div>
            <div>
              <dt>最近活跃</dt>
              <dd>{{ enterprise.lastActiveAt || "—" }}</dd>
            </div>
            <div>
              <dt>客户备注</dt>
              <dd>{{ enterprise.remark || "—" }}</dd>
            </div>
          </dl>
        </article>
      </section>
    </template>
    <AppModal
      v-model="balanceModal"
      title="调整企业余额"
      confirm-text="确认调整"
      :loading="submitting"
      @confirm="adjustBalance"
      ><div class="field">
        <label>调整金额（元）</label
        ><input
          v-model.number="balanceForm.amount"
          type="number"
          class="layui-input"
          placeholder="增加输入正数，扣减输入负数"
        />
      </div>
      <div class="field">
        <label>操作原因 *</label
        ><textarea
          v-model.trim="balanceForm.reason"
          class="layui-textarea"
          maxlength="500"
        ></textarea>
        <p v-if="formError" class="error-text">{{ formError }}</p>
      </div></AppModal
    >
    <AppModal
      v-model="subscriptionModal"
      title="设置企业套餐"
      confirm-text="确认设置"
      :loading="submitting"
      @confirm="setSubscription"
      ><div class="field">
        <label>套餐 *</label
        ><select v-model="subscriptionForm.planId" class="layui-select">
          <option :value="null" disabled>请选择套餐</option>
          <option v-for="plan in activePlans" :key="plan.id" :value="plan.id">
            {{ plan.name }}
          </option>
        </select>
      </div>
      <div class="field">
        <label>到期日期 *</label
        ><input
          v-model="subscriptionForm.endDate"
          type="date"
          class="layui-input"
          :min="tomorrow"
        />
      </div>
      <div class="field">
        <label>操作原因 *</label
        ><textarea
          v-model.trim="subscriptionForm.reason"
          class="layui-textarea"
          maxlength="500"
        ></textarea>
        <p v-if="formError" class="error-text">{{ formError }}</p>
      </div></AppModal
    >
    <AppModal
      v-model="cancelModal"
      title="取消企业套餐"
      confirm-text="立即取消"
      danger
      :loading="submitting"
      @confirm="cancelSubscription"
      ><p>取消后企业将立即失去套餐权益，不会删除历史订阅记录。</p>
      <div class="field">
        <label>操作原因 *</label
        ><textarea
          v-model.trim="cancelReason"
          class="layui-textarea"
          maxlength="500"
        ></textarea>
        <p v-if="formError" class="error-text">{{ formError }}</p>
      </div></AppModal
    >
    <AppToast :message="toastMessage" :type="toastType" />
  </div>
</template>
<script>
import PageHeader from "@/components/PageHeader.vue";
import LineChart from "@/components/LineChart.vue";
import AppModal from "@/components/AppModal.vue";
import AppToast from "@/components/AppToast.vue";
import EnterpriseNav from "./EnterpriseNav.vue";
import feedback from "@/mixins/feedback";
import { enterpriseApi } from "@/api/monitor";
export default {
  name: "EnterpriseDetailPage",
  components: { PageHeader, LineChart, AppModal, AppToast, EnterpriseNav },
  mixins: [feedback],
  /** 保存真实企业详情、趋势、套餐选项及人工财务操作状态，所有业务请求均通过监控后端完成。 */
  data: () => ({
    enterprise: null,
    usage: { interval: "DAY", points: [] },
    plans: [],
    range: "30d",
    rangeOptions: [
      { value: "7d", label: "近 7 天" },
      { value: "15d", label: "近 15 天" },
      { value: "30d", label: "近 30 天" },
      { value: "3m", label: "近 3 个月" },
      { value: "6m", label: "近 6 个月" },
      { value: "1y", label: "近 1 年" },
    ],
    loading: true,
    submitting: false,
    balanceModal: false,
    subscriptionModal: false,
    cancelModal: false,
    balanceForm: { amount: null, reason: "" },
    subscriptionForm: { planId: null, endDate: "", reason: "" },
    cancelReason: "",
    formError: "",
  }),
  computed: {
    /** 路由企业标识保持字符串，避免 Java Long 在 JavaScript Number 中发生不可逆的精度损失。 */ id() {
      return String(this.$route.params.id || '');
    },
    /** 将企业状态码转换为展示文案。 */ statusText() {
      return this.enterprise.status === 1
        ? "正常"
        : this.enterprise.status === 2
        ? "欠费限制"
        : "已停用";
    },
    /** 人工设置只允许选择上架套餐。 */ activePlans() {
      return this.plans.filter((plan) => Number(plan.status) === 1);
    },
    /** 使用后端已补齐的分组标签生成横轴。 */ labels() {
      return this.usage.points.map((item) => item.label);
    },
    /** 组合 API、OCR 和已处理工单三条趋势。 */ series() {
      return [
        {
          name: "系统调用",
          data: this.usage.points.map((item) => item.requestCount),
        },
        {
          name: "OCR 用量",
          data: this.usage.points.map((item) => item.ocrCount),
          area: false,
        },
        {
          name: "已处理工单",
          data: this.usage.points.map((item) => item.workorderCount),
          area: false,
        },
      ];
    },
    /** 指标卡使用详情接口的本月聚合，不再将趋势最后一点当作月度值。 */ monthMetrics() {
      const usage = this.enterprise.monthUsage || {};
      return [
        { label: "本月已处理工单", value: usage.workorderCount },
        { label: "本月系统调用", value: usage.requestCount },
        { label: "本月 OCR 用量", value: usage.ocrCount },
      ];
    },
    /** 显示当前自动粒度。 */ intervalText() {
      return (
        { DAY: "按日统计", WEEK: "按周统计", MONTH: "按月统计" }[
          this.usage.interval
        ] || "自动分粒度"
      );
    },
    /** 与后端到期日晚于今天的校验对齐。 */ tomorrow() {
      const date = new Date();
      date.setDate(date.getDate() + 1);
      return date.toISOString().slice(0, 10);
    },
  },
  /** 初始化时并行读取详情、30 天趋势和套餐选项。 */ mounted() {
    Promise.all([
      this.loadDetail(),
      this.loadUsage(),
      enterpriseApi.subscriptionPlans().then((data) => {
        this.plans = data;
      }),
    ])
      .catch(this.errorMessage)
      .finally(() => {
        this.loading = false;
      });
  },
  methods: {
    /** 安全截取日期。 */ datePart: (value) =>
      value ? String(value).slice(0, 10) : "—",
    /** 格式化数量。 */ format: (value) =>
      Number(value || 0).toLocaleString("zh-CN"),
    /** 格式化人民币。 */ money: (value) =>
      `¥ ${Number(value || 0).toLocaleString("zh-CN", {
        minimumFractionDigits: 2,
      })}`,
    /** 读取企业详情和本月快照。 */ loadDetail() {
      return enterpriseApi.detail(this.id).then((data) => {
        this.enterprise = data;
      });
    },
    /** 按范围代码读取自动分粒度趋势。 */ loadUsage() {
      return enterpriseApi
        .usage(this.id, { range: this.range })
        .then((data) => {
          this.usage = data;
        })
        .catch(this.errorMessage);
    },
    /** 重置并打开余额表单。 */ openBalance() {
      this.formError = "";
      this.balanceForm = { amount: null, reason: "" };
      this.balanceModal = true;
    },
    /** 以当前订阅预填套餐表单。 */ openSubscription() {
      this.formError = "";
      this.subscriptionForm = {
        planId: this.enterprise.planId,
        endDate: this.enterprise.subscriptionEndDate || "",
        reason: "",
      };
      this.subscriptionModal = true;
    },
    /** 清空原因后打开取消警示弹窗。 */ openCancel() {
      this.formError = "";
      this.cancelReason = "";
      this.cancelModal = true;
    },
    /** 校验非零金额和审计原因后，调用真实后端完成钱包调账、流水及操作日志写入。 */ async adjustBalance() {
      if (!this.balanceForm.amount || !this.balanceForm.reason) {
        this.formError = "请输入非零调整金额并填写操作原因";
        return;
      }
      this.submitting = true;
      try {
        const data = await enterpriseApi.adjustBalance(
          this.id,
          this.balanceForm
        );
        this.enterprise.balance = data.balance;
        this.balanceModal = false;
        this.notify("企业余额已调整");
      } catch (error) {
        this.errorMessage(error);
      } finally {
        this.submitting = false;
      }
    },
    /** 提交套餐、到期日和审计原因到真实后端。 */ async setSubscription() {
      if (
        !this.subscriptionForm.planId ||
        !this.subscriptionForm.endDate ||
        !this.subscriptionForm.reason
      ) {
        this.formError = "请选择套餐、到期日期并填写操作原因";
        return;
      }
      this.submitting = true;
      try {
        this.enterprise = await enterpriseApi.setSubscription(
          this.id,
          this.subscriptionForm
        );
        this.subscriptionModal = false;
        this.notify("企业套餐已更新");
      } catch (error) {
        this.errorMessage(error);
      } finally {
        this.submitting = false;
      }
    },
    /** 经警示弹窗确认后取消套餐并重读详情。 */ async cancelSubscription() {
      if (!this.cancelReason) {
        this.formError = "请填写取消原因";
        return;
      }
      this.submitting = true;
      try {
        await enterpriseApi.cancelSubscription(this.id, {
          reason: this.cancelReason,
        });
        await this.loadDetail();
        this.cancelModal = false;
        this.notify("企业套餐已取消");
      } catch (error) {
        this.errorMessage(error);
      } finally {
        this.submitting = false;
      }
    },
  },
};
</script>
<style scoped>
.detail-metrics {
  grid-template-columns: repeat(3, 1fr);
  margin-top: 16px;
}
.range-select {
  width: 132px;
}
.detail-list {
  margin: 0;
  padding: 8px 18px;
}
.detail-list div {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}
.detail-list div:last-child {
  border: 0;
}
.detail-list dt {
  color: var(--muted);
}
.detail-list dd {
  margin: 0;
  text-align: right;
  font-weight: 600;
}
@media (max-width: 620px) {
  .detail-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
