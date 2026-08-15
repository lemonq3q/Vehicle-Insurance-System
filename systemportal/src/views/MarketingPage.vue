<template>
  <main class="marketing-page">
    <header class="marketing-nav" :class="{ scrolled: navScrolled }">
      <router-link class="brand" to="/" aria-label="iDatag 官网首页">
        <img class="brand-logo" src="@/assets/brand/idatag-logo.png" alt="" />
        <span class="brand-name">iDatag</span>
        <span class="brand-divider"></span>
        <span class="brand-product">车险业务云平台</span>
      </router-link>
      <nav aria-label="官网导航">
        <a href="#capabilities" @click.prevent="scrollToSection('capabilities')">产品能力</a>
        <a href="#teams" @click.prevent="scrollToSection('teams')">适用团队</a>
        <a href="#plans" @click.prevent="scrollToSection('plans')">套餐价格</a>
        <router-link class="nav-login" to="/login">登录</router-link>
        <router-link class="layui-btn portal-btn nav-cta" to="/login">立即使用</router-link>
      </nav>
    </header>

    <section class="hero" aria-labelledby="hero-title">
      <div class="hero-grid-lines" aria-hidden="true"></div>
      <div class="hero-content">
        <p class="eyebrow"><span></span> 专为车险团队打造的数字工作台</p>
        <h1 id="hero-title">让每一次投保、协作与经营，<strong>更快一步</strong></h1>
        <p class="hero-desc">
          从影像建档、业务流转到续保经营与财务追溯，iDatag 把分散的工作收进一个清晰、可靠的企业空间，让团队把时间重新交还给客户服务。
        </p>
        <div class="hero-actions">
          <router-link class="layui-btn portal-btn portal-btn-primary hero-primary" to="/login">进入企业门户</router-link>
          <a class="text-link" href="#capabilities" @click.prevent="scrollToSection('capabilities')">了解产品能力 <span aria-hidden="true">→</span></a>
        </div>
        <dl class="hero-facts" aria-label="产品特点">
          <div><dt>统一</dt><dd>账号、角色与企业数据</dd></div>
          <div><dt>清晰</dt><dd>订单、余额与资金记录</dd></div>
          <div><dt>可靠</dt><dd>租户隔离与权限控制</dd></div>
        </dl>
      </div>

      <div class="product-scene" aria-label="iDatag 工作台界面示意">
        <div class="scene-window">
          <div class="scene-sidebar">
            <div class="scene-logo"><img src="@/assets/brand/idatag-logo.png" alt="" /></div>
            <i v-for="index in 6" :key="index" :class="{ active: index === 2 }"></i>
          </div>
          <div class="scene-main">
            <div class="scene-header">
              <span>企业经营概览</span>
              <small>杭州小马车险服务有限公司</small>
            </div>
            <div class="scene-stats">
              <div><small>本月业务</small><strong>286</strong><span>较上月 +18.6%</span></div>
              <div><small>待续保客户</small><strong>32</strong><span>未来 30 天</span></div>
              <div><small>团队成员</small><strong>18</strong><span>协作正常</span></div>
            </div>
            <div class="scene-chart">
              <div class="chart-title"><span>业务趋势</span><small>近 7 日</small></div>
              <div class="bars">
                <i v-for="height in chartBars" :key="height" :style="{ height: `${height}%` }"></i>
              </div>
            </div>
            <div class="scene-tasks">
              <span>今日待办</span>
              <p><i></i> 续保客户跟进 <b>12</b></p>
              <p><i></i> 待完善业务资料 <b>5</b></p>
            </div>
          </div>
        </div>
        <aside class="scene-note renewal-note">
          <small>续保提醒</small><strong>保单到期前 30 天</strong><span>自动进入跟进队列</span>
        </aside>
        <aside class="scene-note entry-note">
          <small>智能录入</small><strong>证件影像快速建档</strong><span>减少重复手工填写</span>
        </aside>
      </div>
    </section>

    <section class="trust-strip" aria-label="平台服务范围" data-reveal>
      <p>服务车险业务全流程</p>
      <span>投保资料归档</span><span>企业成员协作</span><span>续保经营提醒</span><span>财务对账追溯</span><span>多角色权限管理</span>
    </section>

    <section id="capabilities" class="section workflow-section">
      <div class="section-heading" data-reveal>
        <p class="section-kicker">WORKFLOW</p>
        <h2>从一张证件开始，贯通车险业务全流程</h2>
        <p>不增加团队的学习负担，用一套连贯的工作方式替代散落的表格、聊天记录和纸质单据。</p>
      </div>
      <div class="workflow-grid">
        <article v-for="(item, index) in workflows" :key="item.title" class="workflow-item" data-reveal>
          <span class="step-number">0{{ index + 1 }}</span>
          <div class="workflow-visual" :class="`visual-${index + 1}`" aria-hidden="true">
            <div v-if="index === 0" class="document-visual"><i></i><i></i><i></i><b>OCR</b></div>
            <div v-else-if="index === 1" class="flow-visual"><i></i><span></span><i></i><span></span><i></i></div>
            <div v-else-if="index === 2" class="renewal-visual"><b>30</b><span>天内到期</span><i></i></div>
            <div v-else class="finance-visual"><i></i><i></i><i></i><b>¥</b></div>
          </div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.text }}</p>
          <ul><li v-for="point in item.points" :key="point">{{ point }}</li></ul>
        </article>
      </div>
    </section>

    <section class="operations-section">
      <div class="operations-copy" data-reveal="left">
        <p class="section-kicker">ONE WORKSPACE</p>
        <h2>一个企业空间，承接从团队协作到经营复盘</h2>
        <p>企业拥有者、管理员与出单员各司其职。成员变动、套餐订阅、充值订单与资金流水均有迹可循，业务规模扩大后依然井然有序。</p>
        <div class="operation-list">
          <div v-for="item in operationItems" :key="item.title">
            <span>{{ item.no }}</span><p><strong>{{ item.title }}</strong><small>{{ item.text }}</small></p>
          </div>
        </div>
      </div>
      <div class="ledger-panel" aria-label="企业经营数据示意" data-reveal="right">
        <header><div><small>经营对账</small><strong>本月资金概览</strong></div><span>2026 年 7 月</span></header>
        <div class="ledger-total"><small>本月业务净额</small><strong>¥ 128,640.00</strong><span>数据更新于今日 11:20</span></div>
        <div class="ledger-lines">
          <p><span>业务收入</span><b>¥ 168,420.00</b></p>
          <p><span>渠道结算</span><b>- ¥ 32,680.00</b></p>
          <p><span>平台服务</span><b>- ¥ 7,100.00</b></p>
        </div>
        <footer><span>每笔业务均可回溯</span><b>查看资金明细 →</b></footer>
      </div>
    </section>

    <section id="teams" class="section teams-section">
      <div class="section-heading compact" data-reveal>
        <p class="section-kicker">BUILT FOR INSURANCE TEAMS</p>
        <h2>匹配不同阶段的车险经营团队</h2>
      </div>
      <div class="teams-grid">
        <article v-for="team in teams" :key="team.title" data-reveal>
          <p class="team-label">{{ team.label }}</p>
          <h3>{{ team.title }}</h3>
          <p>{{ team.text }}</p>
          <ul><li v-for="point in team.points" :key="point">{{ point }}</li></ul>
        </article>
      </div>
    </section>

    <section class="security-section">
      <div data-reveal="left">
        <p class="section-kicker">SECURITY BY DESIGN</p>
        <h2><span>客户资料是团队最重要的资产，</span><span>安全从系统底层开始</span></h2>
      </div>
      <div class="security-points" data-reveal="right">
        <p><strong>租户数据隔离</strong><span>企业数据按租户边界隔离，避免跨企业访问。</span></p>
        <p><strong>角色权限控制</strong><span>关键操作依照拥有者、管理员和出单员身份授权。</span></p>
        <p><strong>关键记录留痕</strong><span>成员变动、订单与资金流水保留完整业务记录。</span></p>
      </div>
    </section>

    <section id="plans" class="section plans-section">
      <div class="section-heading plans-heading" data-reveal>
        <div><p class="section-kicker">PRICING</p><h2>按团队规模选择合适的版本</h2></div>
        <p>从小团队起步到多成员协作，随业务成长平滑升级。具体权益与价格以当前上架套餐为准。</p>
      </div>
      <div v-if="plansLoading" class="plan-grid" aria-label="套餐加载中">
        <article v-for="index in 3" :key="index" class="plan-card plan-skeleton" data-reveal></article>
      </div>
      <div v-else class="plan-grid">
        <article v-for="(plan, index) in displayPlans" :key="plan.id || plan.name" class="plan-card" :class="{ featured: index === 1 }" data-reveal>
          <span v-if="index === 1" class="recommended">推荐选择</span>
          <p class="plan-code">{{ planLabel(index) }}</p>
          <h3>{{ plan.name }}</h3>
          <p class="plan-description">{{ plan.description || '为车险服务团队提供稳定、清晰的数字化经营空间。' }}</p>
          <div class="price"><small>¥</small>{{ money(plan.price) }}<span>/ {{ periodLabel(plan.billingPeriod) }}</span></div>
          <ul>
            <li>最多 {{ plan.userLimit || 0 }} 名企业成员</li>
            <li>包含 {{ plan.workorderLimit || 0 }} 个工单额度</li>
            <li>{{ plan.durationDays || 0 }} 天服务周期</li>
            <li>企业协作与财务记录</li>
          </ul>
          <router-link class="layui-btn portal-btn" :class="index === 1 ? 'portal-btn-primary' : 'plan-button'" to="/login">选择此套餐</router-link>
        </article>
      </div>
      <p v-if="plansError" class="plans-note">套餐服务暂时不可用，当前展示为基础版本信息。</p>
    </section>

    <section class="final-cta" data-reveal>
      <p>准备好让团队工作更轻松了吗？</p>
      <h2>把繁杂交给系统，把时间留给客户</h2>
    </section>

    <footer class="marketing-footer">
      <nav><router-link to="/privacy">用户隐私声明</router-link><router-link to="/terms">服务使用协议</router-link><router-link to="/about">关于我们</router-link></nav>
      <p>香港科学园（HKSTP）Ideation 计划入选企业。</p>
      <small>© 2026 iDatag. 保留所有权利。</small>
    </footer>
  </main>
</template>

<script>
import { markRaw } from 'vue';
import { getMarketingPlans } from '@/api/portal';

const FALLBACK_PLANS = [
  { id: 'starter', name: '轻量版', description: '适合小团队快速建立规范的车险业务工作方式。', price: 299, billingPeriod: 'MONTH', durationDays: 30, userLimit: 5, workorderLimit: 1000 },
  { id: 'professional', name: '专业版', description: '适合稳定经营团队，覆盖日常协作与经营管理。', price: 2999, billingPeriod: 'YEAR', durationDays: 365, userLimit: 30, workorderLimit: 5000 },
  { id: 'enterprise', name: '企业版', description: '适合多成员、多岗位协作的规模化车险服务机构。', price: 8999, billingPeriod: 'YEAR', durationDays: 365, userLimit: 100, workorderLimit: 10000 }
];

export default {
  name: 'MarketingPage',
  data() {
    return {
      navScrolled: false,
      plans: [],
      plansLoading: true,
      plansError: false,
      revealObserver: null,
      chartBars: [38, 54, 47, 68, 61, 82, 74, 92, 78, 88],
      workflows: [
        { title: '资料快速建档', text: '减少重复录入，让证件与业务资料从一开始就规范归档。', points: ['证件影像集中管理', '业务字段统一沉淀'] },
        { title: '业务有序流转', text: '报价、支付、出单等阶段清晰衔接，团队随时掌握当前进度。', points: ['关键节点状态明确', '多人协作责任清晰'] },
        { title: '续保提前经营', text: '把即将到期的客户提前放进跟进视野，不再依赖个人记忆。', points: ['到期客户集中查看', '提前安排续保动作'] },
        { title: '财务完整追溯', text: '订单、余额与资金变动互相对应，让每一笔记录都有来处。', points: ['充值订阅记录完整', '资金明细随时核对'] }
      ],
      operationItems: [
        { no: '01', title: '企业与成员', text: '邀请加入、角色分工、状态管理' },
        { no: '02', title: '订阅与额度', text: '套餐周期、成员席位、自动续费' },
        { no: '03', title: '订单与资金', text: '充值订单、订阅订单、资金流水' }
      ],
      teams: [
        { label: 'OPC / 独立团队', title: '一个人，也能像一家公司高效运转', text: '告别白天跑业务、晚上补表格，把机械整理交给系统。', points: ['核心客户资料集中归档', '续保客户提前进入视野', '轻量投入即可开始'] },
        { label: '中小代理机构', title: '让团队协作不再依赖口头交接', text: '成员、角色和业务状态保持一致，管理者更容易掌握全局。', points: ['多角色职责清晰', '成员变动全程留痕', '经营数据统一查看'] },
        { label: '汽车经销商 / 服务机构', title: '为持续增长预留规范的经营底座', text: '从单点业务到多人协作，保持数据、流程和财务记录稳定延续。', points: ['按团队规模灵活扩容', '企业级数据边界', '业务与财务完整追溯'] }
      ]
    };
  },
  computed: {
    displayPlans() {
      return this.plans.length ? this.plans.slice(0, 3) : FALLBACK_PLANS;
    }
  },
  mounted() {
    window.addEventListener('scroll', this.handleScroll, { passive: true });
    this.setupRevealAnimations();
    this.loadPlans();
  },
  beforeUnmount() {
    window.removeEventListener('scroll', this.handleScroll);
    this.revealObserver?.disconnect();
  },
  methods: {
    handleScroll() {
      this.navScrolled = window.scrollY > 20;
    },
    scrollToSection(id) {
      const section = document.getElementById(id);
      if (!section) return;
      const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      section.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' });
      window.history.replaceState(null, '', `#${id}`);
    },
    setupRevealAnimations() {
      this.revealObserver?.disconnect();
      const elements = this.$el.querySelectorAll('[data-reveal]:not(.is-visible)');
      const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      if (!('IntersectionObserver' in window) || reduceMotion) {
        elements.forEach((element) => element.classList.add('is-visible'));
        return;
      }

      this.revealObserver = markRaw(new IntersectionObserver((entries, observer) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        });
      }, { threshold: 0.14, rootMargin: '0px 0px -8% 0px' }));

      elements.forEach((element, index) => {
        element.style.setProperty('--reveal-delay', `${(index % 4) * 70}ms`);
        this.revealObserver.observe(element);
      });
    },
    async loadPlans() {
      try {
        const response = await getMarketingPlans();
        this.plans = Array.isArray(response?.data) ? response.data : [];
      } catch (error) {
        this.plansError = true;
      } finally {
        this.plansLoading = false;
        this.$nextTick(this.setupRevealAnimations);
      }
    },
    money(value) {
      const amount = Number(value || 0);
      return Number.isInteger(amount) ? amount.toLocaleString('zh-CN') : amount.toFixed(2);
    },
    periodLabel(period) {
      return { MONTH: '月', YEAR: '年', DAY: '周期' }[period] || '周期';
    },
    planLabel(index) {
      return ['STARTER', 'PROFESSIONAL', 'ENTERPRISE'][index] || 'PLAN';
    }
  }
};
</script>

<style scoped>
.marketing-page { --ink: #10251f; --deep: #0b2e26; --green: #0f8f68; --mint: #dff5ec; --paper: #f5f7f4; --line: #dce5df; min-height: 100dvh; overflow: hidden; color: var(--ink); background: #fff; }
.marketing-nav { position: fixed; inset: 0 0 auto; z-index: 30; display: flex; align-items: center; justify-content: space-between; height: 76px; padding: 0 clamp(24px, 5vw, 76px); color: #fff; border-bottom: 1px solid rgba(255,255,255,.14); transition: background 180ms ease, box-shadow 180ms ease; }
.marketing-nav.scrolled { color: var(--ink); background: rgba(255,255,255,.96); box-shadow: 0 8px 28px rgba(16,37,31,.08); }
.brand, .marketing-nav nav { display: flex; align-items: center; }
.brand { gap: 11px; font-weight: 700; }
.brand-logo { width: 38px; height: 38px; object-fit: contain; }
.brand-name { font-size: 19px; }
.brand-divider { width: 1px; height: 18px; margin: 0 2px; background: currentColor; opacity: .3; }
.brand-product { font-size: 13px; font-weight: 500; opacity: .72; }
.marketing-nav nav { gap: 28px; font-size: 14px; font-weight: 600; }
.marketing-nav nav a { white-space: nowrap; }
.nav-login { margin-left: 10px; }
.nav-cta { margin: 0; min-height: 38px; padding: 0 19px; line-height: 38px; border: 1px solid #f0b44d; background: #f0b44d; color: #17362c; }
.hero { position: relative; display: flex; align-items: center; min-height: 760px; height: 92dvh; padding: 132px clamp(24px, 7vw, 110px) 90px; overflow: hidden; color: #fff; background: #0b2e26; }
.hero-grid-lines { position: absolute; inset: 0; opacity: .08; background-size: 54px 54px; background-image: linear-gradient(rgba(255,255,255,.55) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.55) 1px, transparent 1px); mask-image: linear-gradient(90deg, #000, transparent 76%); }
.hero-content { position: relative; z-index: 3; width: min(1100px, 56%); }
.eyebrow, .section-kicker, .team-label, .plan-code { letter-spacing: 0; font-size: 13px; font-weight: 700; }
.eyebrow { display: flex; align-items: center; gap: 10px; margin: 0 0 24px; color: #bce8d8; }
.eyebrow span { width: 32px; height: 2px; background: #f0b44d; }
.hero h1 { max-width: 1100px; margin: 0; font-size: clamp(48px, 5.6vw, 76px); line-height: 1.12; font-weight: 700; }
.hero h1 strong { display: block; color: #f0b44d; font-weight: 700; }
.hero-desc { max-width: 630px; margin: 28px 0 0; color: #d7e7e1; font-size: 18px; line-height: 1.8; }
.hero-actions { display: flex; align-items: center; gap: 28px; margin-top: 34px; }
.hero-primary { min-height: 48px; padding: 0 27px; line-height: 48px; border-color: #18a77b; background: #18a77b; }
.text-link { padding: 12px 0; color: #fff; font-weight: 700; }
.text-link span { display: inline-block; margin-left: 7px; transition: transform 160ms ease; }
.text-link:hover span { transform: translateX(4px); }
.hero-facts { display: flex; gap: 0; margin: 48px 0 0; }
.hero-facts div { min-width: 150px; padding-right: 28px; margin-right: 28px; border-right: 1px solid rgba(255,255,255,.2); }
.hero-facts div:last-child { border: 0; }
.hero-facts dt { color: #fff; font-size: 16px; font-weight: 700; }
.hero-facts dd { margin: 5px 0 0; color: #9fc0b5; font-size: 12px; }
.product-scene { position: absolute; z-index: 2; top: 54%; left: 61%; width: min(780px, 56vw); transform: translateY(-48%) rotate(-1deg); }
.scene-window { display: grid; grid-template-columns: 68px 1fr; min-height: 490px; overflow: hidden; border: 1px solid rgba(255,255,255,.24); border-radius: 8px; background: #f7f9f8; box-shadow: 0 42px 90px rgba(0,0,0,.35); }
.scene-sidebar { display: flex; align-items: center; flex-direction: column; gap: 28px; padding: 20px 14px; background: #10241f; }
.scene-logo { display: grid; place-items: center; width: 38px; aspect-ratio: 1; }
.scene-logo img { width: 100%; height: 100%; object-fit: contain; }
.scene-sidebar i { width: 22px; height: 6px; border-radius: 2px; background: #49645b; }
.scene-sidebar i.active { background: #36d5a0; }
.scene-main { padding: 30px; color: var(--ink); }
.scene-header { display: flex; justify-content: space-between; padding-bottom: 22px; border-bottom: 1px solid #e2e8e4; font-size: 20px; font-weight: 700; }
.scene-header small { color: #71827c; font-size: 11px; font-weight: 500; }
.scene-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 20px 0; }
.scene-stats div { padding: 16px; border: 1px solid #e3ebe6; border-radius: 6px; background: #fff; }
.scene-stats small, .scene-stats span { display: block; color: #788a84; font-size: 9px; }
.scene-stats strong { display: block; margin: 6px 0 4px; font-size: 23px; }
.scene-stats span { color: #13815f; }
.scene-chart, .scene-tasks { border: 1px solid #e3ebe6; border-radius: 6px; background: #fff; }
.scene-chart { padding: 18px; }
.chart-title { display: flex; justify-content: space-between; font-size: 12px; font-weight: 700; }
.chart-title small { color: #82918c; font-weight: 500; }
.bars { display: flex; align-items: flex-end; gap: 11px; height: 116px; padding-top: 16px; border-bottom: 1px solid #dce5df; }
.bars i { flex: 1; min-width: 7px; border-radius: 2px 2px 0 0; background: #4fc69e; }
.scene-tasks { margin-top: 14px; padding: 14px 18px; font-size: 11px; }
.scene-tasks > span { font-weight: 700; }
.scene-tasks p { display: flex; align-items: center; margin: 9px 0 0; color: #657871; }
.scene-tasks p i { width: 7px; height: 7px; margin-right: 8px; border-radius: 50%; background: #f0b44d; }
.scene-tasks p b { margin-left: auto; color: var(--ink); }
.scene-note { position: absolute; display: flex; flex-direction: column; width: 210px; padding: 16px 18px; border: 1px solid rgba(255,255,255,.34); border-radius: 6px; background: #fff; color: var(--ink); box-shadow: 0 18px 44px rgba(0,0,0,.18); }
.scene-note small, .scene-note span { color: #71827c; font-size: 10px; }
.scene-note strong { margin: 4px 0; font-size: 13px; }
.renewal-note { right: 2%; bottom: -7%; }
.entry-note { top: 18%; left: -8%; border-left: 4px solid #f0b44d; }
.trust-strip { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 18px 36px; min-height: 96px; padding: 22px 5vw; border-bottom: 1px solid var(--line); background: #fff; color: #657871; font-size: 13px; }
.trust-strip p { margin: 0 18px 0 0; color: var(--ink); font-weight: 700; }
.trust-strip span { white-space: nowrap; }
.section { padding: 104px clamp(24px, 7vw, 110px); }
.section-heading { max-width: 760px; margin-bottom: 52px; }
.workflow-section .section-heading { max-width: 960px; }
.section-heading.compact { margin-bottom: 36px; }
.section-kicker, .team-label, .plan-code { margin: 0 0 12px; color: var(--green); }
.section-heading h2, .operations-copy h2, .security-section h2, .final-cta h2 { margin: 0; font-size: clamp(32px, 4vw, 48px); line-height: 1.25; }
.section-heading > p:last-child { margin: 18px 0 0; color: #63766f; font-size: 16px; }
.workflow-section { background: var(--paper); }
.workflow-grid { display: grid; grid-template-columns: repeat(4, 1fr); border-top: 1px solid #cad8d0; border-bottom: 1px solid #cad8d0; }
.workflow-item { position: relative; min-width: 0; padding: 30px 24px 34px; border-right: 1px solid #cad8d0; }
.workflow-item:first-child { padding-left: 0; }
.workflow-item:last-child { padding-right: 0; border-right: 0; }
.step-number { position: absolute; top: -13px; left: 24px; padding: 0 8px; background: var(--paper); color: #71827c; font-size: 12px; font-weight: 700; }
.workflow-item:first-child .step-number { left: 0; }
.workflow-visual { position: relative; display: grid; place-items: center; height: 150px; margin-bottom: 25px; overflow: hidden; border-radius: 6px; background: #e7eee9; }
.workflow-item h3 { margin: 0 0 10px; font-size: 21px; }
.workflow-item > p { min-height: 78px; margin: 0; color: #63766f; }
.workflow-item ul, .teams-grid ul, .plan-card ul { margin: 20px 0 0; padding: 0; list-style: none; }
.workflow-item li, .teams-grid li, .plan-card li { position: relative; margin: 8px 0; padding-left: 17px; color: #4f655d; font-size: 13px; }
.workflow-item li::before, .teams-grid li::before, .plan-card li::before { position: absolute; left: 0; content: '—'; color: var(--green); }
.document-visual { width: 92px; height: 112px; padding: 24px 16px; border-radius: 5px; background: #fff; box-shadow: 12px 12px 0 #bdd6cb; }
.document-visual i { display: block; height: 4px; margin: 8px 0; background: #c9d8d1; }
.document-visual b { position: absolute; right: 20%; bottom: 20%; padding: 5px 8px; border-radius: 3px; background: #0f8f68; color: #fff; font-size: 10px; }
.flow-visual { display: flex; align-items: center; }
.flow-visual i { width: 34px; height: 34px; border: 8px solid #fff; border-radius: 50%; box-shadow: 0 0 0 2px #35a681; }
.flow-visual span { width: 24px; height: 2px; background: #8eb7a8; }
.renewal-visual { display: flex; align-items: center; flex-direction: column; }
.renewal-visual b { font-size: 52px; line-height: 1; color: #0f8f68; }
.renewal-visual span { margin-top: 4px; color: #61766e; font-size: 12px; }
.renewal-visual i { width: 76px; height: 5px; margin-top: 15px; border-radius: 3px; background: #f0b44d; }
.finance-visual i { position: absolute; bottom: 35px; width: 26px; background: #5eb596; }
.finance-visual i:nth-child(1) { left: 31%; height: 34px; }.finance-visual i:nth-child(2) { left: 44%; height: 62px; }.finance-visual i:nth-child(3) { left: 57%; height: 82px; }
.finance-visual b { position: absolute; top: 20px; right: 23%; color: #0f8f68; font-size: 26px; }
.operations-section { display: grid; grid-template-columns: .9fr 1.1fr; gap: clamp(54px, 9vw, 140px); padding: 110px clamp(24px, 7vw, 110px); color: #fff; background: #10251f; }
.operations-copy > p:not(.section-kicker) { max-width: 600px; margin: 22px 0 0; color: #b8cdc5; font-size: 16px; }
.operations-copy .section-kicker, .security-section .section-kicker { color: #58d2a8; }
.operation-list { margin-top: 40px; border-top: 1px solid #3b554c; }
.operation-list > div { display: flex; gap: 18px; padding: 18px 0; border-bottom: 1px solid #3b554c; }
.operation-list > div > span { color: #58d2a8; font-size: 12px; font-weight: 700; }
.operation-list p { display: flex; justify-content: space-between; width: 100%; margin: 0; }
.operation-list strong { font-size: 15px; }.operation-list small { color: #9eb8ae; }
.ledger-panel { align-self: center; padding: 32px; border-radius: 8px; background: #f5f7f4; color: var(--ink); box-shadow: 22px 22px 0 #1b4035; }
.ledger-panel header { display: flex; justify-content: space-between; padding-bottom: 24px; border-bottom: 1px solid var(--line); }
.ledger-panel header div { display: flex; flex-direction: column; }.ledger-panel header small { color: #73847e; }.ledger-panel header strong { margin-top: 4px; font-size: 19px; }.ledger-panel header > span { color: #63766f; font-size: 12px; }
.ledger-total { padding: 30px 0; border-bottom: 1px solid var(--line); }.ledger-total small, .ledger-total span { display: block; color: #73847e; font-size: 11px; }.ledger-total strong { display: block; margin: 8px 0; font-size: 38px; }
.ledger-lines { padding: 18px 0; }.ledger-lines p { display: flex; justify-content: space-between; margin: 12px 0; color: #63766f; }.ledger-lines b { color: var(--ink); font-weight: 600; }
.ledger-panel footer { display: flex; justify-content: space-between; padding-top: 18px; border-top: 1px solid var(--line); color: #63766f; font-size: 12px; }.ledger-panel footer b { color: var(--green); }
.teams-section { background: #fff; }
.teams-grid { display: grid; grid-template-columns: repeat(3, 1fr); border-top: 1px solid var(--line); }
.teams-grid article { min-height: 350px; padding: 34px; border-right: 1px solid var(--line); }
.teams-grid article:first-child { padding-left: 0; }.teams-grid article:last-child { padding-right: 0; border: 0; }
.teams-grid h3 { margin: 16px 0; font-size: 25px; line-height: 1.35; }.teams-grid article > p:not(.team-label) { color: #63766f; }
.security-section { display: grid; grid-template-columns: 1.2fr .8fr; gap: clamp(48px, 6vw, 96px); padding: 94px clamp(24px, 7vw, 110px); background: #e3f0ea; }
.security-section h2 { max-width: 560px; margin: 0; font-size: clamp(20px, 3vw, 40px); line-height: 1.25; }
.security-section h2 span { display: block; white-space: nowrap; }
.security-points { margin-left: -170px; }
.security-points p { display: grid; grid-template-columns: 155px 1fr; gap: 28px; margin: 0; padding: 20px 0; border-bottom: 1px solid #bfd4ca; }.security-points strong { font-size: 15px; }.security-points span { color: #587068; font-size: 14px; }
.plans-section { background: var(--paper); }
.plans-heading { display: flex; justify-content: space-between; max-width: none; }.plans-heading > p { max-width: 430px; }
.plan-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0; border: 1px solid #cad8d0; background: #fff; }
.plan-card { position: relative; min-height: 500px; padding: 38px; border-right: 1px solid #cad8d0; }.plan-card:last-child { border: 0; }.plan-card.featured { margin: -12px 0; padding-top: 50px; border: 1px solid #0f8f68; background: #f2faf6; box-shadow: 0 18px 44px rgba(15,91,68,.12); }
.recommended { position: absolute; top: 0; right: 24px; padding: 7px 12px; background: #0f8f68; color: #fff; font-size: 11px; font-weight: 700; }
.plan-card h3 { margin: 0 0 12px; font-size: 27px; }.plan-description { min-height: 70px; color: #63766f; }
.price { margin: 28px 0; font-size: 42px; font-weight: 700; font-variant-numeric: tabular-nums; }.price small { margin-right: 4px; font-size: 19px; }.price span { color: #71827c; font-size: 13px; font-weight: 500; }
.plan-card ul { min-height: 110px; margin-bottom: 28px; }.plan-card .portal-btn { width: 100%; margin: 0; text-align: center; }.plan-button { border: 1px solid #9ab1a7; background: #fff; color: var(--ink); }
.plan-skeleton { min-height: 500px; background: #edf2ef; animation: pulse 1.2s ease-in-out infinite alternate; }.plans-note { margin: 24px 0 0; color: #63766f; text-align: center; }
.final-cta { padding: 96px 24px; color: #fff; background: #0f8f68; text-align: center; }.final-cta p { margin: 0 0 12px; color: #c8f4e4; }.final-cta h2 { margin: 0 auto; }
.marketing-footer { display: grid; grid-template-columns: 1fr auto; column-gap: 28px; row-gap: 24px; padding: 32px clamp(24px, 7vw, 110px); color: #c2d3cc; background: #081c17; }.marketing-footer > p { justify-self: end; margin: 0; white-space: nowrap; }.marketing-footer nav { display: flex; align-items: center; gap: 24px; font-size: 13px; }.marketing-footer nav a { text-decoration: none; text-underline-offset: 5px; transition: color 160ms ease, text-decoration-color 160ms ease; }.marketing-footer nav a:hover, .marketing-footer nav a:focus-visible { color: #58d2a8; text-decoration: underline; }.marketing-footer small { grid-column: 2; justify-self: end; color: #78968a; }

#capabilities, #teams, #plans { scroll-margin-top: 76px; }
[data-reveal] { opacity: 0; transform: translateY(26px); transition: opacity 520ms ease, transform 600ms cubic-bezier(.2,.75,.25,1); transition-delay: var(--reveal-delay, 0ms); }
[data-reveal="left"] { transform: translateX(-28px); }
[data-reveal="right"] { transform: translateX(28px); }
[data-reveal].is-visible { opacity: 1; transform: translate(0, 0); }
@media (prefers-reduced-motion: reduce) { [data-reveal] { opacity: 1; transform: none; } }
@keyframes pulse { from { opacity: .55; } to { opacity: 1; } }
@media (max-width: 1100px) { .marketing-nav nav { gap: 18px; }.marketing-nav nav a:not(.nav-login):not(.nav-cta) { display: none; }.product-scene { left: 66%; opacity: .54; }.hero-content { width: 68%; }.workflow-grid { grid-template-columns: repeat(2, 1fr); }.workflow-item:nth-child(2) { border-right: 0; }.workflow-item:first-child { padding-left: 24px; }.workflow-item:nth-child(-n+2) { border-bottom: 1px solid #cad8d0; }.operations-section { gap: 54px; }.teams-grid article { padding: 28px; } }
@media (max-width: 1280px) { .security-points { margin-left: -80px; } }
@media (max-width: 1050px) { .security-section { grid-template-columns: 1fr; }.security-points { margin-left: 0; } }
@media (max-width: 800px) { .brand-product, .brand-divider, .nav-login { display: none; }.marketing-nav { height: 68px; padding: 0 20px; }.hero { height: auto; min-height: 760px; padding: 112px 24px 70px; align-items: flex-start; }.hero-content { width: 100%; }.hero h1 { font-size: 48px; }.hero-desc { font-size: 16px; }.hero-facts { flex-wrap: wrap; gap: 20px; }.hero-facts div { min-width: 120px; margin: 0; padding: 0 20px 0 0; }.product-scene { top: auto; bottom: -215px; left: 26%; width: 760px; opacity: .24; transform: rotate(-2deg); }.scene-note { display: none; }.trust-strip { justify-content: flex-start; }.trust-strip p { width: 100%; }.operations-section, .security-section { grid-template-columns: 1fr; }.security-points { margin-left: 0; }.teams-grid, .plan-grid { grid-template-columns: 1fr; }.teams-grid article, .plan-card { min-height: auto; padding: 30px 0; border-right: 0; border-bottom: 1px solid var(--line); }.plan-grid { padding: 0 24px; }.plan-card.featured { margin: 0 -24px; padding: 38px 24px; }.plans-heading { display: block; }.marketing-footer { grid-template-columns: 1fr; }.marketing-footer > p { justify-self: start; white-space: normal; }.marketing-footer small { grid-column: 1; justify-self: start; } }
@media (max-width: 560px) { .nav-cta { padding: 0 13px; }.brand { gap: 8px; }.brand-name { font-size: 17px; }.hero h1 { font-size: 40px; }.hero-actions { align-items: stretch; flex-direction: column; gap: 10px; }.hero-primary { text-align: center; }.hero-facts dd { font-size: 11px; }.section { padding: 76px 20px; }.workflow-grid { grid-template-columns: 1fr; }.workflow-item, .workflow-item:first-child { padding: 28px 0; border-right: 0; border-bottom: 1px solid #cad8d0; }.workflow-item .step-number, .workflow-item:first-child .step-number { left: 0; }.workflow-item > p { min-height: auto; }.operations-section { padding: 76px 20px; }.operation-list p { flex-direction: column; }.operation-list small { margin-top: 4px; }.ledger-panel { padding: 22px; box-shadow: 10px 10px 0 #1b4035; }.ledger-total strong { font-size: 30px; }.security-section { padding: 70px 20px; }.security-points p { grid-template-columns: 1fr; gap: 6px; }.marketing-footer nav { flex-wrap: wrap; }.marketing-footer > p { font-size: 13px; } }
@media (prefers-reduced-motion: reduce) { .plan-skeleton { animation: none; } }
</style>
