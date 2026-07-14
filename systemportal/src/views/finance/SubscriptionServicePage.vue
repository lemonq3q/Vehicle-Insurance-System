<template>
  <section>
    <div class="section-title">
      <div>
        <h1>订阅服务</h1>
        <p>查看当前订阅并选择套餐</p>
      </div>
      <button class="layui-btn layui-btn-primary layui-border-green portal-btn" type="button" :disabled="!canManageFinance" @click="goToRecharge">余额充值</button>
    </div>

    <div class="stat-grid">
      <div class="portal-card stat-card">
        <div class="stat-label">企业余额</div>
        <div class="stat-value">¥{{ overview.wallet?.balanceAmount }}</div>
        <div class="stat-note">币种 {{ overview.wallet?.currency || 'CNY' }}</div>
      </div>
      <div class="portal-card stat-card">
        <div class="stat-label">当前套餐</div>
        <div class="stat-value">{{ overview.subscription?.plan?.name || '-' }}</div>
        <div class="stat-note">{{ overview.subscription?.userLimit || 0 }} 人上限</div>
      </div>
      <div class="portal-card stat-card">
        <div class="stat-label">到期时间</div>
        <div class="stat-value small">{{ overview.subscription?.endAt || '-' }}</div>
        <div class="stat-note">下次续费 {{ overview.subscription?.nextRenewAt || '-' }}</div>
      </div>
      <div class="portal-card stat-card">
        <div class="stat-label">自动续费</div>
        <div class="stat-value">{{ overview.subscription?.autoRenewEnabled ? '已开启' : '未开启' }}</div>
        <div class="stat-note">
          <button class="layui-btn layui-btn-xs layui-btn-primary layui-border-green" :disabled="!canManageFinance" @click="toggleAutoRenew">
            {{ overview.subscription?.autoRenewEnabled ? '关闭' : '开启' }}
          </button>
        </div>
      </div>
    </div>

    <div class="portal-card plan-list">
      <article v-for="plan in plans" :key="plan.id" class="plan-card">
        <div class="plan-head">
          <span class="portal-tag" :class="{ blue: plan.id === overview.subscription?.planId }">
            {{ plan.id === overview.subscription?.planId ? '当前套餐' : '可订阅' }}
          </span>
          <strong>{{ plan.name }}</strong>
        </div>
        <p>{{ plan.description }}</p>
        <div class="price">¥{{ plan.price }}<small>/{{ periodName(plan.billingPeriod) }}</small></div>
        <ul>
          <li>成员上限：{{ plan.userLimit }} 人</li>
          <li>有效天数：{{ plan.durationDays }} 天</li>
          <li>原价：¥{{ plan.originalPrice }}</li>
        </ul>
        <button class="layui-btn portal-btn portal-btn-primary plan-action" :disabled="!canManageFinance" @click="openOrder(plan)">
          {{ actionName(plan) }}
        </button>
      </article>
    </div>

    <p class="message" aria-live="polite">{{ message }}</p>
  </section>
</template>

<script>
import { getFinanceOverview, getPlans, updateAutoRenew } from '@/api/portal';

export default {
  name: 'SubscriptionServicePage',
  data() {
    return {
      overview: { wallet: {}, subscription: { plan: {} } },
      plans: [],
      message: ''
    };
  },
  async created() {
    await this.loadData();
  },
  computed: {
    canManageFinance() {
      return this.$store.getters.canManageFinance;
    }
  },
  methods: {
    async loadData() {
      const [overview, plans] = await Promise.all([getFinanceOverview(), getPlans()]);
      this.overview = overview.data;
      this.plans = plans.data;
      this.message = this.$route.query.orderNo ? `订单 ${this.$route.query.orderNo} 已完成支付，套餐订阅已更新。` : '';
    },
    periodName(period) {
      return period === 'YEAR' ? '年' : period === 'MONTH' ? '月' : '天';
    },
    actionName(plan) {
      if (!this.overview.subscription?.id) return '订阅套餐';
      return plan.id === this.overview.subscription.planId ? '续订套餐' : '改订套餐';
    },
    goToRecharge() {
      if (!this.canManageFinance) return;
      this.$router.push('/portal/finance/recharge');
    },
    openOrder(plan) {
      if (!this.canManageFinance) return;
      this.$router.push({
        name: 'finance-subscription-order-detail',
        params: { planId: plan.id }
      });
    },
    async toggleAutoRenew() {
      if (!this.canManageFinance) return;
      const response = await updateAutoRenew({ autoRenewEnabled: !this.overview.subscription?.autoRenewEnabled });
      this.message = response.msg;
      await this.loadData();
    }
  }
};
</script>

<style scoped>
.stat-value.small {
  font-size: 18px;
}

.plan-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 18px;
  overflow: hidden;
}

.plan-card {
  position: relative;
  min-width: 0;
  padding: 24px;
}

.plan-card:not(:first-child)::before {
  position: absolute;
  top: 24px;
  bottom: 24px;
  left: 0;
  width: 1px;
  background: var(--portal-border);
  content: '';
}

.plan-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.plan-head strong {
  font-size: 20px;
}

.plan-card p {
  min-height: 54px;
  color: var(--portal-muted);
}

.price {
  margin: 18px 0;
  font-size: 32px;
  font-weight: 800;
}

.price small {
  color: var(--portal-muted);
  font-size: 13px;
}

.plan-card ul {
  padding-left: 18px;
  color: var(--portal-secondary);
}

.plan-action {
  width: 100%;
  margin-top: 16px;
}

.section-title .portal-btn:disabled,
.stat-note button:disabled,
.plan-action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.message {
  color: #166534;
}

@media (max-width: 1100px) {
  .plan-list {
    grid-template-columns: 1fr;
  }

  .plan-card {
    position: relative;
  }

  .plan-card:not(:first-child)::before {
    top: 0;
    right: 24px;
    bottom: auto;
    left: 24px;
    width: auto;
    height: 1px;
  }
}
</style>
