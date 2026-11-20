<template>
  <div>
    <PageHeader
      eyebrow="System Governance"
      title="请求日志"
      description="查询用户操作、接口处理结果与服务器异常。"
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
    <section class="panel query-panel request-log-panel" :aria-busy="loading">
      <div class="query-toolbar"><h2>请求记录</h2></div>
      <form class="request-filters" @submit.prevent="search">
        <div class="field date-field">
          <label for="request-date-range">请求时间</label>
          <el-config-provider :locale="elementLocale">
            <el-date-picker
              id="request-date-range"
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
              :clearable="false"
              placement="bottom-end"
              popper-class="monitor-date-range-popper"
            />
          </el-config-provider>
        </div>
        <div class="field">
          <label for="request-service">所属后端</label
          ><select
            id="request-service"
            v-model="query.sourceSystem"
            class="layui-select"
          >
            <option value="">全部后端</option>
            <option value="InsuranceSystem">车险后端</option>
            <option value="InsuranceSystemSaas">SaaS 后端</option>
            <option value="InsuranceSystemMonitor">监控后端</option>
            <option value="MaintenanceCoordinator">维护协调器</option>
          </select>
        </div>
        <div class="field">
          <label for="request-exception">异常情况</label
          ><select
            id="request-exception"
            v-model="query.hasException"
            class="layui-select"
          >
            <option value="">全部情况</option>
            <option value="1">有异常</option>
            <option value="0">无异常</option>
          </select>
        </div>
        <div class="field">
          <label for="request-httpStatus">HTTP 状态</label
          ><input
            id="request-httpStatus"
            v-model="query.httpStatus"
            class="layui-input"
            type="number"
            placeholder="如：500"
          />
        </div>
        <div class="field">
          <label for="request-businessCode">业务返回码</label
          ><input
            id="request-businessCode"
            v-model="query.businessCode"
            class="layui-input"
            type="text"
            placeholder="如：500"
          />
        </div>
        <div class="field">
          <label for="request-enterpriseId">企业 ID</label
          ><input
            id="request-enterpriseId"
            v-model="query.enterpriseId"
            class="layui-input"
            type="number"
            placeholder="输入企业 ID"
          />
        </div>
        <div class="field">
          <label for="request-userId">用户 ID</label
          ><input
            id="request-userId"
            v-model="query.userId"
            class="layui-input"
            type="number"
            placeholder="输入用户 ID"
          />
        </div>
        <div class="field wide-field">
          <label for="request-uri">请求 URI</label
          ><input
            id="request-uri"
            v-model="query.uri"
            class="layui-input"
            type="text"
            placeholder="输入完整请求路径，精确匹配"
          />
        </div>
        <div class="field wide-field">
          <label for="request-logId">日志 ID</label
          ><input
            id="request-logId"
            v-model="query.logId"
            class="layui-input"
            type="text"
            placeholder="输入完整日志 ID"
          />
        </div>
        <div class="action-row filter-actions">
          <button class="layui-btn monitor-primary" :disabled="loading">
            查询</button
          ><button
            class="layui-btn monitor-secondary"
            type="button"
            :disabled="loading"
            @click="reset"
          >
            重置
          </button>
        </div>
      </form>
      <div v-if="loading" class="loading-state" role="status">
        正在查询请求日志…
      </div>
      <div v-else-if="failed" class="empty-state" role="status">
        查询失败，请重试。
      </div>
      <div v-else-if="!result.list.length" class="empty-state">
        当前时间范围内没有符合条件的请求日志。
      </div>
      <template v-else>
        <div class="table-wrap">
          <table class="layui-table monitor-table request-table">
            <thead>
              <tr>
                <th>接收时间</th>
                <th>所属后端</th>
                <th>请求</th>
                <th>企业 / 用户</th>
                <th>HTTP / 业务码</th>
                <th>处理耗时</th>
                <th>异常情况</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody v-for="row in result.list" :key="row.log_id">
              <tr>
                <td class="number">{{ dateTime(row.started_at) }}</td>
                <td>{{ serviceName(row.source_system) }}</td>
                <td class="request-uri">
                  <span class="request-method">{{ row.method }}</span
                  ><span :title="row.uri">{{ row.uri }}</span>
                </td>
                <td class="number">
                  {{ row.enterprise_id ?? "—" }} / {{ row.user_id ?? "—" }}
                </td>
                <td class="number">
                  {{ row.http_status }} / {{ row.business_code || "—" }}
                </td>
                <td class="number">{{ row.duration_ms }} ms</td>
                <td>
                  <span
                    :class="['tag', row.has_exception ? 'danger' : 'muted']"
                    >{{ row.has_exception ? "有异常" : "无异常" }}</span
                  >
                </td>
                <td>
                  <button
                    class="layui-btn layui-btn-sm monitor-secondary"
                    :aria-expanded="expandedId === row.log_id"
                    @click="showDetail(row)"
                  >
                    {{ expandedId === row.log_id ? "收起" : "详情" }}
                  </button>
                </td>
              </tr>
              <tr v-if="expandedId === row.log_id">
                <td colspan="8">
                  <div class="request-detail">
                    <h3>请求详情</h3>
                    <p class="helper">日志 ID：{{ row.log_id }}</p>
                    <div
                      v-if="detailLoading"
                      class="loading-state"
                      role="status"
                    >
                      正在加载详情…
                    </div>
                    <pre v-else>{{ detail }}</pre>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <AppPagination
          :page-no="query.pageNo"
          :page-size="query.pageSize"
          :total="result.total"
          @change="changePage"
        />
      </template>
    </section>
  </div>
</template>
<script>
import AppPagination from "@/components/AppPagination.vue";
import { ElConfigProvider, ElDatePicker } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import "element-plus/es/components/date-picker/style/css";
import PageHeader from "@/components/PageHeader.vue";
import { requestLogApi } from "@/api/requestLogs";
/** 默认查询本地当天，防止无界扫描历史日志。 */
function initialQuery() {
  const d = new Date();
  const today = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(
    2,
    "0"
  )}-${String(d.getDate()).padStart(2, "0")}`;
  return {
    startDate: today,
    endDate: today,
    sourceSystem: "",
    hasException: "",
    httpStatus: "",
    businessCode: "",
    uri: "",
    enterpriseId: "",
    userId: "",
    logId: "",
    pageNo: 1,
    pageSize: 20,
  };
}
/** 按本地日历日生成快捷日期，与审计页采用同样的日期范围交互。 */
function recentRange(days) {
  const end = new Date();
  const start = new Date();
  start.setDate(start.getDate() - days + 1);
  return [start, end];
}
/** 管理员请求诊断页面；错误通知由现有请求层负责，页面只恢复状态。 */
export default {
  components: { PageHeader, AppPagination, ElConfigProvider, ElDatePicker },
  data: () => ({
    query: initialQuery(),
    result: { list: [], total: 0 },
    loading: false,
    failed: false,
    expandedId: null,
    elementLocale: zhCn,
    dateShortcuts: [
      { text: "当天", value: () => recentRange(1) },
      { text: "近一周", value: () => recentRange(7) },
      { text: "近一月", value: () => recentRange(30) },
    ],
    detailLoading: false,
    detail: "",
    sequence: 0,
  }),
  computed: {
    /** 日期组件映射既有起止字段，保持接口契约。 */
    dateRange: {
      get() {
        return [this.query.startDate, this.query.endDate];
      },
      set(value) {
        if (value?.length === 2) {
          this.query.startDate = value[0];
          this.query.endDate = value[1];
        }
      },
    },
  },
  mounted() {
    this.load();
  },
  methods: {
    /** 只应用最近一次查询，防止快速切换条件时旧响应覆盖新列表。 */
    async load() {
      const current = ++this.sequence;
      this.loading = true;
      this.failed = false;
      this.expandedId = null;
      try {
        const result = await requestLogApi.page(this.query);
        if (current === this.sequence) this.result = result;
      } catch {
        if (current === this.sequence) {
          this.failed = true;
          this.result = { list: [], total: 0 };
        }
      } finally {
        if (current === this.sequence) this.loading = false;
      }
    },
    /** 新条件从第一页查询。 */
    search() {
      this.query.pageNo = 1;
      this.load();
    },
    /** 恢复当天条件。 */
    reset() {
      this.query = initialQuery();
      this.load();
    },
    /** 按稳定时间与ID排序翻页。 */
    changePage(pageNo, pageSize = this.query.pageSize) {
      this.query.pageNo = pageNo;
      this.query.pageSize = pageSize;
      this.load();
    },
    /** 按需读取原始详情，文本插值展示，禁止执行日志中的HTML。 */
    async showDetail(row) {
      if (this.expandedId === row.log_id) {
        this.expandedId = null;
        return;
      }
      this.expandedId = row.log_id;
      this.detailLoading = true;
      this.detail = "";
      try {
        const result = await requestLogApi.detail(row.log_id);
        if (this.expandedId === row.log_id)
          this.detail = JSON.stringify(JSON.parse(result.payload), null, 2);
      } catch {
        if (this.expandedId === row.log_id)
          this.detail = "详情加载失败，请收起后重试。";
      } finally {
        if (this.expandedId === row.log_id) this.detailLoading = false;
      }
    },
    /** 用现有业务名称展示后端，保留未知实例的原始名称。 */
    serviceName(value) {
      return (
        {
          InsuranceSystem: "车险后端",
          InsuranceSystemSaas: "SaaS 后端",
          InsuranceSystemMonitor: "监控后端",
          MaintenanceCoordinator: "维护协调器",
        }[value] || value
      );
    },
    /** 与审计表格一致呈现时间，原值无法解析时保留文本。 */
    dateTime(value) {
      if (!value) return "—";
      return String(value)
        .replace("T", " ")
        .replace(/\.\d+(Z)?$/, "");
    },
  },
};
</script>
<style scoped>
.request-filters {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 18px 0;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border);
}
.request-filters .field {
  min-width: 0;
}
.request-filters .layui-input,
.request-filters .layui-select {
  width: 100%;
  height: 40px;
}
.date-field,
.wide-field {
  grid-column: span 2;
}
.date-field :deep(.monitor-date-range) {
  width: 100%;
  --el-date-editor-width: 100%;
}
.filter-actions {
  grid-column: 1/-1;
  justify-content: flex-end;
}
.request-table {
  min-width: 1000px;
}
.request-uri {
  max-width: 280px;
  overflow-wrap: anywhere;
}
.request-method {
  display: block;
  color: var(--muted);
  font-size: 12px;
}
.request-detail {
  padding: 16px;
  background: var(--surface-soft);
  border: 1px solid var(--border);
  border-radius: 8px;
}
.request-detail h3 {
  margin: 0 0 6px;
  font-size: 14px;
}
.request-detail pre {
  max-height: 360px;
  margin: 12px 0 0;
  padding: 12px;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text);
  font: 12px/1.6 Consolas, monospace;
}
@media (max-width: 1100px) {
  .request-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .request-filters {
    grid-template-columns: minmax(0, 1fr);
  }
  .date-field,
  .wide-field {
    grid-column: auto;
  }
  .filter-actions .layui-btn {
    flex: 1;
  }
}
</style>
