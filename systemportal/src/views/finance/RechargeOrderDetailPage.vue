<template>
  <section>
    <div class="section-title">
      <div>
        <h1>充值订单详情</h1>
        <p>查看订单状态并完成模拟支付</p>
      </div>
      <router-link class="layui-btn layui-btn-primary portal-btn" to="/portal/finance/recharges">返回订单列表</router-link>
    </div>

    <article v-if="order" class="portal-card detail-panel">
      <div class="detail-heading">
        <div><span>充值订单号</span><h2>{{ order.rechargeNo }}</h2></div>
        <span class="portal-tag" :class="{ warn: order.status === 1, gray: order.status === 3 }">{{ statusName('recharge', order.status) }}</span>
      </div>
      <dl class="detail-grid">
        <div><dt>充值金额</dt><dd class="amount">¥{{ money(order.amount) }}</dd></div>
        <div><dt>支付渠道</dt><dd>{{ channelName(order.payChannel) }}</dd></div>
        <div><dt>第三方交易号</dt><dd>{{ order.payTradeNo || '-' }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ order.createdAt || '-' }}</dd></div>
        <div><dt>支付时间</dt><dd>{{ order.paidAt || '-' }}</dd></div>
        <div><dt>当前状态</dt><dd>{{ statusName('recharge', order.status) }}</dd></div>
      </dl>
      <div v-if="order.status === 1" class="detail-actions">
        <button class="layui-btn layui-btn-primary layui-border-red portal-btn" :disabled="submitting" @click="openCancelDialog">取消订单</button>
        <button class="layui-btn portal-btn portal-btn-primary" :disabled="submitting" @click="completePayment">{{ submitting ? '处理中' : '完成支付' }}</button>
      </div>
      <div v-else-if="order.status === 2 && hasSubscriptionContext" class="subscription-resume">
        <div>
          <strong>充值已到账</strong>
          <span>系统将使用企业余额完成原套餐订单。</span>
        </div>
        <button class="layui-btn portal-btn portal-btn-primary" :disabled="submitting" @click="completePendingSubscription">
          {{ submitting ? '正在开通套餐' : '继续完成套餐订阅' }}
        </button>
      </div>
    </article>

    <ConfirmDialog
      :visible="cancelDialog"
      title="取消充值订单"
      message="确定取消当前充值订单吗？取消后不能继续支付，需要重新创建订单。"
      confirm-text="确认取消"
      :loading="submitting"
      @cancel="cancelDialog = false"
      @confirm="cancelOrder"
    />
  </section>
</template>

<script>
import { cancelRechargeOrder, completeRechargeOrder, createSubscriptionOrder, getRechargeOrder } from '@/api/portal';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import { getStatusName } from '@/utils/portalLabels';

export default {
  name: 'RechargeOrderDetailPage',
  components: { ConfirmDialog },
  /**
   * 保存充值订单详情及提交、取消状态。路由查询参数可能同时携带待完成的套餐订单上下文，
   * 充值完成后可无缝继续原套餐购买流程。
   */
  data() {
    return { order: null, submitting: false, cancelDialog: false };
  },
  computed: {
    /**
     * 同时存在套餐和周期参数时，说明本次充值由余额不足的套餐订单引导而来。
     */
    hasSubscriptionContext() {
      return Boolean(this.$route.query.planId && this.$route.query.periodCount);
    }
  },
  /**
   * 页面创建后按路由订单 ID 查询企业名下的充值订单详情。
   */
  created() {
    this.loadOrder();
  },
  methods: {
    statusName: getStatusName,
    /**
     * 将订单金额和余额统一显示为两位小数。
     */
    money(value) {
      return Number(value || 0).toFixed(2);
    },
    /**
     * 将支付渠道代码转换为详情页使用的完整名称，未知渠道保留原值便于排查。
     */
    channelName(channel) {
      return { WECHAT: '微信支付', ALIPAY: '支付宝', BANK: '银行转账' }[channel] || channel;
    },
    /**
     * 读取当前企业范围内的充值订单，企业隔离及不存在校验由后端接口负责。
     */
    async loadOrder() {
      const response = await getRechargeOrder(this.$route.params.id);
      this.order = response.data;
    },
    /**
     * 仅允许待支付订单进入模拟支付完成流程。入账成功后更新本地订单；若存在套餐上下文，
     * 立即使用新增余额继续创建原套餐订单。
     */
    async completePayment() {
      if (!this.order || this.order.status !== 1) return;
      this.submitting = true;
      try {
        const response = await completeRechargeOrder({ rechargeOrderId: this.order.id });
        this.order = response.data;
        await this.completePendingSubscription();
      } finally {
        this.submitting = false;
      }
    },
    /**
     * 根据充值流程保留的套餐、周期和自动续订参数创建套餐订单，成功后返回套餐服务页展示订单号。
     */
    async completePendingSubscription() {
      if (!this.hasSubscriptionContext) return;
      this.submitting = true;
      try {
        const response = await createSubscriptionOrder({
          planId: String(this.$route.query.planId),
          periodCount: Number(this.$route.query.periodCount),
          autoRenew: this.$route.query.autoRenew === 'true'
        });
        await this.$router.push({
          name: 'finance-subscription',
          query: { orderNo: response.data.orderNo }
        });
      } finally {
        this.submitting = false;
      }
    },
    /**
     * 打开取消充值订单的二次确认框。
     */
    openCancelDialog() {
      this.cancelDialog = true;
    },
    /**
     * 请求取消当前充值订单，并以服务端返回状态覆盖页面快照，确保取消结果真实生效。
     */
    async cancelOrder() {
      this.submitting = true;
      try {
        const response = await cancelRechargeOrder(this.order.id);
        this.order = response.data;
        this.cancelDialog = false;
      } finally {
        this.submitting = false;
      }
    }
  }
};
</script>

<style scoped>
.detail-panel {
  max-width: 820px;
  padding: 26px;
  margin: 20px auto 0;
}

.detail-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--portal-border);
}

.detail-heading span,
.detail-grid dt {
  color: var(--portal-muted);
}

.detail-heading h2 {
  margin: 5px 0 0;
  font-size: 20px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 30px;
  margin: 0;
}

.detail-grid div {
  padding: 18px 0;
  border-bottom: 1px solid var(--portal-border);
}

.detail-grid dd {
  margin: 6px 0 0;
  overflow-wrap: anywhere;
  font-weight: 600;
}

.detail-grid .amount {
  color: var(--portal-accent-strong);
  font-size: 22px;
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}

.detail-actions .layui-btn {
  margin: 0;
}

.subscription-resume {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 16px;
  margin-top: 24px;
  border-left: 3px solid var(--portal-accent);
  background: #eefbf3;
}

.subscription-resume div {
  display: grid;
  gap: 4px;
}

.subscription-resume span {
  color: var(--portal-muted);
}

.subscription-resume .layui-btn {
  margin: 0;
  white-space: nowrap;
}

@media (max-width: 620px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .subscription-resume {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
