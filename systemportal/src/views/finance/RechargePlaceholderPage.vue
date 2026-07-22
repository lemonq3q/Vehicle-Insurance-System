<template>
  <section>
    <div class="section-title">
      <div>
        <h1>余额充值</h1>
        <p>创建充值订单并模拟支付到账</p>
      </div>
      <router-link class="layui-btn layui-btn-primary portal-btn" to="/portal/finance/recharges">充值订单</router-link>
    </div>

    <div class="recharge-layout" :class="{ single: !hasOrderContext }">
      <article class="portal-card recharge-panel">
        <div class="panel-heading">
          <div>
            <span class="step-label">{{ order ? '订单确认' : '创建订单' }}</span>
            <h2>{{ order ? '确认模拟支付' : '选择充值金额' }}</h2>
          </div>
          <span class="balance-label">当前余额 <strong>¥{{ money(balanceAmount) }}</strong></span>
        </div>

        <form v-if="!order" class="recharge-form" @submit.prevent="createOrder">
          <div class="form-field">
            <label for="rechargeAmount">充值金额</label>
            <div class="amount-input">
              <span>¥</span>
              <input id="rechargeAmount" v-model.trim="form.amount" class="layui-input" inputmode="decimal" placeholder="请输入充值金额" />
            </div>
          </div>
          <div class="quick-amounts" aria-label="常用充值金额">
            <button v-for="amount in quickAmounts" :key="amount" class="quick-amount" type="button" @click="form.amount = String(amount)">¥{{ amount }}</button>
          </div>
          <div class="form-field">
            <label for="payChannel">模拟支付渠道</label>
            <select id="payChannel" v-model="form.payChannel" class="layui-select">
              <option value="WECHAT">微信支付</option>
              <option value="ALIPAY">支付宝</option>
              <option value="BANK">银行转账</option>
            </select>
          </div>
          <button class="layui-btn portal-btn portal-btn-primary submit-button" :disabled="submitting" type="submit">
            {{ submitting ? '创建中' : '创建充值订单' }}
          </button>
        </form>

        <div v-else class="order-detail">
          <dl>
            <div><dt>充值订单号</dt><dd>{{ order.rechargeNo }}</dd></div>
            <div><dt>支付渠道</dt><dd>{{ channelName(order.payChannel) }}</dd></div>
            <div><dt>第三方交易号</dt><dd>{{ order.payTradeNo || '-' }}</dd></div>
            <div><dt>创建时间</dt><dd>{{ order.createdAt || '-' }}</dd></div>
            <div class="amount-row"><dt>到账金额</dt><dd>¥{{ money(order.amount) }}</dd></div>
          </dl>

          <div v-if="order.status === 2" class="paid-result">
            <i class="layui-icon layui-icon-ok-circle"></i>
            <div><strong>充值已到账</strong><span>当前企业余额 ¥{{ money(balanceAmount) }}</span></div>
          </div>

          <div class="order-actions">
            <button v-if="order.status === 1" class="layui-btn layui-btn-primary portal-btn" type="button" :disabled="submitting" @click="resetOrder">重新创建</button>
            <button v-if="order.status === 1" class="layui-btn portal-btn portal-btn-primary" type="button" :disabled="submitting" @click="completeOrder">
              {{ submitting ? '处理中' : '完成支付' }}
            </button>
            <button v-else class="layui-btn portal-btn portal-btn-primary" type="button" :disabled="submitting" @click="handlePaidOrder">
              {{ hasOrderContext ? (submitting ? '正在开通套餐' : '继续完成套餐订阅') : '返回订阅服务' }}
            </button>
          </div>
        </div>
      </article>

      <aside v-if="hasOrderContext" class="portal-card order-context">
        <h2>待支付订阅</h2>
        <div><span>订单应付</span><strong>¥{{ money(requiredAmount) }}</strong></div>
        <div><span>充值前余额</span><strong>¥{{ money(originalBalanceAmount) }}</strong></div>
        <div class="shortfall"><span>建议充值</span><strong>¥{{ money(shortfallAmount) }}</strong></div>
      </aside>
    </div>
  </section>
</template>

<script>
import { completeRechargeOrder, createRechargeOrder, createSubscriptionOrder, getFinanceOverview, getRechargeOrder } from '@/api/portal';
import { notifyWarning } from '@/utils/notification';

export default {
  name: 'RechargePlaceholderPage',
  data() {
    return {
      form: { amount: '', payChannel: 'WECHAT' },
      quickAmounts: [100, 500, 1000, 5000],
      order: null,
      balanceAmount: 0,
      submitting: false
    };
  },
  computed: {
    requiredAmount() {
      return Number(this.$route.query.requiredAmount || 0);
    },
    originalBalanceAmount() {
      return Number(this.$route.query.balanceAmount || 0);
    },
    shortfallAmount() {
      return Number(this.$route.query.shortfallAmount || 0);
    },
    hasOrderContext() {
      return Boolean(this.$route.query.planId);
    }
  },
  async created() {
    const response = await getFinanceOverview();
    this.balanceAmount = Number(response.data.wallet?.balanceAmount || 0);
    if (this.shortfallAmount > 0) this.form.amount = this.money(this.shortfallAmount);
    if (this.$route.query.rechargeOrderId) {
      const orderResponse = await getRechargeOrder(this.$route.query.rechargeOrderId);
      this.order = orderResponse.data;
    }
  },
  methods: {
    money(value) {
      return Number(value || 0).toFixed(2);
    },
    channelName(channel) {
      return { WECHAT: '微信支付', ALIPAY: '支付宝', BANK: '银行转账' }[channel] || channel;
    },
    async createOrder() {
      const amount = Number(this.form.amount);
      if (!Number.isFinite(amount) || amount <= 0) {
        notifyWarning('请输入大于 0 的充值金额');
        return;
      }
      this.submitting = true;
      try {
        const response = await createRechargeOrder({ amount: amount.toFixed(2), payChannel: this.form.payChannel });
        await this.$router.push({
          name: 'finance-recharge-detail',
          params: { id: response.data.id },
          query: { ...this.$route.query, rechargeOrderId: undefined }
        });
      } finally {
        this.submitting = false;
      }
    },
    async completeOrder() {
      if (!this.order || this.order.status !== 1) return;
      this.submitting = true;
      try {
        const response = await completeRechargeOrder({ rechargeOrderId: this.order.id });
        this.order = response.data;
        this.balanceAmount = Number(response.data.balanceAmount || this.balanceAmount);
        await this.completePendingSubscription();
      } finally {
        this.submitting = false;
      }
    },
    resetOrder() {
      this.order = null;
      const query = { ...this.$route.query };
      delete query.rechargeOrderId;
      this.$router.replace({ query });
    },
    async completePendingSubscription() {
      if (!this.hasOrderContext) return;
      const response = await createSubscriptionOrder({
        planId: Number(this.$route.query.planId),
        periodCount: Number(this.$route.query.periodCount),
        autoRenew: this.$route.query.autoRenew === 'true'
      });
      await this.$router.push({
        name: 'finance-subscription',
        query: { orderNo: response.data.orderNo }
      });
    },
    async handlePaidOrder() {
      if (!this.hasOrderContext) {
        this.returnToOrder();
        return;
      }
      this.submitting = true;
      try {
        await this.completePendingSubscription();
      } finally {
        this.submitting = false;
      }
    },
    returnToOrder() {
      if (!this.hasOrderContext) {
        this.$router.push('/portal/finance/subscription');
        return;
      }
      this.$router.push({
        name: 'finance-subscription-order-detail',
        params: { planId: this.$route.query.planId },
        query: { periodCount: this.$route.query.periodCount, autoRenew: this.$route.query.autoRenew }
      });
    }
  }
};
</script>

<style scoped>
.recharge-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 18px;
  max-width: 980px;
  margin: 20px auto 0;
}

.recharge-panel,
.order-context {
  padding: 26px;
}

.recharge-layout.single {
  grid-template-columns: minmax(0, 760px);
  justify-content: center;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--portal-border);
}

.panel-heading h2,
.order-context h2 {
  margin: 4px 0 0;
  font-size: 20px;
}

.step-label,
.balance-label,
.order-context span,
.order-detail dt,
.paid-result span {
  color: var(--portal-muted);
}

.balance-label strong {
  margin-left: 6px;
  color: var(--portal-text);
  font-size: 18px;
}

.recharge-form {
  margin-top: 24px;
}

.amount-input {
  position: relative;
}

.amount-input span {
  position: absolute;
  top: 9px;
  left: 13px;
  font-weight: 700;
}

.amount-input input {
  padding-left: 32px;
  font-size: 18px;
  font-weight: 700;
}

.quick-amounts {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin: 12px 0 22px;
}

.quick-amount {
  min-height: 38px;
  border: 1px solid var(--portal-border);
  border-radius: 4px;
  color: var(--portal-secondary);
  background: #fff;
  cursor: pointer;
}

.quick-amount:hover {
  border-color: var(--portal-accent);
  color: var(--portal-accent-strong);
}

.layui-select {
  width: 100%;
  height: 38px;
  border: 1px solid var(--portal-border);
  border-radius: 4px;
}

.submit-button {
  width: 100%;
  margin-top: 24px;
}

.order-detail dl {
  margin: 20px 0;
}

.order-detail dl div,
.order-context div {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 12px 0;
  border-bottom: 1px solid var(--portal-border);
}

.order-detail dd {
  margin: 0;
  text-align: right;
  overflow-wrap: anywhere;
}

.amount-row dd,
.order-context .shortfall strong {
  color: var(--portal-accent-strong);
  font-size: 20px;
  font-weight: 800;
}

.paid-result {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-left: 3px solid var(--portal-accent);
  background: #eefbf3;
}

.paid-result i {
  color: var(--portal-accent-strong);
  font-size: 26px;
}

.paid-result div {
  display: grid;
  gap: 3px;
}

.order-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}

.order-actions .layui-btn {
  margin: 0;
}

.submit-button:disabled,
.order-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

@media (max-width: 820px) {
  .recharge-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .panel-heading {
    flex-direction: column;
  }

  .quick-amounts {
    grid-template-columns: repeat(2, 1fr);
  }

  .order-actions {
    flex-direction: column;
  }
}
</style>
