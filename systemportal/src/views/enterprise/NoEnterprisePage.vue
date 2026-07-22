<template>
  <section class="empty-enterprise-page">
    <article class="portal-card empty-enterprise-panel">
      <span class="empty-icon" aria-hidden="true"><i class="layui-icon layui-icon-template-1"></i></span>
      <h1>尚未加入企业</h1>
      <p>当前账号还没有企业成员关系，加入或创建企业后即可使用企业人员、订阅和财务功能。</p>
      <router-link class="layui-btn portal-btn portal-btn-primary" to="/portal/enterprise/info">前往加入企业</router-link>
    </article>
  </section>
</template>

<script>
export default { name: 'NoEnterprisePage' };
</script>

<style scoped>
.empty-enterprise-page {
  display: grid;
  min-height: calc(100dvh - 150px);
  place-items: center;
}

.empty-enterprise-panel {
  width: min(520px, 100%);
  padding: 42px 34px;
  text-align: center;
}

.empty-icon {
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  margin: 0 auto 18px;
  border-radius: 50%;
  color: var(--portal-accent-strong);
  background: #eaf8ef;
}

.empty-icon i {
  font-size: 27px;
}

.empty-enterprise-panel h1 {
  margin: 0 0 10px;
  font-size: 25px;
}

.empty-enterprise-panel p {
  margin: 0 0 24px;
  color: var(--portal-muted);
  line-height: 1.75;
}
</style>
