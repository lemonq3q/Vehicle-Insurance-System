<template>
  <section>
    <div class="section-title">
      <div>
        <h1>订阅订单详情</h1>
        <p>确认订阅周期和自动续费设置，系统将根据当前订阅计算应付或应退金额</p>
      </div>
      <button class="layui-btn layui-btn-primary portal-btn" type="button" @click="$router.back()">返回</button>
    </div>

    <div v-if="loading" class="portal-card loading-state">正在计算订单...</div>

    <div v-else-if="plan && preview" class="order-layout">
      <article class="portal-card order-form-panel">
        <div class="panel-heading">
          <div>
            <span class="portal-tag" :class="{ blue: preview.orderType === 'CHANGE_PLAN' }">{{ orderTypeName }}</span>
            <h2>{{ plan.name }}</h2>
          </div>
          <div class="plan-price">¥{{ money(plan.price) }}<small>/{{ periodName(plan.billingPeriod) }}</small></div>
        </div>

        <div class="plan-facts">
          <div><span>成员上限</span><strong>{{ plan.userLimit }} 人</strong></div>
          <div><span>单周期时长</span><strong>{{ plan.durationDays }} 天</strong></div>
          <div><span>预计生效</span><strong>{{ preview.startAt }}</strong></div>
          <div><span>预计到期</span><strong>{{ preview.endAt }}</strong></div>
        </div>

        <div class="form-section">
          <label for="periodCount">连续订阅周期</label>
          <div class="period-control">
            <button type="button" aria-label="减少一个周期" :disabled="!canManageFinance || form.periodCount <= preview.minimumPeriodCount" @click="changePeriod(-1)">
              <i class="layui-icon layui-icon-subtraction"></i>
            </button>
            <input
              id="periodCount"
              v-model.number="form.periodCount"
              class="layui-input"
              type="number"
              :min="preview.minimumPeriodCount"
              :disabled="!canManageFinance"
              step="1"
              @change="normalizePeriod"
            />
            <button type="button" aria-label="增加一个周期" :disabled="!canManageFinance" @click="changePeriod(1)">
              <i class="layui-icon layui-icon-addition"></i>
            </button>
            <span>个{{ periodName(plan.billingPeriod) }}周期</span>
          </div>
          <p v-if="preview.orderType === 'CHANGE_PLAN'" class="field-help">
            当前套餐剩余约 {{ preview.remainingPeriodCount }} 个周期。改订后需至少选择 {{ preview.minimumPeriodCount }} 个新套餐周期，以覆盖原订阅有效期。
          </p>
        </div>

        <label class="renew-setting">
          <span>
            <strong>到期自动续费</strong>
            <small>到期前按所选套餐自动创建续费订单</small>
          </span>
          <input v-model="form.autoRenew" type="checkbox" :disabled="!canManageFinance" />
        </label>

        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      </article>

      <aside class="portal-card amount-panel">
        <h2>金额明细</h2>
        <dl>
          <div><dt>套餐金额</dt><dd>¥{{ money(preview.priceAmount) }}</dd></div>
          <div v-if="preview.orderType === 'CHANGE_PLAN'"><dt>原套餐剩余价值抵扣</dt><dd class="credit">-¥{{ money(preview.creditAmount) }}</dd></div>
          <div class="divider"><dt>企业余额</dt><dd>¥{{ money(overview.wallet.balanceAmount) }}</dd></div>
          <div v-if="preview.refundAmount > 0"><dt>退回企业余额</dt><dd class="refund">+¥{{ money(preview.refundAmount) }}</dd></div>
        </dl>
        <div class="total-row">
          <span>{{ preview.refundAmount > 0 ? '本次应退' : '本次应付' }}</span>
          <strong>¥{{ money(preview.refundAmount > 0 ? preview.refundAmount : preview.payableAmount) }}</strong>
        </div>
        <p v-if="isBalanceInsufficient" class="balance-warning">
          当前余额不足，还需充值 ¥{{ money(shortfallAmount) }}。提交后将前往充值页面。
        </p>
        <p v-if="!canManageFinance" class="permission-note">出单员仅可查看订阅信息，无法提交或支付套餐订单。</p>
        <button class="layui-btn portal-btn portal-btn-primary submit-button" type="button" :disabled="submitting || !canManageFinance" @click="submitOrder">
          {{ !canManageFinance ? '无操作权限' : submitting ? '正在提交...' : isBalanceInsufficient ? '前往充值' : '提交并支付' }}
        </button>
        <p class="submit-note">提交即表示确认套餐、周期和金额，支付将使用企业余额。</p>
      </aside>
    </div>

    <div v-else class="empty-state">未找到对应套餐，请返回订阅服务重新选择。</div>
  </section>
</template>

<script>
import { createSubscriptionOrder, getFinanceOverview, getPlans, getSubscriptionOrderPreview } from '@/api/portal';

export default {
  name: 'SubscriptionOrderDetailPage',
  data() {
    return {
      loading: true,
      submitting: false,
      plan: null,
      overview: { wallet: {}, subscription: {} },
      preview: null,
      form: { periodCount: 1, autoRenew: false },
      errorMessage: ''
    };
  },
  computed: {
    orderTypeName() {
      return { BUY: '首次订阅', RENEW: '续订套餐', CHANGE_PLAN: '改订套餐' }[this.preview?.orderType] || '套餐订单';
    },
    canManageFinance() {
      return this.$store.getters.canManageFinance;
    },
    isBalanceInsufficient() {
      return Number(this.preview?.payableAmount || 0) > Number(this.overview.wallet?.balanceAmount || 0);
    },
    shortfallAmount() {
      return Math.max(0, Number(this.preview?.payableAmount || 0) - Number(this.overview.wallet?.balanceAmount || 0));
    }
  },
  async created() {
    await this.loadPage();
  },
  methods: {
    money(value) {
      return Number(value || 0).toFixed(2);
    },
    periodName(period) {
      return period === 'YEAR' ? '年' : period === 'MONTH' ? '月' : '日';
    },
    async loadPage() {
      this.loading = true;
      try {
        const [overviewResponse, plansResponse] = await Promise.all([getFinanceOverview(), getPlans()]);
        this.overview = overviewResponse.data;
        this.plan = plansResponse.data.find(item => item.id === Number(this.$route.params.planId)) || null;
        if (!this.plan) return;

        this.form.autoRenew = this.$route.query.autoRenew === undefined
          ? Boolean(this.overview.subscription?.autoRenewEnabled)
          : this.$route.query.autoRenew === 'true';
        this.form.periodCount = Math.max(1, Number(this.$route.query.periodCount || 1));
        await this.loadPreview(true);
      } finally {
        this.loading = false;
      }
    },
    async loadPreview(applyMinimum = false) {
      const response = await getSubscriptionOrderPreview({
        planId: this.plan.id,
        periodCount: this.form.periodCount
      });
      this.preview = response.data;
      if (applyMinimum && this.form.periodCount < this.preview.minimumPeriodCount) {
        this.form.periodCount = this.preview.minimumPeriodCount;
        await this.loadPreview();
      }
    },
    async normalizePeriod() {
      const minimum = this.preview?.minimumPeriodCount || 1;
      this.form.periodCount = Math.max(minimum, Math.floor(Number(this.form.periodCount) || minimum));
      await this.loadPreview();
    },
    async changePeriod(delta) {
      this.form.periodCount += delta;
      await this.normalizePeriod();
    },
    goToRecharge() {
      this.$router.push({
        name: 'finance-recharge',
        query: {
          requiredAmount: this.preview.payableAmount,
          balanceAmount: this.overview.wallet.balanceAmount,
          shortfallAmount: this.shortfallAmount,
          planId: this.plan.id,
          periodCount: this.form.periodCount,
          autoRenew: this.form.autoRenew
        }
      });
    },
    async submitOrder() {
      if (!this.canManageFinance) return;
      this.errorMessage = '';
      await this.normalizePeriod();
      if (!this.preview.eligible) {
        this.errorMessage = this.preview.validationMessage;
        return;
      }
      if (this.isBalanceInsufficient) {
        this.goToRecharge();
        return;
      }

      this.submitting = true;
      try {
        const response = await createSubscriptionOrder({
          planId: this.plan.id,
          periodCount: this.form.periodCount,
          autoRenew: this.form.autoRenew
        });
        await this.$router.push({
          name: 'finance-subscription',
          query: { orderNo: response.data.orderNo }
        });
      } catch (error) {
        if (Number(error.code) === 409) {
          this.goToRecharge();
          return;
        }
        this.errorMessage = error.message || '订单提交失败，请稍后重试';
      } finally {
        this.submitting = false;
      }
    }
  }
};
</script>

<style scoped>
.loading-state {
  padding: 48px;
  color: var(--portal-muted);
  text-align: center;
}

.order-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  align-items: start;
  gap: 18px;
}

.order-form-panel,
.amount-panel {
  padding: 24px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--portal-border);
}

.panel-heading h2 {
  margin: 10px 0 0;
  font-size: 24px;
}

.plan-price {
  color: var(--portal-text);
  font-size: 28px;
  font-weight: 800;
  white-space: nowrap;
}

.plan-price small {
  color: var(--portal-muted);
  font-size: 13px;
  font-weight: 400;
}

.plan-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  margin: 20px 0;
  overflow: hidden;
  border: 1px solid var(--portal-border);
  border-radius: 6px;
  background: var(--portal-border);
}

.plan-facts div {
  padding: 14px 16px;
  background: #fff;
}

.plan-facts span,
.plan-facts strong {
  display: block;
}

.plan-facts span {
  margin-bottom: 4px;
  color: var(--portal-muted);
  font-size: 12px;
}

.form-section {
  padding: 20px 0;
  border-top: 1px solid var(--portal-border);
}

.form-section > label {
  display: block;
  margin-bottom: 8px;
  font-weight: 700;
}

.period-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.period-control button {
  width: 40px;
  height: 40px;
  border: 1px solid var(--portal-border);
  border-radius: 6px;
  background: #fff;
}

.period-control button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.period-control .layui-input {
  width: 88px;
  text-align: center;
}

.period-control span,
.field-help {
  color: var(--portal-muted);
  font-size: 13px;
}

.field-help {
  margin: 10px 0 0;
}

.renew-setting {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 0;
  border-top: 1px solid var(--portal-border);
}

.renew-setting strong,
.renew-setting small {
  display: block;
}

.renew-setting small {
  margin-top: 3px;
  color: var(--portal-muted);
}

.renew-setting input {
  width: 20px;
  height: 20px;
}

.form-error,
.balance-warning {
  padding: 10px 12px;
  border-radius: 6px;
  color: #b91c1c;
  background: #fef2f2;
}

.permission-note {
  padding: 10px 12px;
  margin: 0 0 14px;
  border-radius: 6px;
  color: #92400e;
  background: #fffbeb;
  font-size: 13px;
}

.amount-panel {
  position: sticky;
  top: 0;
}

.amount-panel h2 {
  margin: 0 0 18px;
  font-size: 18px;
}

.amount-panel dl {
  margin: 0;
}

.amount-panel dl div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 0;
}

.amount-panel dt {
  color: var(--portal-muted);
}

.amount-panel dd {
  margin: 0;
  font-weight: 600;
}

.amount-panel .credit,
.amount-panel .refund {
  color: #15803d;
}

.amount-panel .divider {
  margin-top: 10px;
  padding-top: 16px;
  border-top: 1px solid var(--portal-border);
}

.total-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 0;
  margin-top: 10px;
  border-top: 1px solid var(--portal-border);
}

.total-row strong {
  color: var(--portal-accent-strong);
  font-size: 28px;
}

.balance-warning {
  margin: 0 0 14px;
  font-size: 13px;
}

.submit-button {
  width: 100%;
}

.submit-button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.submit-note {
  margin: 10px 0 0;
  color: var(--portal-muted);
  font-size: 12px;
  text-align: center;
}

@media (max-width: 980px) {
  .order-layout {
    grid-template-columns: 1fr;
  }

  .amount-panel {
    position: static;
  }
}

@media (max-width: 620px) {
  .panel-heading,
  .renew-setting {
    align-items: flex-start;
    flex-direction: column;
  }

  .plan-facts {
    grid-template-columns: 1fr;
  }
}
</style>
