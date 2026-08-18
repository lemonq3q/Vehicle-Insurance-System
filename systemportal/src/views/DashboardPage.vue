<template>
  <section>
    <div class="section-title">
      <div>
        <h1>仪表盘</h1>
        <p>展示个人账号、当前企业和最近一个月内的经营提醒。</p>
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
        <div v-if="loadingReminders" class="timeline-empty">正在加载近期提醒...</div>
        <div v-else-if="!notices.length" class="timeline-empty">最近一个月暂无提醒</div>
        <div v-else class="timeline">
          <div v-for="item in notices" :key="item.id" class="timeline-item" :class="`severity-${item.severity.toLowerCase()}`">
            <span></span>
            <div>
              <div class="reminder-heading">
                <strong>{{ item.title }}</strong>
                <time>{{ item.occurredAt }}</time>
              </div>
              <p>{{ item.content }}</p>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script>
import { getRoleName } from '@/utils/portalLabels';
import { getRecentReminders } from '@/api/portal';

export default {
  name: 'DashboardPage',
  /**
   * 提供仪表盘当前阶段的经营指标和提醒样板数据；账号、企业与角色信息仍从实时 Vuex 上下文读取。
   */
  data() {
    return {
      stats: [
        { label: '本月处理工单', value: '286', note: '较上月增加 18%' },
        { label: '续保提醒', value: '42', note: '12 条需要今日跟进' },
        { label: '企业成员', value: '4', note: '专业版上限 30 人' },
        { label: '企业余额', value: '¥12,680.50', note: '自动续费已开启' }
      ],
      notices: [],
      loadingReminders: false
    };
  },
  created() {
    this.loadRecentReminders();
  },
  methods: {
    /**
     * 从门户提醒接口读取已经完成时间范围和优先级排序的数据。请求失败时保留空列表，
     * 全局请求拦截器负责展示错误，仪表盘本身仍允许用户查看其他账号信息。
     */
    async loadRecentReminders() {
      this.loadingReminders = true;
      try {
        this.notices = await getRecentReminders() || [];
      } finally {
        this.loadingReminders = false;
      }
    }
  },
  computed: {
    /**
     * 暴露当前登录用户供账号概况区域展示。
     */
    user() {
      return this.$store.state.user;
    },
    /**
     * 暴露用户当前加入的企业；无企业时页面使用兜底文案。
     */
    enterprise() {
      return this.$store.state.currentEnterprise;
    },
    /**
     * 将当前成员角色代码转换为中文角色名称。
     */
    roleName() {
      return getRoleName(this.$store.state.currentMember?.roleCode);
    },
    /**
     * 取用户姓名首字符生成无图片头像，姓名缺失时使用“用户”首字。
     */
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

.timeline-item.severity-critical > span {
  background: #dc2626;
}

.timeline-item.severity-warning > span {
  background: #d97706;
}

.reminder-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.reminder-heading time {
  flex: none;
  color: var(--portal-muted);
  font-size: 12px;
}

.timeline-empty {
  padding: 28px 0;
  color: var(--portal-muted);
  text-align: center;
}

@media (max-width: 980px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
