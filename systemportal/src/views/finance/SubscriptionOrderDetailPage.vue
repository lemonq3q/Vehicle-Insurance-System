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
          <div><span>工单额度</span><strong>{{ plan.workorderLimit }} 单</strong></div>
          <div><span>单周期时长</span><strong>{{ plan.durationDays }} 天</strong></div>
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

      </article>

      <aside class="portal-card amount-panel">
        <h2>金额明细</h2>
        <dl>
          <div><dt>套餐金额</dt><dd>¥{{ money(preview.priceAmount) }}</dd></div>
          <div v-if="preview.orderType === 'CHANGE_PLAN'"><dt>原套餐剩余价值抵扣</dt><dd class="credit">-¥{{ money(preview.creditAmount) }}</dd></div>
          <div v-if="preview.workorderOverageCount > 0">
            <dt>超额工单费（{{ preview.workorderOverageCount }} 单）</dt>
            <dd>¥{{ money(preview.workorderOverageAmount) }}</dd>
          </div>
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
import { notifyWarning } from '@/utils/notification';

export default {
  name: 'SubscriptionOrderDetailPage',
  /**
   * 保存目标套餐、企业财务概览、服务端试算结果和用户选择的订阅周期。试算结果是金额及可订阅性的唯一依据，
   * 页面不自行复制后端的套餐变更、抵扣或超额工单计费规则。
   */
  data() {
    return {
      loading: true,
      submitting: false,
      plan: null,
      overview: { wallet: {}, subscription: {} },
      preview: null,
      form: { periodCount: 1, autoRenew: false }
    };
  },
  computed: {
    /**
     * 根据试算返回的订单类型展示首次购买、续订或改订，未知类型使用通用套餐订单文案。
     */
    orderTypeName() {
      return { BUY: '首次订阅', RENEW: '续订套餐', CHANGE_PLAN: '改订套餐' }[this.preview?.orderType] || '套餐订单';
    },
    /**
     * 套餐购买和余额支出只允许企业拥有者或管理员执行，权限来源与全局企业上下文保持一致。
     */
    canManageFinance() {
      return this.$store.getters.canManageFinance;
    },
    /**
     * 使用后端最终应付金额与钱包余额比较，套餐基础价、抵扣及超额工单费用均已包含在应付金额中。
     */
    isBalanceInsufficient() {
      return Number(this.preview?.payableAmount || 0) > Number(this.overview.wallet?.balanceAmount || 0);
    },
    /**
     * 计算需要补充的最小余额并限制结果不小于零，供充值页预填充值金额。
     */
    shortfallAmount() {
      return Math.max(0, Number(this.preview?.payableAmount || 0) - Number(this.overview.wallet?.balanceAmount || 0));
    }
  },
  /**
   * 页面创建后并行加载财务上下文与套餐数据，再请求对应周期的权威订单试算。
   */
  async created() {
    await this.loadPage();
  },
  methods: {
    /**
     * 将各类订单金额统一格式化为两位小数，空值按零显示。
     */
    money(value) {
      return Number(value || 0).toFixed(2);
    },
    /**
     * 将套餐计费周期枚举转换为年、月、日单位，用于价格和周期说明。
     */
    periodName(period) {
      return period === 'YEAR' ? '年' : period === 'MONTH' ? '月' : '日';
    },
    /**
     * 并行读取钱包订阅概览和可售套餐，按路由 planId 选定目标套餐。套餐标识在前后端契约中按字符串
     * 传递，以避免 Java Long 超出 JavaScript 安全整数范围时发生精度损失；路由显式传值时沿用用户在
     * 充值往返流程中的选择，最后加载试算并校正最低购买周期。
     */
    async loadPage() {
      this.loading = true;
      try {
        const [overviewResponse, plansResponse] = await Promise.all([getFinanceOverview(), getPlans()]);
        this.overview = overviewResponse.data;
        const routePlanId = String(this.$route.params.planId || '');
        this.plan = plansResponse.data.find(item => String(item.id) === routePlanId) || null;
        if (!this.plan) return;

        this.form.autoRenew = this.$route.query.autoRenew === undefined
          ? Number(this.overview.subscription?.status) === 1 && Boolean(this.overview.subscription?.autoRenewEnabled)
          : this.$route.query.autoRenew === 'true';
        this.form.periodCount = Math.max(1, Number(this.$route.query.periodCount || 1));
        await this.loadPreview(true);
      } finally {
        this.loading = false;
      }
    },
    /**
     * 向服务端请求套餐订单试算。首次加载若用户周期低于后端给出的最低周期，则更新表单并再次试算，
     * 确保展示的基础费用、套餐差额、超额工单费用和最终应付金额全部对应合法周期。
     */
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
    /**
     * 将周期输入修正为不低于最低周期的整数，再重新试算，避免小数、空值或负数进入下单请求。
     */
    async normalizePeriod() {
      const minimum = this.preview?.minimumPeriodCount || 1;
      this.form.periodCount = Math.max(minimum, Math.floor(Number(this.form.periodCount) || minimum));
      await this.loadPreview();
    },
    /**
     * 响应周期加减按钮，并复用统一校正逻辑处理下限和试算刷新。
     */
    async changePeriod(delta) {
      this.form.periodCount += delta;
      await this.normalizePeriod();
    },
    /**
     * 将应付金额、当前余额、缺口及待购买套餐上下文带到充值页；充值成功后可据此继续原订阅订单。
     */
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
    /**
     * 提交前再次规范周期并检查后端试算资格。余额不足时转入充值流程；余额足够则创建套餐订单，
     * 成功后回到套餐服务页展示订单号。并发余额变化导致的 409 同样转入充值，其余错误由请求层统一提示。
     */
    async submitOrder() {
      if (!this.canManageFinance) return;
      await this.normalizePeriod();
      if (!this.preview.eligible) {
        notifyWarning(this.preview.validationMessage || '请求异常');
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
        // Request errors are displayed by the Axios interceptor.
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
