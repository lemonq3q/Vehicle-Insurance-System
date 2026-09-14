<template>
  <section>
    <div class="section-title">
      <div>
        <h1>订阅订单详情</h1>
        <p>查看本次套餐订阅的权益快照、费用构成和支付结果</p>
      </div>
      <button class="layui-btn layui-btn-primary portal-btn" type="button" @click="backToOrders">返回订单列表</button>
    </div>

    <div v-if="loading" class="portal-card loading-state" aria-live="polite">正在加载订单...</div>

    <div v-else-if="order" class="order-layout">
      <article class="portal-card order-main-panel">
        <div class="panel-heading">
          <div>
            <div class="status-line">
              <span class="portal-tag" :class="{ blue: order.orderType === 'CHANGE_PLAN' }">{{ orderTypeName }}</span>
              <span class="portal-tag" :class="{ warn: [1, 6].includes(Number(order.status)) }">{{ statusName('order', order.status) }}</span>
            </div>
            <h2>{{ planName }}</h2>
            <p class="order-number">订单号：{{ order.orderNo }}</p>
          </div>
          <div class="plan-price">¥{{ money(order.priceAmount ?? order.payableAmount) }}<small>/套餐金额</small></div>
        </div>

        <div class="plan-facts">
          <div><span>成员上限</span><strong>{{ displayValue(order.buyUserLimit, '人') }}</strong></div>
          <div><span>工单额度</span><strong>{{ displayValue(order.buyWorkorderLimit, '单') }}</strong></div>
          <div><span>订阅周期</span><strong>{{ displayValue(order.periodCount || 1, '个周期') }}</strong></div>
          <div><span>服务时长</span><strong>{{ displayValue(order.buyDurationDays, '天') }}</strong></div>
        </div>

        <section class="detail-section" aria-labelledby="order-info-title">
          <h3 id="order-info-title">订单信息</h3>
          <dl class="detail-grid">
            <div><dt>支付方式</dt><dd>{{ payTypeName }}</dd></div>
            <div><dt>自动续费</dt><dd>{{ order.autoRenew ? '已开启' : '未开启' }}</dd></div>
            <div><dt>创建时间</dt><dd>{{ order.createdAt || '-' }}</dd></div>
            <div><dt>支付时间</dt><dd>{{ order.paidAt || '-' }}</dd></div>
          </dl>
        </section>

        <section v-if="order.failureReason" class="failure-card" aria-labelledby="failure-title">
          <h3 id="failure-title">订单未完成</h3>
          <p>{{ order.failureReason }}</p>
        </section>
      </article>

      <aside class="portal-card amount-panel">
        <h2>金额明细</h2>
        <dl>
          <div><dt>套餐金额</dt><dd>¥{{ money(order.priceAmount ?? order.payableAmount) }}</dd></div>
          <div v-if="Number(order.discountAmount || 0) > 0"><dt>优惠金额</dt><dd class="credit">-¥{{ money(order.discountAmount) }}</dd></div>
          <div v-if="Number(order.creditAmount || 0) > 0"><dt>原套餐剩余价值抵扣</dt><dd class="credit">-¥{{ money(order.creditAmount) }}</dd></div>
          <div v-if="Number(order.workorderOverageCount || 0) > 0">
            <dt>超额工单费（{{ order.workorderOverageCount }} 单）</dt>
            <dd>¥{{ money(order.workorderOverageAmount) }}</dd>
          </div>
          <div v-if="Number(order.refundAmount || 0) > 0"><dt>退回企业余额</dt><dd class="refund">+¥{{ money(order.refundAmount) }}</dd></div>
          <div class="divider"><dt>应付金额</dt><dd>¥{{ money(order.payableAmount) }}</dd></div>
        </dl>
        <div class="total-row">
          <span>实付金额</span>
          <strong>¥{{ money(order.paidAmount) }}</strong>
        </div>
        <p class="amount-note">金额以订单创建时的套餐和计费规则为准。</p>
      </aside>
    </div>

    <div v-else class="portal-card empty-state">未找到该订阅订单，请返回订单列表重新选择。</div>
  </section>
</template>

<script>
import { getSubscriptionOrder } from '@/api/portal';
import { getStatusName } from '@/utils/portalLabels';

/**
 * 历史订阅订单只读详情页。
 * 页面沿用用户购买套餐时的双栏订单布局，左侧展示下单时的套餐权益快照和订单状态，右侧展示费用构成；
 * 所有数据均通过当前企业范围内的详情接口读取，不根据当前套餐配置反推历史订单，也不在该页面执行支付或重试。
 */
export default {
  name: 'SubscriptionOrderRecordDetailPage',
  data() {
    return {
      loading: true,
      order: null
    };
  },
  computed: {
    /**
     * 优先使用订单关联套餐名称；若套餐已下架或删除，则使用下单时保存的套餐快照名称，保证历史信息可读。
     */
    planName() {
      return this.order?.planName || this.order?.planSnapshot?.name || '历史套餐';
    },
    /**
     * 将订单业务类型转换为用户可理解的订阅动作，未知枚举保留原值以兼容后端后续扩展。
     */
    orderTypeName() {
      return { BUY: '首次订阅', RENEW: '续订套餐', AUTO_RENEW: '自动续费', CHANGE_PLAN: '改订套餐' }[this.order?.orderType]
        || this.order?.orderType
        || '套餐订单';
    },
    /**
     * 将支付渠道转换为门户展示文案；当前订阅订单主要使用企业余额，同时兼容历史或扩展渠道。
     */
    payTypeName() {
      return { BALANCE: '企业余额', WECHAT: '微信支付', ALIPAY: '支付宝' }[this.order?.payType]
        || this.order?.payType
        || '-';
    }
  },
  /**
   * 页面创建后按路由订单主键加载详情；请求错误由统一请求拦截器提示，页面保留空状态作为恢复入口。
   */
  async created() {
    await this.loadOrder();
  },
  methods: {
    statusName: getStatusName,
    /**
     * 查询当前企业的目标订单并保存服务端返回快照，避免直接信任列表页携带的可变数据。
     */
    async loadOrder() {
      this.loading = true;
      try {
        const response = await getSubscriptionOrder(this.$route.params.id);
        this.order = response.data;
      } catch (error) {
        this.order = null;
      } finally {
        this.loading = false;
      }
    },
    /**
     * 返回订阅订单列表的固定路由，保证直接打开详情页时也有可预测的退出路径。
     */
    backToOrders() {
      this.$router.push({ name: 'finance-orders' });
    },
    /**
     * 将订单金额统一格式化为两位小数，兼容接口中的数字和字符串类型，空值按零展示。
     */
    money(value) {
      return Number(value || 0).toFixed(2);
    },
    /**
     * 为套餐权益数字追加业务单位；缺失的历史字段展示占位符，不将零值误判为无数据。
     */
    displayValue(value, unit) {
      return value === null || value === undefined || value === '' ? '-' : `${value} ${unit}`;
    }
  }
};
</script>

<style scoped>
.loading-state,
.empty-state {
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

.order-main-panel,
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

.status-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.panel-heading h2 {
  margin: 10px 0 0;
  font-size: 24px;
}

.order-number,
.amount-note {
  margin: 6px 0 0;
  color: var(--portal-muted);
  font-size: 13px;
}

.plan-price {
  color: var(--portal-text);
  font-size: 28px;
  font-weight: 800;
  white-space: nowrap;
}

.plan-price small {
  display: block;
  color: var(--portal-muted);
  font-size: 13px;
  font-weight: 400;
  text-align: right;
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

.plan-facts span,
.detail-grid dt {
  margin-bottom: 4px;
  color: var(--portal-muted);
  font-size: 12px;
}

.detail-section {
  padding-top: 4px;
}

.detail-section h3,
.failure-card h3 {
  margin: 0 0 12px;
  font-size: 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px;
  margin: 0;
}

.detail-grid div {
  padding: 14px 0;
  border-bottom: 1px solid var(--portal-border);
}

.detail-grid dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-weight: 600;
}

.failure-card {
  padding: 16px;
  margin-top: 20px;
  border-left: 3px solid #d97706;
  background: #fff7ed;
}

.failure-card p {
  margin: 0;
  color: #9a3412;
}

.amount-panel h2 {
  margin: 0 0 16px;
  font-size: 18px;
}

.amount-panel dl {
  margin: 0;
}

.amount-panel dl div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
}

.amount-panel dt {
  color: var(--portal-muted);
}

.amount-panel dd {
  margin: 0;
  font-weight: 600;
  text-align: right;
}

.amount-panel .divider {
  padding-top: 16px;
  margin-top: 6px;
  border-top: 1px solid var(--portal-border);
}

.credit,
.refund {
  color: #15803d;
}

.total-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding-top: 18px;
  margin-top: 10px;
  border-top: 1px solid var(--portal-border);
}

.total-row strong {
  color: var(--portal-accent-strong);
  font-size: 28px;
}

@media (max-width: 980px) {
  .order-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .panel-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .plan-price small {
    display: inline;
    margin-left: 6px;
  }

  .plan-facts,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
