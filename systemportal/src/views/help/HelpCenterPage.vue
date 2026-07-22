<template>
  <section>
    <div class="section-title">
      <div>
        <h1>帮助中心</h1>
        <p>客服与帮助内容暂未正式上线，当前用占位页预留入口。</p>
      </div>
    </div>

    <article class="portal-card help-card">
      <div class="help-icon">
        <i class="layui-icon layui-icon-service"></i>
      </div>
      <h2>客服中心待上线</h2>
      <p>后续这里会承载在线客服、常见问题、工单反馈和服务公告。当前版本先保留菜单入口，避免后续路由结构变化。</p>
      <div class="help-actions">
        <router-link class="layui-btn portal-btn portal-btn-primary" to="/portal/dashboard">返回仪表盘</router-link>
        <router-link class="layui-btn layui-btn-primary layui-border-green portal-btn" to="/portal/user/profile">检查账号资料</router-link>
      </div>
    </article>
  </section>
</template>

<script>
export default {
  name: 'HelpCenterPage'
};
</script>

<style scoped>
.help-card {
  max-width: 720px;
  padding: 48px;
  margin: 20px auto 0;
  text-align: center;
}

.help-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 8px;
  background: #e8f7ee;
  color: #166534;
}

.help-icon i {
  font-size: 36px;
}

.help-card h2 {
  margin: 20px 0 10px;
  font-size: 26px;
}

.help-card p {
  margin: 0 auto;
  max-width: 560px;
  color: var(--portal-muted);
}

.help-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 26px;
  flex-wrap: wrap;
}
</style>
