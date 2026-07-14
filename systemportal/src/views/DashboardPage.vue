<template>
  <section>
    <div class="section-title">
      <div>
        <h1>仪表盘</h1>
        <p>展示个人账号、当前企业和近期经营状态，当前为静态样板数据。</p>
      </div>
      <span class="portal-tag blue">今日 2026-07-07</span>
    </div>

    <div class="stat-grid">
      <div class="portal-card stat-card" v-for="item in stats" :key="item.label">
        <div class="stat-label">{{ item.label }}</div>
        <div class="stat-value">{{ item.value }}</div>
        <div class="stat-note">{{ item.note }}</div>
      </div>
    </div>

    <div class="dashboard-grid">
      <article class="portal-card panel">
        <h2>账号概况</h2>
        <div class="profile-strip">
          <div class="avatar">{{ initials }}</div>
          <div>
            <strong>{{ user?.realName || '未登录用户' }}</strong>
            <p>{{ user?.phone }}</p>
          </div>
        </div>
        <ul class="info-list">
          <li><span>当前企业</span><strong>{{ enterprise?.name || '暂无企业' }}</strong></li>
          <li><span>企业角色</span><strong>{{ roleName }}</strong></li>
          <li><span>最后登录</span><strong>{{ user?.lastLoginTime }}</strong></li>
        </ul>
      </article>

      <article class="portal-card panel">
        <h2>近期提醒</h2>
        <div class="timeline">
          <div v-for="item in notices" :key="item.title" class="timeline-item">
            <span></span>
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.text }}</p>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script>
import { getRoleName } from '@/mock/portalMock';

export default {
  name: 'DashboardPage',
  data() {
    return {
      stats: [
        { label: '本月处理工单', value: '286', note: '较上月增加 18%' },
        { label: '续保提醒', value: '42', note: '12 条需要今日跟进' },
        { label: '企业成员', value: '4', note: '专业版上限 30 人' },
        { label: '企业余额', value: '¥12,680.50', note: '自动续费已开启' }
      ],
      notices: [
        { title: '套餐续费充足', text: '下一次自动续费时间为 2027-06-25。' },
        { title: '邀请码仍可使用', text: 'XMEB-7K29Q 剩余 3 次可用名额。' },
        { title: '资金流水正常', text: '最近一次充值订单已于 2026-07-05 支付成功。' }
      ]
    };
  },
  computed: {
    user() {
      return this.$store.state.user;
    },
    enterprise() {
      return this.$store.state.currentEnterprise;
    },
    roleName() {
      return getRoleName(this.$store.state.currentMember?.roleCode);
    },
    initials() {
      return (this.user?.realName || '用户').slice(0, 1);
    }
  }
};
</script>

<style scoped>
.dashboard-grid {
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  gap: 18px;
  margin-top: 18px;
}

.panel {
  padding: 22px;
}

.panel h2 {
  margin: 0 0 18px;
  font-size: 18px;
}

.profile-strip {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border-radius: 8px;
  background: #f8fafc;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 8px;
  background: #dcfce7;
  color: #166534;
  font-weight: 800;
}

.profile-strip p,
.timeline-item p {
  margin: 4px 0 0;
  color: var(--portal-muted);
}

.info-list {
  padding: 0;
  margin: 18px 0 0;
  list-style: none;
}

.info-list li {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 13px 0;
  border-bottom: 1px solid var(--portal-border);
}

.info-list span {
  color: var(--portal-muted);
}

.timeline {
  display: grid;
  gap: 16px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 14px 1fr;
  gap: 12px;
}

.timeline-item > span {
  width: 10px;
  height: 10px;
  margin-top: 8px;
  border-radius: 999px;
  background: var(--portal-accent);
}

@media (max-width: 980px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
