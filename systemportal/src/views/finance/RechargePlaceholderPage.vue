<template>
  <section>
    <div class="section-title">
      <div>
        <h1>余额充值</h1>
        <p>在线支付能力正在接入，本页面暂时用于承接余额不足的订阅订单</p>
      </div>
    </div>

    <article class="portal-card placeholder-panel">
      <span class="placeholder-icon" aria-hidden="true"><i class="layui-icon layui-icon-rmb"></i></span>
      <h2>支付平台即将上线</h2>
      <p>后续可在此选择支付渠道并完成企业余额充值，充值成功后将返回原订阅订单继续支付。</p>

      <div v-if="hasOrderContext" class="amount-summary">
        <div><span>订单应付</span><strong>¥{{ money(requiredAmount) }}</strong></div>
        <div><span>当前余额</span><strong>¥{{ money(balanceAmount) }}</strong></div>
        <div class="shortfall"><span>还需充值</span><strong>¥{{ money(shortfallAmount) }}</strong></div>
      </div>

      <div class="placeholder-actions">
        <button class="layui-btn portal-btn portal-btn-primary" type="button" @click="returnToOrder">返回订单</button>
        <router-link class="layui-btn layui-btn-primary portal-btn" to="/portal/finance/recharges">查看充值订单</router-link>
      </div>
    </article>
  </section>
</template>

<script>
export default {
  name: 'RechargePlaceholderPage',
  computed: {
    requiredAmount() {
      return Number(this.$route.query.requiredAmount || 0);
    },
    balanceAmount() {
      return Number(this.$route.query.balanceAmount || 0);
    },
    shortfallAmount() {
      return Number(this.$route.query.shortfallAmount || 0);
    },
    hasOrderContext() {
      return Boolean(this.$route.query.planId);
    }
  },
  methods: {
    money(value) {
      return Number(value || 0).toFixed(2);
    },
    returnToOrder() {
      if (!this.hasOrderContext) {
        this.$router.push('/portal/finance/subscription');
        return;
      }
      this.$router.push({
        name: 'finance-subscription-order-detail',
        params: { planId: this.$route.query.planId },
        query: {
          periodCount: this.$route.query.periodCount,
          autoRenew: this.$route.query.autoRenew
        }
      });
    }
  }
};
</script>

<style scoped>
.placeholder-panel {
  max-width: 680px;
  padding: 40px;
  margin: 40px auto 0;
  text-align: center;
}

.placeholder-icon {
  display: grid;
  width: 56px;
  height: 56px;
  place-items: center;
  margin: 0 auto 18px;
  border-radius: 50%;
  color: #166534;
  background: #e8f7ee;
}

.placeholder-icon i {
  font-size: 26px;
}

.placeholder-panel h2 {
  margin: 0 0 8px;
  font-size: 24px;
}

.placeholder-panel > p {
  max-width: 520px;
  margin: 0 auto;
  color: var(--portal-muted);
}

.amount-summary {
  margin: 28px 0;
  border: 1px solid var(--portal-border);
  border-radius: 6px;
  text-align: left;
}

.amount-summary div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
}

.amount-summary div + div {
  border-top: 1px solid var(--portal-border);
}

.amount-summary span {
  color: var(--portal-muted);
}

.amount-summary .shortfall strong {
  color: var(--portal-danger);
}

.placeholder-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 28px;
}

@media (max-width: 560px) {
  .placeholder-panel {
    padding: 28px 20px;
  }

  .placeholder-actions {
    flex-direction: column;
  }
}
</style>
