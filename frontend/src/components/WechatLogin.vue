<template>
  <div class="wechat-login" aria-live="polite">
    <p class="wechat-title">{{ binding ? '扫码绑定微信，增加一种登录方式' : '使用微信“扫一扫”登录' }}</p>
    <div class="wechat-stage" :aria-busy="loading || (!frameReady && !error)">
      <div v-if="!error && (loading || !frameReady)" class="wechat-placeholder">正在加载微信二维码…</div>
      <div v-show="!loading && !error" :id="containerId" class="wechat-code" :class="{ 'wechat-code--pending': !frameReady }"></div>
      <div v-if="error" class="wechat-placeholder" role="status">{{ error }}</div>
    </div>
    <button type="button" class="wechat-refresh" :disabled="loading" @click="refresh">刷新二维码</button>
    <p v-if="binding" class="wechat-help">仅支持绑定尚未关联其他账号的微信</p>
    <p v-else class="wechat-help"><span>仅限已注册且已绑定微信的账号；</span><span>新账号请前往企业门户注册。</span></p>
  </div>
</template>

<script>
import { startWechat } from '@/api/wechat';

let sdkPromise;
/**
 * 按需加载微信官方二维码组件，复用同一加载任务；失败后允许重试，不生成仿真二维码。
 */
function loadSdk() {
  if (window.WxLogin) return Promise.resolve();
  if (!sdkPromise) sdkPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://res.wx.qq.com/connect/zh_CN/htmledition/js/wxLogin.js';
    const timeout = window.setTimeout(() => { script.remove(); reject(new Error('微信组件加载超时，请重试')); }, 15000);
    script.onload = () => { window.clearTimeout(timeout); window.WxLogin ? resolve() : reject(new Error('微信组件加载失败')); };
    script.onerror = () => { window.clearTimeout(timeout); script.remove(); reject(new Error('微信组件加载失败，请重试')); };
    document.head.appendChild(script);
  }).catch(error => { sdkPromise = null; throw error; });
  return sdkPromise;
}

/**
 * 为登录或首次绑定显示微信官方 iframe；扫码状态由官方组件展示，确认后顶层跳转回调页。
 * 只在当前标签页保存短期 verifier，不保存微信凭证；组件卸载后不再写入扫码状态。
 */
export default {
  name: 'WechatLogin',
  props: { binding: { type: Boolean, default: false } },
  /** 初始化独立容器和加载状态，避免个人中心与登录组件 ID 冲突。 */
  data() { return { containerId: `wechat-${Math.random().toString(36).slice(2)}`, loading: true, frameReady: false, error: '', generation: 0, expiryTimer: null, frameTimer: null, frameCleanup: null, pendingState: '' }; },
  /** 挂载后再创建官方 iframe，保证容器已经存在。 */
  mounted() { this.refresh(); },
  /**
   * 离开登录页或关闭绑定弹窗时失效未完成任务，释放计时器和 DOM 监听。
   * 保留 sessionStorage 中的 verifier，供微信授权跳转后的回调页完成校验。
   */
  beforeUnmount() { this.generation++; window.clearTimeout(this.expiryTimer); this.clearFrameLoading(); },
  methods: {
    /**
     * 释放当前二维码的加载计时器与捕获监听，供刷新、过期、失败和卸载共用。
     * 不改变登录凭证或业务错误，保证旧 iframe 不影响后续扫码请求。
     */
    clearFrameLoading() {
      window.clearTimeout(this.frameTimer);
      if (this.frameCleanup) this.frameCleanup();
      this.frameCleanup = null;
    },
    /**
     * 生成浏览器随机凭证并向后端申请 state；只发送摘要，回调时才提交原值证明浏览器归属。
     * SDK 或配置不可用时保留邮箱入口，旧请求返回时不得覆盖新二维码。
     */
    async refresh() {
      const generation = ++this.generation;
      this.clearFrameLoading();
      this.loading = true; this.frameReady = false; this.error = ''; window.clearTimeout(this.expiryTimer);
      if (this.pendingState) sessionStorage.removeItem(`wechat:${this.pendingState}`);
      try {
        const verifier = Array.from(crypto.getRandomValues(new Uint8Array(32)), x => x.toString(16).padStart(2, '0')).join('');
        const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
        const challenge = Array.from(new Uint8Array(digest), x => x.toString(16).padStart(2, '0')).join('');
        const response = await startWechat(this.binding, challenge);
        const config = response.data.data;
        await loadSdk();
        if (generation !== this.generation) return;
        this.pendingState = config.state;
        sessionStorage.setItem(`wechat:${config.state}`, JSON.stringify({ verifier, binding: this.binding, expiresAt: Date.now() + config.expiresInSeconds * 1000 }));
        this.loading = false;
        await this.$nextTick();
        if (generation !== this.generation) return;
        const container = document.getElementById(this.containerId);
        container.replaceChildren();
        /*
         * 官方标题和公司说明位于跨域 iframe，使用官方 href 参数加载静态 CSS。
         * BASE_URL 保留 /saas/、/insurance/ 部署前缀，登录及绑定共用样式并保留官方扫码状态。
         */
        const stylesheet = new URL(`${process.env.BASE_URL}wechat-login.css?v=20261019-compact`, window.location.origin).href;
        /*
         * 跨域页面的 HTML 由微信生成，宿主页无法删除其标题节点。
         * 加载期间保持 iframe 不可见，等页面及外链样式加载完成再显示，避免默认标题闪现。
         * 先在容器捕获 load，再创建 iframe，避免初始化期间遗漏加载通知。
         * 超时只提示网络等待；只要 state 未过期，迟到的加载成功仍可恢复显示。
         */
        const onFrameLoad = event => {
          if (generation !== this.generation || event.target.tagName !== 'IFRAME') return;
          this.clearFrameLoading();
          this.error = '';
          this.frameReady = true;
        };
        container.addEventListener('load', onFrameLoad, true);
        this.frameCleanup = () => container.removeEventListener('load', onFrameLoad, true);
        this.frameTimer = window.setTimeout(() => {
          if (generation === this.generation) this.error = '微信二维码加载超时，请刷新后重试';
        }, 15000);
        new window.WxLogin({ id: this.containerId, appid: config.appId, scope: 'snsapi_login', redirect_uri: encodeURIComponent(config.redirectUri), state: config.state, self_redirect: false, style: 'black', href: stylesheet });
        if (!container.querySelector('iframe')) throw new Error('微信二维码加载失败，请重试');
        this.expiryTimer = window.setTimeout(() => {
          this.clearFrameLoading();
          this.error = '二维码已过期，请刷新后重新扫码';
          sessionStorage.removeItem(`wechat:${config.state}`);
        }, config.expiresInSeconds * 1000);
      } catch (error) {
        if (generation === this.generation) { this.clearFrameLoading(); this.error = error.code === 503 ? '微信登录暂不可用，请使用邮箱登录' : (error.message || '微信登录暂不可用，请使用邮箱登录'); this.loading = false; }
      }
    }
  }
};
</script>

<style scoped>
.wechat-stage{display:grid;min-height:260px}.wechat-stage>.wechat-code,.wechat-stage>.wechat-placeholder{grid-area:1/1;box-sizing:border-box}.wechat-code--pending{visibility:hidden}
.wechat-login{text-align:center}.wechat-title{margin:20px 0 4px;font-size:19px;color:#182330}.wechat-code{min-height:260px;display:flex;align-items:center;justify-content:center}.wechat-code :deep(iframe){width:300px;max-width:100%;height:260px!important;border:0}.wechat-placeholder{min-height:260px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;color:#64748b;padding:20px}.wechat-refresh{background:none;border:0;color:#24795b;min-height:44px;padding:8px 20px;cursor:pointer}.wechat-help{font-size:13px;color:#64748b;line-height:1.7;margin:8px auto;max-width:340px}.wechat-help span{display:block}button:focus-visible{outline:2px solid #24795b;outline-offset:3px}
</style>
