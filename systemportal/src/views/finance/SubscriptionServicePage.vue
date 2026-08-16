<template>
  <section>
    <div class="section-title">
      <div>
        <h1>订阅服务</h1>
        <p>查看当前订阅并选择套餐</p>
      </div>
      <button class="layui-btn layui-btn-primary layui-border-green portal-btn" type="button" :disabled="!canManageFinance" @click="goToRecharge">余额充值</button>
    </div>

    <div v-if="isSuspended" class="subscription-alert" role="alert" aria-live="polite">
      <svg class="subscription-alert-icon" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 9v4m0 4h.01M10.3 3.7 2.4 17.4A2 2 0 0 0 4.1 20h15.8a2 2 0 0 0 1.7-2.6L13.7 3.7a2 2 0 0 0-3.4 0Z" />
      </svg>
      <div class="subscription-alert-content">
        <strong>当前套餐已暂停</strong>
        <p>{{ suspensionMessage }}</p>
      </div>
      <button v-if="canManageFinance" class="layui-btn portal-btn subscription-alert-action" type="button" @click="goToRecharge">
        前往充值
      </button>
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
     * 自动续订开关同时要求财务管理权限和一份尚未到期的当前套餐；欠费暂停不改变套餐本身。
     */
    canUpdateAutoRenew() {
      return this.canManageFinance && this.hasCurrentSubscription;
    },
    /**
     * 订阅状态值 1 表示套餐正在生效，暂停、取消或未订阅均不作为当前套餐使用。
     */
    hasActiveSubscription() {
      return Number(this.overview.subscription?.status) === 1;
    },
    /**
     * 状态 3 表示套餐暂停。页面只在该状态展示原因告警，不把未订阅和已过期错误描述为暂停。
     */
    isSuspended() {
      return Number(this.overview.subscription?.status) === 3;
    },
    /**
     * 正常或暂停的未过期套餐都仍是企业当前套餐，可继续充值、续订或改订；服务端负责最终有效期校验。
     */
    hasCurrentSubscription() {
      return [1, 3].includes(Number(this.overview.subscription?.status));
    },
    /**
     * 将服务端暂停原因转换为包含恢复路径的用户提示，未知原因使用保守文案并避免承诺自动恢复。
     */
    suspensionMessage() {
      const messages = {
        ARREARS: '企业余额低于欠费停止阈值，车险后台服务暂不可用。充值后余额超过恢复阈值将自动恢复。',
        MANUAL: '套餐已由平台人工暂停，如需恢复请联系平台客服。',
        RISK_CONTROL: '套餐因风险控制暂停，如需恢复请联系平台客服完成核验。'
      };
      return messages[this.overview.subscription?.suspendReason] || '套餐当前暂停，请联系平台客服确认原因及恢复方式。';
    },
    /**
     * 有效订阅显示关联套餐名称，关联数据异常和未订阅状态使用不同兜底文案便于排查。
     */
    currentPlanName() {
      if (!this.hasCurrentSubscription) return '暂未订阅';
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
      if (!this.hasCurrentSubscription) return '订阅套餐';
      return plan.id === this.overview.subscription.planId ? '续订套餐' : '改订套餐';
    },
    /**
     * 比较套餐 ID 判断套餐卡片是否为企业当前正在生效的方案。
     */
    isCurrentPlan(plan) {
      return this.hasCurrentSubscription && plan.id === this.overview.subscription?.planId;
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

.subscription-alert {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 18px;
  padding: 16px 18px;
  border: 1px solid #f3b7b7;
  border-radius: 8px;
  background: #fff5f5;
  color: #8b1e1e;
}

.subscription-alert-icon {
  flex: 0 0 22px;
  width: 22px;
  height: 22px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.subscription-alert-content {
  flex: 1;
  min-width: 0;
}

.subscription-alert-content strong {
  display: block;
  margin-bottom: 4px;
}

.subscription-alert-content p {
  margin: 0;
  line-height: 1.6;
}

.subscription-alert-action {
  flex: 0 0 auto;
  min-height: 40px;
  border-color: #b42318;
  background: #b42318;
  color: #fff;
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
  .subscription-alert {
    flex-wrap: wrap;
  }

  .subscription-alert-action {
    margin-left: 34px;
  }

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
