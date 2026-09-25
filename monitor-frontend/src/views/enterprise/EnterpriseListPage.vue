<template>
  <div>
    <PageHeader
      eyebrow="Customer Operations"
      title="企业列表"
      description="集中查询和维护平台内全部企业、余额与套餐状态。"
    />
    <section class="panel query-panel">
      <div class="query-toolbar">
        <h2>全部企业</h2>
        <form class="query-filters" @submit.prevent="search">
          <input
            id="entKeyword"
            v-model.trim="query.keyword"
            class="layui-input"
            placeholder="输入企业名称、编码"
            aria-label="企业名称或编码"
          />
          <select
            id="entStatus"
            v-model="query.status"
            class="layui-select"
            aria-label="企业状态"
          >
            <option value="">全部状态</option>
            <option value="1">正常</option>
            <option value="2">欠费限制</option>
            <option value="3">已停用</option>
          </select>
          <select
            id="plan"
            v-model="query.planId"
            class="layui-select"
            aria-label="当前套餐"
          >
            <option value="">全部套餐</option>
            <option v-for="plan in plans" :key="plan.id" :value="plan.id">
              {{ plan.name }}
            </option>
          </select>
          <select
            id="expire"
            v-model="query.expireDays"
            class="layui-select"
            aria-label="到期范围"
          >
            <option value="">全部到期范围</option>
            <option value="7">7 天内到期</option>
            <option value="30">30 天内到期</option>
          </select>
          <button class="layui-btn monitor-primary" :disabled="loading">
            查询
          </button>
          <button
            type="button"
            class="layui-btn monitor-secondary"
            @click="reset"
          >
            重置
          </button>
        </form>
      </div>
      <div v-if="loading" class="loading-state">正在查询企业…</div>
      <div v-else-if="!result.list.length" class="empty-state">
        没有符合条件的企业，请调整筛选条件。
      </div>
      <template v-else
        ><div class="table-wrap">
          <table class="layui-table monitor-table">
            <thead>
              <tr>
                <th>企业</th>
                <th>状态</th>
                <th>成员数</th>
                <th>账户余额</th>
                <th>当前套餐</th>
                <th>套餐到期</th>
                <th>本月已处理工单</th>
                <th>本月系统调用</th>
                <th>本月 OCR 用量</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in result.list" :key="item.id">
                <td>
                  <strong>{{ item.name }}</strong
                  ><br /><small>{{ item.code }}</small>
                </td>
                <td>
                  <span :class="['tag', statusMeta(item.status).class]">{{
                    statusMeta(item.status).text
                  }}</span>
                </td>
                <td>{{ item.memberCount }}</td>
                <td class="number">{{ money(item.balance) }}</td>
                <td>{{ item.planName || "未订阅" }}</td>
                <td>{{ item.subscriptionEndDate || "—" }}</td>
                <td>{{ format(item.monthUsage.workorderCount) }}</td>
                <td>{{ format(item.monthUsage.requestCount) }}</td>
                <td>{{ format(item.monthUsage.ocrCount) }}</td>
                <td><div class="enterprise-actions">
                  <router-link
                    class="layui-btn layui-btn-xs monitor-primary"
                    :to="`/enterprises/${item.id}/overview`"
                    >查看</router-link
                  >
                  <button v-if="canEditSettings" type="button" class="layui-btn layui-btn-xs monitor-secondary" @click="openSettings(item)">设置</button>
                </div></td>
              </tr>
            </tbody>
          </table>
        </div>
        <AppPagination
          :page-no="result.pageNo"
          :page-size="result.pageSize"
          :total="result.total"
          @change="changePage"
      /></template>
    </section>
    <AppModal v-model="settingsVisible" title="企业设置" confirm-text="保存" :loading="settingsSaving" @confirm="saveSettings">
      <div class="field">
        <label for="enterpriseDataRetention">是否保留数据</label>
        <select id="enterpriseDataRetention" v-model.number="settingsForm.dataRetentionEnabled" class="layui-select">
          <option :value="0">不保留（到期后按规则清理）</option>
          <option :value="1">保留（跳过到期清理）</option>
        </select>
      </div>
    </AppModal>
    <AppToast :message="toastMessage" :type="toastType" />
  </div>
</template>
<script>
import PageHeader from "@/components/PageHeader.vue";
import AppPagination from "@/components/AppPagination.vue";
import AppToast from "@/components/AppToast.vue";
import AppModal from "@/components/AppModal.vue";
import feedback from "@/mixins/feedback";
import { enterpriseApi } from "@/api/monitor";
import { authState } from "@/auth/session";

export default {
  name: "EnterpriseListPage",
  components: { PageHeader, AppPagination, AppToast, AppModal },
  mixins: [feedback],
  /**
   * 保存企业关键字、状态、套餐、到期范围筛选和分页结果，同时加载套餐选项供筛选器使用。
   */
  data: () => ({
    query: {
      keyword: "",
      status: "",
      planId: "",
      expireDays: "",
      pageNo: 1,
      pageSize: 10,
    },
    result: { list: [], pageNo: 1, pageSize: 10, total: 0 },
    plans: [],
    loading: false,
    settingsVisible: false,
    settingsSaving: false,
    settingsEnterprise: null,
    settingsForm: { dataRetentionEnabled: 0 },
  }),
  computed: {
    /** 高风险数据保留特权只向监控管理员展示入口，服务端仍独立执行权限校验。 */
    canEditSettings() { return authState.user?.roleCode === "ADMIN"; },
  },
  /**
   * 页面挂载后并行加载企业列表和套餐筛选选项，任一初始化异常通过统一反馈展示。
   */
  mounted() {
    Promise.all([
      this.load(),
      enterpriseApi.subscriptionPlans().then((data) => {
        this.plans = data;
      }),
    ]).catch(this.errorMessage);
  },
  methods: {
    /** 从列表当前值打开企业设置表单，避免异步详情请求导致弹窗闪烁旧企业状态。 */
    openSettings(item) {
      this.settingsEnterprise = item;
      this.settingsForm.dataRetentionEnabled = Number(item.dataRetentionEnabled || 0);
      this.settingsVisible = true;
    },
    /** 只提交本次表单允许修改的数据保留字段；成功后同步当前页并保留原查询条件。 */
    async saveSettings() {
      if (!this.settingsEnterprise || this.settingsSaving) return;
      this.settingsSaving = true;
      try {
        const result = await enterpriseApi.updateSettings(this.settingsEnterprise.id, {
          dataRetentionEnabled: this.settingsForm.dataRetentionEnabled,
        });
        this.settingsEnterprise.dataRetentionEnabled = Number(result.dataRetentionEnabled);
        this.settingsVisible = false;
        this.notify("企业设置已保存");
      } catch (error) {
        this.errorMessage(error);
      } finally {
        this.settingsSaving = false;
      }
    },
    /**
     * 以中文千分位显示成员等数量。
     */
    format: (value) => Number(value || 0).toLocaleString("zh-CN"),
    /**
     * 以人民币两位小数显示企业余额。
     */
    money: (value) =>
      `¥ ${Number(value || 0).toLocaleString("zh-CN", {
        minimumFractionDigits: 2,
      })}`,
    /**
     * 将企业正常、欠费限制和停用状态映射为文案与标签样式。
     */
    statusMeta: (status) =>
      status === 1
        ? { text: "正常", class: "success" }
        : status === 2
        ? { text: "欠费限制", class: "danger" }
        : { text: "已停用", class: "muted" },
    /**
     * 按当前筛选和分页读取企业列表，异常时保留现有结果并显示提示。
     */
    async load() {
      this.loading = true;
      try {
        this.result = await enterpriseApi.list(this.query);
      } catch (error) {
        this.errorMessage(error);
      } finally {
        this.loading = false;
      }
    },
    /**
     * 应用企业筛选时重置到第一页。
     */
    search() {
      this.query.pageNo = 1;
      this.load();
    },
    /**
     * 清空业务筛选条件，保留每页条数并重新加载首页。
     */
    reset() {
      Object.assign(this.query, {
        keyword: "",
        status: "",
        planId: "",
        expireDays: "",
        pageNo: 1,
      });
      this.load();
    },
    /**
     * 响应分页页码变化并查询对应企业页。
     */
    changePage(pageNo) {
      this.query.pageNo = pageNo;
      this.load();
    },
  },
};
</script>
<style scoped>
.enterprise-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
.enterprise-actions :deep(.layui-btn + .layui-btn) { margin-left: 0; }
</style>
