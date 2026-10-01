<template>
  <section>
    <div class="section-title">
      <div>
        <h1>余额充值</h1>
        <p>输入充值金额，下一步将在站内安全完成支付</p>
      </div>
      <router-link class="layui-btn layui-btn-primary portal-btn" :to="returnDestination" replace>返回</router-link>
    </div>

    <div class="recharge-layout" :class="{ single: !hasOrderContext }">
      <article class="portal-card recharge-panel">
        <div class="panel-heading">
          <div>
            <span class="step-label">创建订单</span>
            <h2>{{ hasOrderContext ? '补充企业余额' : '选择充值金额' }}</h2>
          </div>
          <span class="balance-label">当前余额 <strong>¥{{ money(balanceAmount) }}</strong></span>
        </div>

        <form class="recharge-form" @submit.prevent="createOrder">
          <div class="form-field">
            <label for="rechargeAmount">充值金额</label>
            <div class="amount-input">
              <span>¥</span>
              <input id="rechargeAmount" v-model.trim="form.amount" class="layui-input" inputmode="decimal" :readonly="hasOrderContext" :min="minimumRechargeAmount" :max="maximumRechargeAmount" step="0.01" placeholder="请输入充值金额" />
            </div>
            <span class="amount-limit">单笔充值限额：¥{{ money(minimumRechargeAmount) }}–¥{{ money(maximumRechargeAmount) }}</span>
          </div>
          <div v-if="!hasOrderContext" class="quick-amounts" aria-label="常用充值金额">
            <button v-for="amount in quickAmounts" :key="amount" class="quick-amount" type="button" @click="form.amount = String(amount)">¥{{ amount }}</button>
          </div>
          <p class="payment-hint">可用的银行卡、钱包及本地支付方式将根据币种和地区展示，请在结账时选择。</p>
          <button class="layui-btn portal-btn portal-btn-primary submit-button" :disabled="submitting" type="submit">
            {{ submitting ? '创建中' : '创建充值订单' }}
          </button>
        </form>

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
import { createRechargeOrder, getFinanceOverview } from '@/api/portal';
import { notifyWarning } from '@/utils/notification';
import { rechargeContextKey, normalizeRechargeContext, saveRechargeContext } from '@/utils/rechargeSubscriptionContext';
import { normalizeRechargeReturnPath } from '@/utils/rechargeReturnPath';

export default {
  name: 'RechargePlaceholderPage',
  /**
   * 创建页是充值流程中间步骤，捕获真正业务入口并随订单传递。
   * 刷新时复用当前 URL；直接进入创建页默认回到订阅服务，不依赖浏览器后退栈。
   */
  beforeRouteEnter(to, from, next) {
    next(vm => {
      vm.rechargeReturnPath = normalizeRechargeReturnPath(
        from.matched.length ? from.fullPath : to.query.returnTo || '/portal/finance/subscription', vm.$router);
      if (to.query.returnTo !== vm.rechargeReturnPath) {
        vm.$router.replace({ path: to.path, query: { ...to.query, returnTo: vm.rechargeReturnPath } });
      }
    });
  },
  /**
   * 保存充值表单、快捷金额、当前待处理订单和钱包余额。路由查询参数承担套餐订单与充值流程之间的上下文传递。
   */
  data() {
    return {
      form: { amount: '' },
      rechargeReturnPath: '/portal/finance/subscription',
      quickAmounts: [100, 500, 1000, 5000],
      balanceAmount: 0,
      minimumRechargeAmount: 5,
      maximumRechargeAmount: 50000,
      submitting: false
    };
  },
  computed: {
    /**
     * 余额不足进入充值时，使用随路由保留的套餐、周期与续费选择返回订单试算页。
     * 此处不复用资金缺口快照，返回后由服务端重新试算；普通充值则返回订阅服务页。
     */
    returnDestination() {
      if (this.hasOrderContext) {
        return {
          name: 'finance-subscription-order-detail',
          params: { planId: String(this.$route.query.planId) },
          query: { periodCount: this.$route.query.periodCount, autoRenew: this.$route.query.autoRenew }
        };
      }
      return { name: 'finance-subscription' };
    },
    /**
     * 原套餐订单的最终应付金额，用于向用户说明充值原因。
     */
    requiredAmount() {
      return Number(this.$route.query.requiredAmount || 0);
    },
    /**
     * 进入充值流程时的钱包余额快照，仅用于展示原始缺口计算背景。
     */
    originalBalanceAmount() {
      return Number(this.$route.query.balanceAmount || 0);
    },
    /**
     * 读取套餐订单计算出的余额缺口，页面初始化时优先将其作为充值金额。
     */
    shortfallAmount() {
      return Number(this.$route.query.shortfallAmount || 0);
    },
    /**
     * 路由携带 planId 表示充值结束后需要继续完成套餐订单，而非普通独立充值。
     */
    hasOrderContext() {
      return Boolean(this.$route.query.planId);
    }
  },
  /**
   * 初始化钱包最新余额，并按套餐订单余额缺口预填建议充值金额。
   */
  async created() {
    const response = await getFinanceOverview();
    this.balanceAmount = Number(response.data.wallet?.balanceAmount || 0);
    this.minimumRechargeAmount = Number(response.data.rechargeLimits?.minimumAmount || 5);
    this.maximumRechargeAmount = Number(response.data.rechargeLimits?.maximumAmount || 50000);
    if (this.hasOrderContext) this.form.amount = this.money(Math.max(this.shortfallAmount, this.minimumRechargeAmount));
  },
  methods: {
    /**
     * 将充值金额、余额和缺口统一格式化为两位小数。
     */
    money(value) {
      return Number(value || 0).toFixed(2);
    },
    /**
     * 按财务概览返回的服务器充值上下限校验金额后创建订单，并跳转订单详情；后端仍会重复执行
     * 相同边界校验，防止绕过页面直接提交。套餐上下文继续随路由保留。
     */
    async createOrder() {
      /*
       * 套餐余额不足的补充充值固定使用原流程传入的缺口，提交时不读取可编辑表单值。
       * 低于充值下限时取最小允许金额，避免小额缺口无法支付；超出单笔上限仍由下方校验提示。
       */
      const amount = this.hasOrderContext
        ? Math.max(this.shortfallAmount, this.minimumRechargeAmount)
        : Number(this.form.amount);
      if (!Number.isFinite(amount) || amount < this.minimumRechargeAmount) {
        notifyWarning(`单笔充值金额不能低于 ¥${this.money(this.minimumRechargeAmount)}`);
        return;
      }
      if (amount > this.maximumRechargeAmount) {
        notifyWarning(`单笔充值金额不能超过 ¥${this.money(this.maximumRechargeAmount)}`);
        return;
      }
      if (!this.hasOrderContext && !/^\d+(\.\d{1,2})?$/.test(this.form.amount)) {
        notifyWarning('充值金额最多保留两位小数');
        return;
      }
      this.submitting = true;
      try {
        const response = await createRechargeOrder({ amount: amount.toFixed(2) });
        /*
         * 在打开 Stripe 前按充值订单保存原套餐意图，外部支付回跳丢失 query 时可恢复。
         * 存储失败时停留当前页面，不启动支付，避免充值后没有套餐续接信息。
         */
        const context = normalizeRechargeContext(this.$route.query);
        if (context) {
          try { saveRechargeContext(rechargeContextKey(response.data.id, this.$store.state), context); }
          catch (error) { notifyWarning('无法保存套餐选择，请允许浏览器使用会话存储后重试。'); return; }
        }
        await this.$router.push({
          name: 'finance-recharge-detail',
          params: { id: response.data.id },
          query: { ...this.$route.query, returnTo: this.rechargeReturnPath }
        });
      } finally {
        this.submitting = false;
      }
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

.amount-limit {
  display: block;
  margin-top: 8px;
  color: var(--portal-muted);
  font-size: 13px;
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

.payment-hint {
  margin: 20px 0 0;
  color: var(--portal-muted);
  line-height: 1.65;
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
