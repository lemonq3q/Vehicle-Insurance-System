<template>
  <div>
    <PageHeader
      eyebrow="System Governance"
      title="审计日志"
      description="查看平台人工操作及后续接入的系统、维护与安全事件。"
    >
      <button
        class="layui-btn monitor-secondary"
        type="button"
        :disabled="loading"
        @click="load"
      >
        刷新列表
      </button>
    </PageHeader>

    <section class="panel query-panel log-panel">
      <div class="query-toolbar">
        <h2>系统日志</h2>
        <form class="query-filters log-filters" @submit.prevent="search">
          <select
            v-model="query.category"
            class="layui-select"
            aria-label="审计类别"
          >
            <option value="">全部类别</option>
            <option
              v-for="item in categories"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </option>
          </select>
          <select
            v-model="query.severity"
            class="layui-select"
            aria-label="严重等级"
          >
            <option value="">全部等级</option>
            <option
              v-for="item in severities"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </option>
          </select>
          <el-config-provider :locale="elementLocale">
            <el-date-picker
              v-model="dateRange"
              class="monitor-date-range"
              type="daterange"
              unlink-panels
              range-separator="到"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY/MM/DD"
              value-format="YYYY-MM-DD"
              :shortcuts="dateShortcuts"
              :disabled-date="disabledDate"
              :clearable="true"
              placement="bottom-end"
              :fallback-placements="['bottom-end']"
              popper-class="monitor-date-range-popper"
              aria-label="审计日志时间范围"
            />
          </el-config-provider>
          <button class="layui-btn monitor-primary" :disabled="loading">
            查询
          </button>
          <button
            class="layui-btn monitor-secondary"
            type="button"
            @click="reset"
          >
            重置
          </button>
        </form>
      </div>
      <div v-if="loading" class="loading-state">正在查询审计日志…</div>
      <div v-else-if="!result.list.length" class="empty-state">
        当前时间范围内没有符合条件的日志。
      </div>
      <template v-else>
        <div class="table-wrap">
          <table class="layui-table monitor-table log-table">
            <thead>
              <tr>
                <th>发生时间</th>
                <th>类别</th>
                <th>等级</th>
                <th>事件</th>
                <th>操作人</th>
                <th>关联企业</th>
                <th>操作目标</th>
                <th>结果</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody v-for="item in result.list" :key="item.id">
              <tr>
                <td class="number">{{ dateTime(item.occurredAt) }}</td>
                <td>
                  <span class="tag">{{ categoryText(item.logCategory) }}</span>
                </td>
                <td>
                  <span :class="['tag', severityMeta(item.severity).class]">{{
                    severityMeta(item.severity).text
                  }}</span>
                </td>
                <td class="event-cell">
                  <strong>{{ item.eventName }}</strong
                  ><small>{{ item.summary }}</small
                  ><code>{{ item.eventCode }}</code>
                </td>
                <td>
                  <strong>{{ item.operatorNameSnapshot || "系统" }}</strong
                  ><small>{{ operatorText(item.operatorType) }}</small>
                </td>
                <td>{{ item.enterpriseNameSnapshot || "—" }}</td>
                <td>
                  <span>{{
                    item.targetNameSnapshot || item.targetId || "—"
                  }}</span
                  ><small>{{ item.targetType || "—" }}</small>
                </td>
                <td>
                  <span :class="['tag', resultMeta(item.resultStatus).class]">{{
                    resultMeta(item.resultStatus).text
                  }}</span>
                </td>
                <td>
                  <button
                    class="layui-btn layui-btn-xs monitor-secondary"
                    type="button"
                    :aria-expanded="expandedId === item.id"
                    @click="toggle(item.id)"
                  >
                    {{ expandedId === item.id ? "收起" : "查看" }}
                  </button>
                </td>
              </tr>
              <tr v-if="expandedId === item.id" class="detail-row">
                <td colspan="9">
                  <div class="log-detail">
                    <dl>
                      <div>
                        <dt>操作原因</dt>
                        <dd>{{ item.operationReason || "—" }}</dd>
                      </div>
                      <div>
                        <dt>请求 ID</dt>
                        <dd class="number">{{ item.requestId || "—" }}</dd>
                      </div>
                      <div>
                        <dt>IP 地址</dt>
                        <dd class="number">{{ item.ipAddress || "—" }}</dd>
                      </div>
                      <div>
                        <dt>来源</dt>
                        <dd>
                          {{ item.sourceSystem }} /
                          {{ item.sourceModule || "—" }}
                        </dd>
                      </div>
                    </dl>
                    <div class="snapshot-grid">
                      <section>
                        <h3>变更前</h3>
                        <pre>{{ prettyJson(item.beforeJson) }}</pre>
                      </section>
                      <section>
                        <h3>变更后</h3>
                        <pre>{{ prettyJson(item.afterJson) }}</pre>
                      </section>
                    </div>
                    <div v-if="item.errorMessage" class="log-error">
                      <strong>{{ item.errorCode || "系统错误" }}</strong
                      ><span>{{ item.errorMessage }}</span>
                    </div>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <AppPagination
          :page-no="result.pageNo"
          :page-size="result.pageSize"
          :total="result.total"
          @change="changePage"
        />
      </template>
    </section>
    <AppToast :message="toastMessage" :type="toastType" />
  </div>
</template>

<script>
import PageHeader from "@/components/PageHeader.vue";
import AppPagination from "@/components/AppPagination.vue";
import AppToast from "@/components/AppToast.vue";
import { ElConfigProvider, ElDatePicker } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import "element-plus/es/components/date-picker/style/css";
import feedback from "@/mixins/feedback";
import { systemLogApi } from "@/api/monitor";
/** 以本地日历日生成 yyyy-MM-dd，避免 UTC 转换导致日期偏移。 */
function localDate(offsetDays = 0) {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate()
  )}`;
}
/** 创建截止今天的常用范围，保持与车险系统日期选择器一致的快捷入口。 */
function recentRange(days) {
  const end = new Date();
  const start = new Date();
  start.setDate(start.getDate() - days + 1);
  return [start, end];
}
const dateShortcuts = [
  { text: "当天", value: () => recentRange(1) },
  { text: "近一周", value: () => recentRange(7) },
  { text: "近一月", value: () => recentRange(30) },
  { text: "近一年", value: () => recentRange(365) },
];
export default {
  name: "SystemLogPage",
  components: {
    PageHeader,
    AppPagination,
    AppToast,
    ElConfigProvider,
    ElDatePicker,
  },
  mixins: [feedback],
  /** 保存审计筛选、服务端分页和单行展开状态，默认查询包含今天的近 30 个自然日。 */
  data: () => ({
    /**
     * 仅向本页日期范围选择器注入 Element Plus 简体中文语言包，
     * 中文化月份、星期与面板按钮，同时避免改变监控平台其他独立组件的语言环境。
     */
    elementLocale: zhCn,
    query: {
      category: "",
      severity: "",
      startDate: localDate(-29),
      endDate: localDate(),
      pageNo: 1,
      pageSize: 10,
    },
    result: { list: [], pageNo: 1, pageSize: 10, total: 0 },
    loading: true,
    expandedId: null,
    today: localDate(),
    dateShortcuts,
    categories: [
      { value: "OPERATION", label: "人工操作" },
      { value: "MAINTENANCE", label: "系统维护" },
      { value: "SYSTEM", label: "系统运行" },
      { value: "SECURITY", label: "安全事件" },
      { value: "BUSINESS", label: "业务事件" },
    ],
    severities: [
      { value: "DEBUG", label: "调试" },
      { value: "INFO", label: "信息" },
      { value: "WARN", label: "警告" },
      { value: "ERROR", label: "错误" },
      { value: "CRITICAL", label: "严重" },
    ],
  }),
  computed: {
    /**
     * Element Plus 以数组维护范围；页面继续映射到 startDate/endDate，组件替换不改变后端契约。
     */
    dateRange: {
      get() {
        return this.query.startDate && this.query.endDate
          ? [this.query.startDate, this.query.endDate]
          : [];
      },
      set(value) {
        this.query.startDate = value?.[0] || "";
        this.query.endDate = value?.[1] || "";
      },
    },
  },
  /** 页面进入后立即读取真实日志数据。 */ mounted() {
    this.load();
  },
  methods: {
    /** 禁止选择今天之后的日期，避免产生后端必然无数据的查询范围。 */
    disabledDate(date) {
      const endOfToday = new Date();
      endOfToday.setHours(23, 59, 59, 999);
      return date.getTime() > endOfToday.getTime();
    },
    /** 移除空筛选值后读取真实分页接口，新数据返回时收起旧详情。 */ async load() {
      this.loading = true;
      this.expandedId = null;
      try {
        const params = Object.fromEntries(
          Object.entries(this.query).filter(([, value]) => value !== "")
        );
        this.result = await systemLogApi.list(params);
      } catch (error) {
        this.errorMessage(error);
      } finally {
        this.loading = false;
      }
    },
    /** 新查询从第一页开始。 */ search() {
      this.query.pageNo = 1;
      this.load();
    },
    /** 恢复全类别、全等级和近 30 日默认范围。 */ reset() {
      Object.assign(this.query, {
        category: "",
        severity: "",
        startDate: localDate(-29),
        endDate: localDate(),
        pageNo: 1,
      });
      this.load();
    },
    /** 分页时保留当前筛选。 */ changePage(pageNo) {
      this.query.pageNo = pageNo;
      this.load();
    },
    /** 表格中同时只展开一条快照，避免长 JSON 破坏扫读节奏。 */ toggle(id) {
      this.expandedId = this.expandedId === id ? null : id;
    },
    /** 安全解析后端 JSON 字符串，历史非法内容原样展示而不阻断页面。 */ prettyJson(
      value
    ) {
      if (!value) return "—";
      try {
        return JSON.stringify(
          typeof value === "string" ? JSON.parse(value) : value,
          null,
          2
        );
      } catch {
        return String(value);
      }
    },
    categoryText(value) {
      return (
        this.categories.find((item) => item.value === value)?.label || value
      );
    },
    operatorText(value) {
      return (
        {
          MONITOR_USER: "监控用户",
          TENANT_USER: "企业用户",
          SYSTEM: "系统",
          JOB: "维护任务",
        }[value] || value
      );
    },
    severityMeta(value) {
      return value === "CRITICAL" || value === "ERROR"
        ? { text: value === "CRITICAL" ? "严重" : "错误", class: "danger" }
        : value === "WARN"
        ? { text: "警告", class: "warning" }
        : value === "DEBUG"
        ? { text: "调试", class: "muted" }
        : { text: "信息", class: "" };
    },
    resultMeta(value) {
      return value === "SUCCESS"
        ? { text: "成功", class: "success" }
        : value === "FAILED"
        ? { text: "失败", class: "danger" }
        : value === "RUNNING"
        ? { text: "执行中", class: "warning" }
        : { text: "部分完成", class: "warning" };
    },
    dateTime(value) {
      return value ? String(value).replace("T", " ").slice(0, 19) : "—";
    },
  },
};
</script>

<style scoped>
.log-panel {
  padding: 22px;
}
.log-filters {
  flex-wrap: wrap;
}
.log-filters > .layui-select {
  width: 138px;
}
.log-table {
  min-width: 1320px;
}
.log-table small {
  display: block;
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
}
.event-cell {
  max-width: 360px;
}
.event-cell small {
  white-space: normal;
}
.event-cell code {
  display: inline-block;
  margin-top: 5px;
  color: var(--muted);
  font-size: 11px;
}
.detail-row > td {
  padding: 0 !important;
  background: #f8fafc;
}
.log-detail {
  padding: 18px 20px;
  border-left: 3px solid var(--primary);
}
.log-detail dl {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 0 0 16px;
}
.log-detail dl div {
  min-width: 0;
}
.log-detail dt {
  margin-bottom: 4px;
  color: var(--muted);
  font-size: 12px;
}
.log-detail dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.snapshot-grid section {
  min-width: 0;
}
.snapshot-grid h3 {
  margin: 0 0 7px;
  font-size: 13px;
}
.snapshot-grid pre {
  min-height: 76px;
  max-height: 260px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text);
  font: 12px/1.6 Consolas, monospace;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.log-error {
  display: flex;
  gap: 10px;
  margin-top: 12px;
  padding: 11px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fff7f7;
  color: #991b1b;
}
@media (max-width: 980px) {
  .log-detail dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 720px) {
  .log-filters {
    width: 100%;
    margin-left: 0;
  }
  .log-filters > .layui-select {
    width: 100%;
  }
  .log-filters > .layui-btn {
    flex: 1;
  }
  .snapshot-grid,
  .log-detail dl {
    grid-template-columns: 1fr;
  }
}
</style>
