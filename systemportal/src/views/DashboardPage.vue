<template>
  <section aria-labelledby="dashboard-title">
    <div class="section-title">
      <div>
        <h1 id="dashboard-title">仪表盘</h1>
        <p>展示个人账号、当前企业和近期经营状态。</p>
      </div>
      <span v-if="statistics" class="portal-tag blue">统计至 {{ statistics.updatedThrough }}</span>
    </div>

    <div v-if="statistics" class="stat-grid">
      <article class="portal-card stat-card">
        <div class="stat-heading"><span class="stat-icon"><i class="layui-icon layui-icon-form" aria-hidden="true"></i></span><span>本月</span></div>
        <div class="stat-label">处理工单数量</div>
        <div class="stat-value">{{ formatNumber(statistics.currentMonthProcessedWorkorders) }}</div>
        <div class="stat-note" :class="trendClass(statistics.workorderComparison)">
          {{ trendText(statistics.workorderComparison) }} · 上月 {{ formatNumber(statistics.previousMonthProcessedWorkorders) }} 单
        </div>
      </article>
      <article class="portal-card stat-card">
        <div class="stat-heading"><span class="stat-icon"><i class="layui-icon layui-icon-notice" aria-hidden="true"></i></span><span>当前</span></div>
        <div class="stat-label">续保提醒数量</div>
        <div class="stat-value">{{ formatNumber(statistics.renewalReminderCount) }}</div>
        <div class="stat-note">其中 {{ formatNumber(statistics.renewalDueThisWeek) }} 单需要在 7 天内处理</div>
      </article>
      <article class="portal-card stat-card">
        <div class="stat-heading"><span class="stat-icon"><i class="layui-icon layui-icon-group" aria-hidden="true"></i></span><span>本月</span></div>
        <div class="stat-label">新增客户数量</div>
        <div class="stat-value">{{ formatNumber(statistics.currentMonthNewCustomers) }}</div>
        <div class="stat-note" :class="trendClass(statistics.customerComparison)">
          {{ trendText(statistics.customerComparison) }} · 上月 {{ formatNumber(statistics.previousMonthNewCustomers) }} 家
        </div>
      </article>
      <article class="portal-card stat-card">
        <div class="stat-heading"><span class="stat-icon"><i class="layui-icon layui-icon-rmb" aria-hidden="true"></i></span><span>本月</span></div>
        <div class="stat-label">企业盈利</div>
        <div class="stat-value money-value">{{ formatMoney(statistics.currentMonthProfit) }}</div>
        <div class="stat-note" :class="trendClass(statistics.profitComparison)">
          {{ trendText(statistics.profitComparison) }} · 上月 {{ formatMoney(statistics.previousMonthProfit) }}
        </div>
      </article>
    </div>

    <div v-else-if="loadingStatistics" class="stat-grid" aria-label="正在加载经营数据">
      <article v-for="index in 4" :key="index" class="portal-card stat-card skeleton-card">
        <span></span><strong></strong><small></small>
      </article>
    </div>

    <div v-else class="portal-card statistics-error" role="alert">
      <span>{{ statisticsError || '经营数据暂时无法加载' }}</span>
      <button class="portal-btn portal-btn-primary" @click="loadStatistics">重新加载</button>
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
import { getDashboardStatistics, getRecentReminders } from '@/api/portal';
import { getRoleName } from '@/utils/portalLabels';

export default {
  name: 'DashboardPage',
  /**
   * 分别维护顶部经营统计与原有近期提醒的加载状态。任一区域失败都不影响另一块内容展示，
   * 账号概况继续直接使用 Vuex 中的当前登录上下文。
   */
  data() {
    return {
      statistics: null,
      statisticsError: '',
      loadingStatistics: false,
      notices: [],
      loadingReminders: false
    };
  },
  created() {
    this.loadStatistics();
    this.loadRecentReminders();
  },
  computed: {
    /** 返回当前登录用户供账号概况展示。 */
    user() { return this.$store.state.user; },
    /** 返回用户当前企业，无企业时由模板显示兜底文案。 */
    enterprise() { return this.$store.state.currentEnterprise; },
    /** 将成员角色代码转换为网站统一中文角色名。 */
    roleName() { return getRoleName(this.$store.state.currentMember?.roleCode); },
    /** 使用姓名首字符生成原有无图片头像。 */
    initials() { return (this.user?.realName || '用户').slice(0, 1); }
  },
  methods: {
    /** 读取顶部四项真实经营指标，失败时提供局部重试而不隐藏下方账号和提醒。 */
    async loadStatistics() {
      this.loadingStatistics = true;
      this.statisticsError = '';
      try {
        this.statistics = await getDashboardStatistics();
      } catch (error) {
        this.statisticsError = error?.message || '经营数据暂时无法加载';
      } finally {
        this.loadingStatistics = false;
      }
    },
    /** 读取当前企业最近一个月提醒，保持原页面严重程度和时间排序展示。 */
    async loadRecentReminders() {
      this.loadingReminders = true;
      try {
        this.notices = await getRecentReminders() || [];
      } finally {
        this.loadingReminders = false;
      }
    },
    /** 将计数格式化为中文千分位数字。 */
    formatNumber(value) { return Number(value || 0).toLocaleString('zh-CN'); },
    /** 将盈利格式化为人民币两位小数并保留负数。 */
    formatMoney(value) {
      return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    },
    /** 把环比结构转换为完整文字，确保不依赖颜色传达涨跌。 */
    trendText(comparison = {}) {
      if (comparison.direction === 'NEW') return '上月暂无数据';
      if (comparison.direction === 'FLAT') return '与上月持平';
      return `较上月${comparison.direction === 'UP' ? '增加' : '减少'} ${comparison.rate || 0}%`;
    },
    /** 下降时使用网站现有危险色，上涨和中性继续沿用原统计说明色。 */
    trendClass(comparison = {}) {
      return { decrease: comparison.direction === 'DOWN' };
    }
  }
};
</script>

<style scoped>
.stat-card { position: relative; min-height: 196px; padding: 20px; overflow: hidden; border-top: 3px solid var(--portal-accent); transition: transform 180ms ease, box-shadow 180ms ease; }
.stat-card::after { position: absolute; right: -34px; bottom: -42px; width: 118px; height: 118px; border-radius: 999px; background: rgba(22, 163, 74, .06); content: ''; }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 14px 30px rgba(15, 23, 42, .09); }
.stat-heading { position: relative; z-index: 1; display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; color: var(--portal-muted); font-size: 12px; font-weight: 600; }
.stat-icon { display: inline-flex; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: 8px; background: rgba(22, 163, 74, .10); color: var(--portal-accent-strong); font-size: 18px; }
.stat-label, .stat-value, .stat-note { position: relative; z-index: 1; }
.stat-value { font-variant-numeric: tabular-nums; }
.money-value { font-size: clamp(23px, 2vw, 28px); font-variant-numeric: tabular-nums; }
.stat-note { white-space: normal; }
.stat-note.decrease { color: var(--portal-danger); }
.statistics-error { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 126px; padding: 18px; color: var(--portal-muted); }
.dashboard-grid { display: grid; grid-template-columns: .9fr 1.1fr; gap: 18px; margin-top: 18px; }
.panel { padding: 22px; }
.panel h2 { margin: 0 0 18px; font-size: 18px; }
.profile-strip { display: flex; align-items: center; gap: 14px; padding: 18px; border-radius: var(--portal-radius); background: #f8fafc; }
.avatar { display: flex; align-items: center; justify-content: center; width: 52px; height: 52px; border-radius: var(--portal-radius); background: #dcfce7; color: #166534; font-weight: 800; }
.profile-strip p, .timeline-item p { margin: 4px 0 0; color: var(--portal-muted); }
.info-list { padding: 0; margin: 18px 0 0; list-style: none; }
.info-list li { display: flex; justify-content: space-between; gap: 14px; padding: 13px 0; border-bottom: 1px solid var(--portal-border); }
.info-list span { color: var(--portal-muted); }
.timeline { display: grid; gap: 16px; }
.timeline-item { display: grid; grid-template-columns: 14px 1fr; gap: 12px; }
.timeline-item > span { width: 10px; height: 10px; margin-top: 8px; border-radius: 999px; background: var(--portal-accent); }
.timeline-item.severity-critical > span { background: var(--portal-danger); }
.timeline-item.severity-warning > span { background: var(--portal-warning); }
.reminder-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.reminder-heading time { flex: none; color: var(--portal-muted); font-size: 12px; }
.timeline-empty { padding: 28px 0; color: var(--portal-muted); text-align: center; }
.skeleton-card span, .skeleton-card strong, .skeleton-card small { display: block; border-radius: 6px; background: linear-gradient(90deg,var(--portal-soft) 25%,#f8fafc 50%,var(--portal-soft) 75%); background-size: 200% 100%; animation: shimmer 1.4s infinite; }
.skeleton-card span { width: 38%; height: 18px; }
.skeleton-card strong { width: 50%; height: 34px; margin-top: 12px; }
.skeleton-card small { width: 72%; height: 16px; margin-top: 14px; }
@keyframes shimmer { to { background-position: -200% 0; } }
@media (prefers-reduced-motion: reduce) { .skeleton-card span, .skeleton-card strong, .skeleton-card small { animation: none; } }
@media (max-width: 980px) { .dashboard-grid { grid-template-columns: 1fr; } }
@media (max-width: 680px) {
  .statistics-error { align-items: stretch; flex-direction: column; }
  .statistics-error .portal-btn { min-height: 44px; }
  .reminder-heading { flex-direction: column; gap: 4px; }
}
</style>
