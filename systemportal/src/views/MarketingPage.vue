<template>
  <main class="marketing-page portal-shell">
    <header class="marketing-nav">
      <router-link class="brand" to="/">
        <span class="brand-mark">XM</span>
        <span>小马e保 SaaS</span>
      </router-link>
      <nav>
        <a href="#plans">套餐</a>
        <a href="#workflow">能力</a>
        <router-link to="/login">登录</router-link>
      </nav>
    </header>

    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">车险机构的企业门户</p>
        <h1>把企业、成员、余额和订阅放进一个清爽的工作台</h1>
        <p class="hero-desc">
          小马e保门户为车险服务团队提供企业账号、邀请加入、角色协作、余额充值和套餐订阅的一体化入口。
        </p>
        <div class="hero-actions">
          <router-link class="layui-btn portal-btn portal-btn-primary" to="/login">进入门户</router-link>
          <a class="layui-btn layui-btn-primary layui-border-green portal-btn" href="#plans">查看套餐</a>
        </div>
      </div>
      <div class="hero-preview" aria-label="门户后台预览">
        <div class="preview-top">
          <span></span>
          <span></span>
          <span></span>
        </div>
        <div class="preview-content">
          <div>
            <p>企业余额</p>
            <strong>¥12,680.50</strong>
            <small>自动续费已开启</small>
          </div>
          <div>
            <p>当前套餐</p>
            <strong>专业版</strong>
            <small>30 人上限 / 年付</small>
          </div>
          <div class="preview-list">
            <span>成员管理</span>
            <span>充值订单</span>
            <span>资金流水</span>
          </div>
        </div>
      </div>
    </section>

    <section id="workflow" class="feature-band">
      <div class="feature-card" v-for="item in features" :key="item.title">
        <i :class="item.icon"></i>
        <h2>{{ item.title }}</h2>
        <p>{{ item.text }}</p>
      </div>
    </section>

    <section id="plans" class="plans-band">
      <div class="section-title light">
        <div>
          <h2>适合车险团队的订阅套餐</h2>
          <p>套餐维度与 SaaS 表 `saas_plan` 对齐，后续可直接接入真实接口。</p>
        </div>
      </div>
      <div class="plan-grid">
        <article v-for="plan in plans" :key="plan.name" class="plan-card">
          <span class="portal-tag" :class="{ blue: plan.recommended }">{{ plan.badge }}</span>
          <h3>{{ plan.name }}</h3>
          <p>{{ plan.desc }}</p>
          <div class="price">¥{{ plan.price }}<small>/{{ plan.period }}</small></div>
          <router-link class="layui-btn portal-btn portal-btn-primary" to="/login">开始使用</router-link>
        </article>
      </div>
    </section>
  </main>
</template>

<script>
export default {
  name: 'MarketingPage',
  data() {
    return {
      features: [
        { icon: 'layui-icon layui-icon-user', title: '企业身份清晰', text: '账号与企业成员分离，拥有者、管理员、出单员职责明确。' },
        { icon: 'layui-icon layui-icon-template', title: '邀请协作可控', text: '邀请码支持人数和过期时间，管理员可统一维护加入入口。' },
        { icon: 'layui-icon layui-icon-rmb', title: '财务链路闭环', text: '企业余额、充值订单、订阅订单和资金流水互相追溯。' }
      ],
      plans: [
        { badge: '起步', name: '轻量版', desc: '5 人以内团队，快速完成门户接入。', price: '299', period: '月' },
        { badge: '推荐', name: '专业版', desc: '30 人团队协作，覆盖主要经营场景。', price: '2,999', period: '年', recommended: true },
        { badge: '进阶', name: '企业版', desc: '多网点企业，预留更高人员和服务空间。', price: '8,999', period: '年' }
      ]
    };
  }
};
</script>

<style scoped>
.marketing-page {
  color: #f8fafc;
  overflow: hidden;
}

.marketing-nav {
  position: fixed;
  top: 0;
  right: 0;
  left: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 72px;
  padding: 0 clamp(20px, 6vw, 80px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.10);
  background: rgba(7, 17, 31, 0.72);
  backdrop-filter: blur(16px);
}

.brand,
.marketing-nav nav {
  display: flex;
  align-items: center;
  gap: 18px;
  font-weight: 700;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: #22c55e;
  color: #052e16;
}

.marketing-nav nav a {
  color: #dbeafe;
  font-size: 14px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(420px, 0.8fr);
  gap: 48px;
  min-height: 92dvh;
  padding: 148px clamp(20px, 6vw, 80px) 84px;
  align-items: center;
}

.eyebrow {
  width: fit-content;
  margin: 0 0 18px;
  padding: 6px 12px;
  border: 1px solid rgba(34, 197, 94, 0.42);
  border-radius: 999px;
  color: #bbf7d0;
}

.hero h1 {
  max-width: 780px;
  margin: 0;
  font-size: clamp(42px, 6vw, 72px);
  line-height: 1.08;
  font-weight: 800;
}

.hero-desc {
  max-width: 620px;
  margin: 24px 0 0;
  color: #cbd5e1;
  font-size: 18px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 32px;
}

.hero-preview {
  min-height: 430px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.10);
  box-shadow: 0 30px 70px rgba(0, 0, 0, 0.34);
  backdrop-filter: blur(18px);
}

.preview-top {
  display: flex;
  gap: 8px;
  padding: 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}

.preview-top span {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #94a3b8;
}

.preview-content {
  display: grid;
  gap: 16px;
  padding: 26px;
}

.preview-content > div {
  padding: 22px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.56);
}

.preview-content p,
.preview-content small {
  color: #cbd5e1;
}

.preview-content strong {
  display: block;
  margin: 8px 0;
  font-size: 30px;
}

.preview-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.preview-list span {
  padding: 12px;
  border-radius: 6px;
  background: rgba(34, 197, 94, 0.16);
  text-align: center;
  color: #dcfce7;
}

.feature-band,
.plans-band {
  padding: 68px clamp(20px, 6vw, 80px);
}

.feature-band {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  background: #f8fafc;
  color: var(--portal-text);
}

.feature-card,
.plan-card {
  border-radius: 8px;
  background: #fff;
  padding: 26px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
}

.feature-card i {
  color: var(--portal-accent);
  font-size: 30px;
}

.feature-card h2,
.plan-card h3 {
  margin: 16px 0 8px;
}

.feature-card p,
.plan-card p {
  margin: 0;
  color: var(--portal-muted);
}

.plans-band {
  background: #eef4f8;
  color: var(--portal-text);
}

.section-title.light {
  color: var(--portal-text);
}

.plan-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}

.price {
  margin: 22px 0;
  font-size: 34px;
  font-weight: 800;
}

.price small {
  color: var(--portal-muted);
  font-size: 14px;
  font-weight: 600;
}

@media (max-width: 960px) {
  .hero,
  .feature-band,
  .plan-grid {
    grid-template-columns: 1fr;
  }

  .hero {
    padding-top: 120px;
  }

  .hero-preview {
    min-height: auto;
  }
}

@media (max-width: 640px) {
  .marketing-nav nav a:not(:last-child) {
    display: none;
  }

  .hero-actions {
    flex-direction: column;
  }
}
</style>
