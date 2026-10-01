<template>
  <section class="recharge-detail-page">
    <div class="section-title">
      <div><h1>充值订单详情</h1><p>支付信息通过安全支付服务加密处理，平台不会接触或保存完整卡号</p></div>
      <button class="layui-btn layui-btn-primary portal-btn" type="button" @click="returnToSource">返回</button>
    </div>
    <div v-if="order" class="checkout-layout" :class="{ 'summary-only-layout': order.status !== 1 }">
      <article class="portal-card summary-panel">
        <div class="detail-heading">
          <div><span>充值订单号</span><h2>{{ order.rechargeNo }}</h2></div>
          <span class="portal-tag" :class="{ warn: [1, 7, 8].includes(order.status), gray: [3, 4, 5, 6, 9].includes(order.status) }">{{ statusName('recharge', order.status) }}</span>
        </div>
        <dl class="detail-grid">
          <div><dt>充值金额</dt><dd class="amount">¥{{ money(order.amount) }}</dd></div>
          <!-- 仅实际发生余额回撤时展示退款摘要；无退款或旧响应缺少字段时不保留空白占位。 -->
          <div v-if="Number(order.refundAmount || 0) > 0"><dt>累计退款金额</dt><dd>¥{{ money(order.refundAmount) }}</dd></div>
          <div><dt>支付平台</dt><dd>Stripe</dd></div>
          <div><dt>支付交易号</dt><dd>{{ order.stripePaymentIntentId || order.payTradeNo || '-' }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ order.createdAt || '-' }}</dd></div>
          <div><dt>支付时间</dt><dd>{{ order.paidAt || '-' }}</dd></div>
        </dl>
        <div v-if="order.status === 1" class="cancel-row"><button class="layui-btn layui-btn-primary layui-border-red portal-btn" :disabled="submitting" @click="openCancelDialog">取消订单</button></div>
        <div v-else-if="order.status === 2" class="payment-result success">
          <i class="layui-icon layui-icon-ok-circle"></i><div><strong>充值已到账</strong><span>付款结果已确认，本次金额已计入企业余额。</span></div>
        </div>
        <div v-else-if="[8, 9].includes(order.status)" class="payment-result muted">
          <div><strong>{{ statusName('recharge', order.status) }}</strong><span>累计退款 ¥{{ money(order.refundAmount) }}，对应金额已从企业余额回撤。原充值金额 ¥{{ money(order.amount) }}。</span></div>
        </div>
        <div v-else-if="order.status === 7" class="payment-result processing" aria-live="polite">
          <span class="checkout-spinner" aria-hidden="true"></span><div><strong>支付处理中</strong><span>Checkout 已完成，银行正在确认资金。你可以安全离开此页面，到账后订单会自动更新。</span></div>
        </div>
        <div v-else class="payment-result muted"><div><strong>{{ statusName('recharge', order.status) }}</strong><span>{{ order.paymentFailureReason || '如需充值，请重新创建一笔订单。' }}</span></div></div>
        <div v-if="order.status === 2 && hasSubscriptionContext" class="subscription-resume">
          <div><strong>确认套餐{{ subscriptionAction }}</strong><span>充值已到账，确认后将使用企业余额完成套餐操作；离开后也可在订阅服务页面重新选择套餐。</span></div>
          <button class="layui-btn portal-btn portal-btn-primary" :disabled="submitting" @click="completePendingSubscription">{{ submitting ? '正在提交' : `确认${subscriptionAction}` }}</button>
        </div>
      </article>
      <article v-if="order.status === 1" class="portal-card stripe-panel" aria-live="polite">
        <div class="stripe-heading">
          <div><span class="step-label">安全支付</span><h2>完成付款</h2><p>选择支付方式并确认本次充值</p></div>
          <span class="stripe-badge">安全加密支付</span>
        </div>
        <div class="stripe-content-scroll">
          <div v-if="checkoutState === 'loading' || checkoutState === 'confirming'" class="checkout-status">
            <span class="checkout-spinner" aria-hidden="true"></span><strong>{{ checkoutState === 'confirming' ? '正在确认支付结果' : '正在加载安全结账页面' }}</strong>
            <p>{{ checkoutState === 'confirming' ? '请勿重复支付，到账通常只需几秒。' : '支付方式将根据您的地区和设备显示。' }}</p>
          </div>
          <div v-if="checkoutError" class="checkout-error" role="alert"><strong>结账页面暂时无法加载</strong><span>{{ checkoutError }}</span><button class="layui-btn layui-btn-primary portal-btn" type="button" @click="mountCheckout">重新加载</button></div>
          <div v-show="checkoutState === 'ready'" ref="stripeCheckout" class="stripe-checkout"></div>
        </div>
      </article>
    </div>
    <ConfirmDialog :visible="cancelDialog" title="取消充值订单" message="确定取消当前充值订单吗？取消后不能继续支付，需要重新创建订单。" confirm-text="确认取消" :loading="submitting" @cancel="cancelDialog = false" @confirm="cancelOrder" />
    <ConfirmDialog :visible="subscriptionResult.visible" :title="subscriptionResult.title" :message="subscriptionResult.message" :show-cancel="false" confirm-type="primary" confirm-text="知道了" @confirm="closeSubscriptionResult" @cancel="closeSubscriptionResult" />
  </section>
</template>

<script>
import { loadStripe } from '@stripe/stripe-js';
import { cancelRechargeOrder, createStripeRechargeCheckoutSession, createSubscriptionOrder, getRechargeOrder } from '@/api/portal';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import { getStatusName } from '@/utils/portalLabels';
import { rechargeContextKey, normalizeRechargeContext, saveRechargeContext, readRechargeContext, consumeRechargeContext } from '@/utils/rechargeSubscriptionContext';
import { normalizeRechargeReturnPath } from '@/utils/rechargeReturnPath';

export default {
  name: 'RechargeOrderDetailPage',
  components: { ConfirmDialog },
  /**
   * 按订单保存业务入口路径，跳过订单创建和试算中间页面，保留入口查询条件。
   * 刷新及 Stripe 回跳时复用当前标签页的记录；首次直接访问且没有记录时返回充值订单列表。
   * 仅记录已匹配的门户页面，避免返回支付外站、登录页或详情页自身。
   */
  beforeRouteEnter(to, from, next) {
    const key = `recharge-return:${to.params.id}`;
    let source = '/portal/finance/recharges';
    try {
      if (from.matched.length && from.path.startsWith('/portal/') && from.name !== 'finance-recharge-detail') {
        source = from.name === 'finance-recharge' ? to.query.returnTo || from.fullPath : from.fullPath;
        window.sessionStorage.setItem(key, source);
      } else {
        source = window.sessionStorage.getItem(key) || source;
      }
    } catch (error) {
      // 存储受限时仍可在本次页面访问中返回已捕获的来源。
    }
    next(vm => {
      vm.returnPath = normalizeRechargeReturnPath(source, vm.$router);
      try { window.sessionStorage.setItem(key, vm.returnPath); }
      catch (error) { /* 存储不可用时，本次访问仍使用内存中的业务入口。 */ }
    });
  },
  /**
   * 维护本地充值订单、Stripe Embedded Checkout 生命周期及回调确认状态。
   * Stripe 组件只存在于当前页面，离开、取消或到账后均销毁，避免重复挂载和事件泄漏。
   */
  data() {
    return {
      order: null, submitting: false, cancelDialog: false,
      returnPath: '/portal/finance/recharges',
      pendingSubscription: null,
      returnedFromPayment: false,
      subscriptionContextKey: '',
      subscriptionResult: { visible: false, title: '', message: '', destination: null },
      checkoutState: 'loading', checkoutError: '', confirmError: '',
      stripeCheckout: null, stripeCheckoutSdk: null, stripePaymentElement: null,
      checkoutActions: null, checkoutCanConfirm: false, checkoutEmail: '',
      isElementsCheckout: false, pollTimer: null, pollAttempts: 0
    };
  },
  computed: {
    /** 仅本次付款回跳或组件完成回调可展示确认入口，历史订单访问不会恢复该入口。 */
    hasSubscriptionContext() { return this.returnedFromPayment && Boolean(this.pendingSubscription); },
    /** 根据原订单试算类型展示继续订阅、续订或改订，历史数据使用订阅作为通用文案。 */
    subscriptionAction() { return { CHANGE_PLAN: '改订', RENEW: '续订', BUY: '订阅' }[this.pendingSubscription?.orderType] || '订阅'; }
  },
  /** 先读取订单真实状态；从 Stripe 返回时等待 Webhook 入账，否则加载嵌入式结账页。 */
  async mounted() {
    const key = rechargeContextKey(this.$route.params.id, this.$store.state);
    this.subscriptionContextKey = key;
    this.returnedFromPayment = this.$route.query.payment_return === '1';
    /*
     * 外部付款回跳只消费一次已保存的选择，不从旧 URL 恢复购买意图。
     * 同时移除回跳和套餐参数，避免刷新或收藏该链接再次显示确认按钮。
     */
    if (this.returnedFromPayment) {
      this.pendingSubscription = consumeRechargeContext(key);
      const query = { ...this.$route.query };
      ['payment_return', 'planId', 'periodCount', 'autoRenew', 'orderType'].forEach(field => { delete query[field]; });
      await this.$router.replace({ path: this.$route.path, query });
    }
    await this.loadOrder();
    if (!this.order) return;
    /*
     * 只有仍待支付的订单保存回跳所需上下文；已经到账或处理中订单的普通访问
     * 清除历史选择，用户需要从订阅服务页面重新发起套餐操作。
     */
    if (!this.returnedFromPayment && this.order.status === 1) {
      const savedContext = readRechargeContext(key);
      this.pendingSubscription = savedContext?.completed ? null : savedContext || normalizeRechargeContext(this.$route.query);
    } else if (!this.returnedFromPayment) {
      consumeRechargeContext(key);
    }
    if (!this.returnedFromPayment && this.pendingSubscription) {
      try { saveRechargeContext(key, this.pendingSubscription); }
      catch (error) { this.checkoutState = 'error'; this.checkoutError = '无法保存套餐选择，请允许浏览器使用会话存储后重新加载。'; return; }
    }
    if (this.order.status === 7) { this.pollPaymentResult(); return; }
    if (this.order.status !== 1) return;
    if (this.returnedFromPayment) this.pollPaymentResult();
    else await this.mountCheckout();
  },
  /** 销毁 Stripe 组件与轮询计时器，防止路由切换后继续更新页面。 */
  beforeUnmount() {
    if (this.subscriptionContextKey) consumeRechargeContext(this.subscriptionContextKey);
    this.destroyCheckout();
    if (this.pollTimer) window.clearTimeout(this.pollTimer);
  },
  methods: {
    /**
     * 返回充值流程的业务入口，兼容旧来源记录，防止返回创建页或支付详情循环。
     * 使用 replace 避免返回后浏览器后退再次进入当前支付详情。
     */
    returnToSource() {
      this.$router.replace(normalizeRechargeReturnPath(this.returnPath, this.$router));
    },
    statusName: getStatusName,
    /** 将订单金额统一显示为两位小数。 */
    money(value) { return Number(value || 0).toFixed(2); },
    /** 读取当前企业范围内的订单；租户隔离和归属校验由服务端执行。 */
    async loadOrder() { const response = await getRechargeOrder(this.$route.params.id); this.order = response.data; },
    /**
     * 获取服务端创建的 Checkout Session。新订单使用 Stripe Full embedded page 完整结账页，
     * 邮箱、支付方式和支付按钮均由 Stripe 渲染；历史 elements Session 提示取消后重新下单，
     * 避免替换正在支付的会话导致重复支付或订单关联失效。
     * 发布密钥可公开，client secret 仅保存在内存中；支付方式列表仍完全由 Stripe 返回。
     */
    async mountCheckout() {
      if (!this.order || this.order.status !== 1) return;
      this.destroyCheckout(); this.checkoutState = 'loading'; this.checkoutError = ''; this.confirmError = '';
      try {
        const response = await createStripeRechargeCheckoutSession(this.order.id);
        const stripe = await loadStripe(response.data.publishableKey);
        if (!stripe) throw new Error('Stripe.js 初始化失败');
        /*
         * 历史 Elements 会话不能挂载完整嵌入页。先取消旧订单使会话过期再重新下单，
         * 防止替换会话时保留另一条可支付链路，导致同一业务订单出现重复付款。
         */
        if (response.data.uiMode === 'elements') {
          throw new Error('此订单使用旧版支付会话，请先取消当前订单，再创建新的充值订单。若新订单仍出现此提示，请更新并重启 SaaS 后端服务。');
        }
        this.stripeCheckout = await stripe.createEmbeddedCheckoutPage({
          fetchClientSecret: () => Promise.resolve(response.data.clientSecret),
          onComplete: () => this.handleCheckoutComplete()
        });
        this.stripeCheckout.mount(this.$refs.stripeCheckout);
        this.checkoutState = 'ready';
      } catch (error) {
        this.checkoutState = 'error'; this.checkoutError = error?.message || '请稍后重试或联系管理员。';
      }
    },
    /**
     * 无整页跳转的支付完成回调同样激活本次确认入口，并消费缓存。
     * 是否到账仍由轮询服务端决定，不能凭客户端回调立即发起套餐扣款。
     */
    handleCheckoutComplete() {
      this.returnedFromPayment = true;
      consumeRechargeContext(this.subscriptionContextKey);
      return this.pollPaymentResult();
    },
    /**
     * 将 Stripe Payment Element 放入门户自己的桌面表单。外层信息层级、邮箱和提交按钮由门户
     * 控制，银行卡及其他支付方式字段仍由 Stripe iframe 渲染，PCI 安全边界不发生变化。
     */
    async mountElementsCheckout(stripe, clientSecret) {
      this.stripeCheckoutSdk = stripe.initCheckoutElementsSdk({
        clientSecret,
        elementsOptions: {
          appearance: {
            theme: 'stripe',
            variables: { colorPrimary: '#1677ff', borderRadius: '8px', spacingUnit: '4px', fontSizeBase: '16px' }
          }
        }
      });
      const result = await this.stripeCheckoutSdk.loadActions();
      if (result.type === 'error') throw new Error(result.error.message || 'Stripe 结账信息加载失败');
      this.checkoutActions = result.actions;
      const session = result.actions.getSession();
      this.checkoutEmail = session.email || '';
      this.checkoutCanConfirm = session.canConfirm;
      this.stripeCheckoutSdk.on('change', updated => { this.checkoutCanConfirm = updated.canConfirm; });
      this.stripePaymentElement = this.stripeCheckoutSdk.createPaymentElement({
        layout: { type: 'accordion', defaultCollapsed: false, radios: 'always', spacedAccordionItems: false },
        fields: { billingDetails: { email: 'never' } }
      });
      this.stripePaymentElement.mount(this.$refs.stripePaymentElement);
    },
    /**
     * 使用 Checkout Session actions 提交 Elements 表单。同步付款成功后立即轮询本地订单；
     * 需要外部认证的支付方式由 Stripe 按 Session return_url 返回本页继续确认。
     */
    async confirmElementsPayment() {
      if (!this.checkoutActions || this.submitting) return;
      this.submitting = true; this.confirmError = '';
      try {
        const result = await this.checkoutActions.confirm({ email: this.checkoutEmail });
        if (result.type === 'error') {
          this.confirmError = result.error.message || '支付资料未通过验证，请检查后重试。';
          return;
        }
        await this.pollPaymentResult();
      } catch (error) {
        this.confirmError = error?.message || '支付提交失败，请稍后重试。';
      } finally { this.submitting = false; }
    },
    /**
     * Stripe 完成页面只代表客户端流程结束，余额必须等待已验签 Webhook 入账。
     * 页面短间隔查询订单；超时不判定失败，以免用户重复付款。
     */
    async pollPaymentResult() {
      this.destroyCheckout(); this.checkoutState = 'confirming'; this.checkoutError = ''; this.pollAttempts += 1;
      await this.loadOrder();
      if ([2, 3, 4, 5, 6, 8, 9].includes(this.order.status)) { this.checkoutState = 'complete'; return; }
      if (this.pollAttempts >= 120) { this.checkoutState = 'complete'; return; }
      this.pollTimer = window.setTimeout(this.pollPaymentResult, this.order.status === 7 ? 5000 : 1500);
    },
    /** 销毁当前 Stripe 嵌入实例，允许安全地重试或离开页面。 */
    destroyCheckout() {
      if (this.stripeCheckout) { this.stripeCheckout.destroy(); this.stripeCheckout = null; }
      if (this.stripePaymentElement) { this.stripePaymentElement.destroy(); this.stripePaymentElement = null; }
      this.stripeCheckoutSdk = null; this.checkoutActions = null; this.checkoutCanConfirm = false;
    },
    /** 根据保留的套餐参数，使用到账后的企业余额继续创建原套餐订单。 */
    async completePendingSubscription() {
      if (this.submitting || this.subscriptionResult.visible) return;
      if (!this.hasSubscriptionContext || this.order?.status !== 2) return;
      this.submitting = true;
      try {
        const { planId, periodCount, autoRenew } = this.pendingSubscription;
        const response = await createSubscriptionOrder({ planId, periodCount, autoRenew });
        /* 套餐请求成功后清除本次选择，刷新或再次访问不能重新激活购买入口。 */
        this.pendingSubscription = null;
        consumeRechargeContext(this.subscriptionContextKey);
        const action = { BUY: '订阅', RENEW: '续订', CHANGE_PLAN: '改订' }[response.data.orderType] || '套餐订阅';
        this.subscriptionResult = { visible: true, title: `${action}成功`, message: `套餐已生效，订单号：${response.data.orderNo}。`, destination: { name: 'finance-subscription', query: { orderNo: response.data.orderNo } } };
      } catch (error) {
        this.subscriptionResult = { visible: true, title: '套餐操作失败', message: error.message || '提交失败，请稍后重试。', destination: null };
      } finally { this.submitting = false; }
    },
    /**
     * 充值后继续套餐购买的结果需要先由用户确认，成功后返回套餐页，失败则保留当前订单供重试。
     */
    async closeSubscriptionResult() {
      const destination = this.subscriptionResult.destination;
      this.subscriptionResult.visible = false;
      if (destination) await this.$router.push(destination);
    },
    /** 打开取消订单的二次确认框，防止误操作中断当前支付。 */
    openCancelDialog() { this.cancelDialog = true; },
    /** 取消仍待支付的本地订单，并销毁已挂载的 Stripe 结账组件。 */
    async cancelOrder() {
      this.submitting = true;
      try { const response = await cancelRechargeOrder(this.order.id); this.order = response.data; this.cancelDialog = false; this.destroyCheckout(); }
      finally { this.submitting = false; }
    }
  }
};
</script>

<style scoped>
.recharge-detail-page { display: flex; height: 100%; min-height: 0; flex-direction: column; overflow: hidden; }
.recharge-detail-page > .section-title { flex: 0 0 auto; }
.checkout-layout { display: grid; flex: 1 1 auto; width: 100%; min-height: 0; grid-template-columns: minmax(280px, 320px) minmax(0, 720px); gap: 24px; max-width: 1064px; margin: 0 auto; align-items: stretch; }
.summary-panel, .stripe-panel { padding: 28px 30px; }
.summary-panel { box-sizing: border-box; height: 100%; overflow-y: auto; }
.stripe-panel { display: flex; box-sizing: border-box; height: 100%; min-height: 0; flex-direction: column; overflow: hidden; }
.stripe-heading { flex: 0 0 auto; }
.stripe-content-scroll { flex: 1 1 auto; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding-right: 8px; scrollbar-gutter: stable; }
.checkout-layout.summary-only-layout { display: block; flex: 0 0 auto; min-height: auto; max-width: 1080px; }
.summary-only-layout .summary-panel { width: 100%; height: auto; overflow: visible; }
.summary-only-layout .detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 40px; }
.detail-heading, .stripe-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding-bottom: 20px; border-bottom: 1px solid var(--portal-border); }
.detail-heading span, .detail-grid dt, .step-label, .checkout-status p, .checkout-error span, .payment-result span, .subscription-resume span, .stripe-heading p { color: var(--portal-muted); }
.detail-heading h2, .stripe-heading h2 { margin: 5px 0 0; font-size: 20px; overflow-wrap: anywhere; }
.stripe-heading p { margin: 6px 0 0; }
.detail-grid { margin: 0; }
.detail-grid div { padding: 16px 0; border-bottom: 1px solid var(--portal-border); }
.detail-grid dd { margin: 6px 0 0; overflow-wrap: anywhere; font-weight: 600; }
.detail-grid .amount { color: var(--portal-accent-strong); font-size: 24px; }
.stripe-badge { padding: 6px 10px; border-radius: 999px; color: #635bff; background: #f2f1ff; font-size: 12px; font-weight: 700; white-space: nowrap; }
.stripe-checkout { min-height: 420px; padding-top: 18px; }
.desktop-checkout-form { display: grid; gap: 18px; padding-top: 22px; }
.checkout-form-section { padding: 20px; border: 1px solid var(--portal-border); border-radius: 10px; background: #fff; }
.form-section-heading { display: flex; align-items: center; gap: 12px; margin-bottom: 18px; }
.form-section-heading > span { display: grid; flex: 0 0 28px; width: 28px; height: 28px; place-items: center; border-radius: 50%; background: #edf5ff; color: #1677ff; font-weight: 700; }
.form-section-heading div { display: grid; gap: 3px; }
.form-section-heading strong { font-size: 16px; }
.form-section-heading small { color: var(--portal-muted); font-size: 13px; }
.checkout-email-label { display: block; margin-bottom: 8px; color: #303846; font-weight: 600; }
.checkout-email { box-sizing: border-box; width: 100%; min-height: 48px; padding: 0 14px; border: 1px solid #c8d0dc; border-radius: 8px; background: #fff; color: #172033; font-size: 16px; outline: none; transition: border-color .2s, box-shadow .2s; }
.checkout-email:focus { border-color: #1677ff; box-shadow: 0 0 0 3px rgba(22, 119, 255, .14); }
.stripe-payment-element { min-height: 180px; }
.checkout-submit { display: flex; min-height: 52px; align-items: center; justify-content: space-between; padding: 0 20px; border: 0; border-radius: 8px; background: #1677ff; color: #fff; cursor: pointer; font-size: 17px; font-weight: 700; box-shadow: 0 6px 14px rgba(22, 119, 255, .2); transition: background-color .2s, box-shadow .2s; }
.checkout-submit:hover:not(:disabled) { background: #0f68df; box-shadow: 0 8px 18px rgba(22, 119, 255, .26); }
.checkout-submit:focus-visible { outline: 3px solid rgba(22, 119, 255, .25); outline-offset: 2px; }
.checkout-submit:disabled { cursor: not-allowed; opacity: .5; box-shadow: none; }
.confirm-error { padding: 12px 14px; border: 1px solid #f1b9b9; border-radius: 8px; background: #fff6f6; color: #a52020; }
.secure-note { display: flex; align-items: center; justify-content: center; gap: 7px; color: var(--portal-muted); font-size: 13px; }
.checkout-status { display: grid; justify-items: center; padding: 70px 20px; text-align: center; }
.checkout-status strong { margin-top: 16px; }
.checkout-status p { margin: 8px 0 0; }
.checkout-spinner { width: 32px; height: 32px; border: 3px solid #e7e9ef; border-top-color: #635bff; border-radius: 50%; animation: spin .8s linear infinite; }
.checkout-error { display: grid; gap: 10px; justify-items: start; padding: 20px; margin-top: 20px; border: 1px solid #f3c7c7; border-radius: 6px; background: #fff8f8; }
.checkout-error .layui-btn { margin: 4px 0 0; }
.cancel-row { display: flex; justify-content: flex-end; margin-top: 22px; }
.payment-result, .subscription-resume { display: flex; align-items: center; gap: 12px; padding: 15px; margin-top: 22px; border-left: 3px solid var(--portal-accent); background: #eefbf3; }
.payment-result.muted { border-color: var(--portal-border); background: #f7f8fa; }
.payment-result.processing { border-color: #d69e2e; background: #fffaf0; }
.payment-result.processing .checkout-spinner { flex: 0 0 auto; width: 24px; height: 24px; border-top-color: #b7791f; }
.payment-result i { color: var(--portal-accent-strong); font-size: 26px; }
.payment-result div, .subscription-resume div { display: grid; gap: 4px; }
.subscription-resume { justify-content: space-between; }
.subscription-resume .layui-btn { margin: 0; white-space: nowrap; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 1080px) { .recharge-detail-page { height: auto; min-height: 100%; overflow: visible; } .checkout-layout { flex: none; grid-template-columns: 1fr; } .summary-panel, .stripe-panel { height: auto; } .stripe-content-scroll { overflow: visible; padding-right: 0; } }
@media (max-width: 680px) { .summary-only-layout .detail-grid { grid-template-columns: 1fr; } }
@media (max-width: 560px) { .summary-panel, .stripe-panel { padding: 18px; } .detail-heading, .stripe-heading, .subscription-resume { align-items: stretch; flex-direction: column; } }
</style>
