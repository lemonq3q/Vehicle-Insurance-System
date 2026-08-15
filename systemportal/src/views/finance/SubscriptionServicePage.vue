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
        <div class="stat-value">¥{{ money(overview.wallet?.balanceAmount) }}</div>
        <div class="stat-note">币种 {{ overview.wallet?.currency || 'CNY' }}</div>
      </div>
      <div class="portal-card stat-card">
        <div class="stat-label">当前套餐</div>
        <div class="stat-value">{{ currentPlanName }}</div>
        <div class="stat-note">{{ overview.subscription?.userLimit || 0 }} 人 · {{ overview.subscription?.workorderLimit || 0 }} 单</div>
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
          <button class="layui-btn layui-btn-xs layui-btn-primary layui-border-green" :disabled="!canUpdateAutoRenew" @click="toggleAutoRenew">
            {{ overview.subscription?.autoRenewEnabled ? '关闭' : '开启' }}
          </button>
        </div>
      </div>
    </div>

    <div class="portal-card plan-list">
      <article v-for="plan in plans" :key="plan.id" class="plan-card">
        <div class="plan-head">
          <span class="portal-tag" :class="{ blue: isCurrentPlan(plan) }">
            {{ isCurrentPlan(plan) ? '当前套餐' : '可订阅' }}
          </span>
          <strong>{{ plan.name }}</strong>
        </div>
        <p>{{ plan.description }}</p>
        <div class="price">¥{{ plan.price }}<small>/{{ periodName(plan.billingPeriod) }}</small></div>
        <ul>
          <li>成员上限：{{ plan.userLimit }} 人</li>
          <li>工单额度：{{ plan.workorderLimit }} 单</li>
          <li>有效天数：{{ plan.durationDays }} 天</li>
          <li>原价：¥{{ plan.originalPrice }}</li>
        </ul>
        <button class="layui-btn portal-btn portal-btn-primary plan-action" :disabled="!canManageFinance" @click="openOrder(plan)">
          {{ actionName(plan) }}
        </button>
      </article>
    </div>

  </section>
</template>

<script>
import { getFinanceOverview, getPlans, updateAutoRenew } from '@/api/portal';

export default {
  name: 'SubscriptionServicePage',
  /**
   * 保存企业钱包、当前订阅快照和全部可售套餐，页面据此生成充值、续订或改订入口。
   */
  data() {
    return {
      overview: { wallet: {}, subscription: { status: 0, userLimit: 0, plan: {} } },
      plans: []
    };
  },
  /**
   * 首次进入套餐服务页时同步加载财务概览和套餐列表。
   */
  async created() {
    await this.loadData();
  },
  computed: {
    /**
     * 企业财务操作统一限制为拥有者和管理员。
     */
    canManageFinance() {
      return this.$store.getters.canManageFinance;
    },
    /**
     * 自动续订开关同时要求财务管理权限和一份当前有效订阅。
     */
    canUpdateAutoRenew() {
      return this.canManageFinance && this.hasActiveSubscription;
    },
    /**
     * 订阅状态值 1 表示套餐正在生效，暂停、取消或未订阅均不作为当前套餐使用。
     */
    hasActiveSubscription() {
      return Number(this.overview.subscription?.status) === 1;
    },
    /**
     * 有效订阅显示关联套餐名称，关联数据异常和未订阅状态使用不同兜底文案便于排查。
     */
    currentPlanName() {
      if (!this.hasActiveSubscription) return '暂未订阅';
      return this.overview.subscription?.plan?.name || '套餐信息缺失';
    }
  },
  methods: {
    /**
     * 并行读取企业财务概览与套餐列表，并为尚未创建钱包或订阅的企业补齐稳定默认结构。
     */
    async loadData() {
      const [overview, plans] = await Promise.all([getFinanceOverview(), getPlans()]);
      const data = overview.data || {};
      this.overview = {
        ...data,
        wallet: data.wallet || { balanceAmount: 0, currency: 'CNY' },
        subscription: data.subscription || { status: 0, userLimit: 0, plan: {} }
      };
      this.plans = plans.data;
    },
    /**
     * 将余额和套餐价格统一格式化为两位小数。
     */
    money(value) {
      return Number(value || 0).toFixed(2);
    },
    /**
     * 将套餐周期枚举转换为价格旁使用的中文单位。
     */
    periodName(period) {
      return period === 'YEAR' ? '年' : period === 'MONTH' ? '月' : '天';
    },
    /**
     * 未订阅时显示订阅；已有有效订阅时，当前套餐进入续订流程，其他套餐进入改订流程。
     */
    actionName(plan) {
      if (!this.hasActiveSubscription) return '订阅套餐';
      return plan.id === this.overview.subscription.planId ? '续订套餐' : '改订套餐';
    },
    /**
     * 比较套餐 ID 判断套餐卡片是否为企业当前正在生效的方案。
     */
    isCurrentPlan(plan) {
      return this.hasActiveSubscription && plan.id === this.overview.subscription?.planId;
    },
    /**
     * 财务管理者可进入独立充值页，普通成员即使触发方法也不会发生跳转。
     */
    goToRecharge() {
      if (!this.canManageFinance) return;
      this.$router.push('/portal/finance/recharge');
    },
    /**
     * 携带目标套餐 ID 进入订单试算页，由试算页决定购买、续订或改订及其最终费用。
     */
    openOrder(plan) {
      if (!this.canManageFinance) return;
      this.$router.push({
        name: 'finance-subscription-order-detail',
        params: { planId: plan.id }
      });
    },
    /**
     * 反转当前订阅的自动续订设置，并重新加载服务端订阅快照确认最终状态。
     */
    async toggleAutoRenew() {
      if (!this.canUpdateAutoRenew) return;
      await updateAutoRenew({ autoRenewEnabled: !this.overview.subscription?.autoRenewEnabled });
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
