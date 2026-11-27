<template>
  <div>
    <PageHeader
      eyebrow="System Governance"
      title="数据库备份"
      description="按数据所属日期和目录查看全量备份、binlog，选择需要的文件下载。"
    >
      <button
        class="layui-btn monitor-secondary"
        :disabled="loading"
        @click="load()"
      >
        刷新目录
      </button>
    </PageHeader>
    <section class="panel query-panel">
      <form class="backup-date-filter" @submit.prevent="search">
        <label for="backup-data-dates">数据所属日期</label
        ><el-config-provider :locale="elementLocale"
          ><el-date-picker
            id="backup-data-dates"
            v-model="dateRange"
            class="monitor-date-range"
            type="daterange"
            range-separator="到"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY/MM/DD"
            value-format="YYYY-MM-DD"
            :clearable="false"
            :disabled-date="disabledDate"
            placement="bottom-end"
            popper-class="monitor-date-range-popper" /></el-config-provider
        ><button class="layui-btn monitor-primary" :disabled="loading">
          查询</button
        ><button
          class="layui-btn monitor-secondary"
          type="button"
          :disabled="loading"
          @click="resetDates"
        >
          近30天
        </button>
      </form>
      <div class="query-toolbar">
        <h2>备份文件</h2>
        <nav class="backup-path" aria-label="备份目录">
          <button class="path-button" @click="open('')">根目录</button
          ><template v-for="part in breadcrumbs" :key="part.path"
            ><span>/</span
            ><button class="path-button" @click="open(part.path)">
              {{ part.name }}
            </button></template
          >
        </nav>
      </div>
      <div v-if="loading" class="loading-state" role="status">
        正在读取备份目录…
      </div>
      <div v-else-if="failed" class="empty-state">读取失败，请刷新重试。</div>
      <div
        v-else-if="!listing.folders.length && !listing.files.length"
        class="empty-state"
      >
        当前日期范围内没有可确认所属日期的备份文件，可调整日期或返回上级目录。
      </div>
      <div v-else class="table-wrap">
        <table class="layui-table monitor-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>类型</th>
              <th>大小</th>
              <th>数据所属日期</th>
              <th>最后修改时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="folder in listing.folders" :key="folder.path">
              <td>
                <button class="path-button" @click="open(folder.path)">
                  <i
                    class="layui-icon layui-icon-folder"
                    aria-hidden="true"
                  ></i>
                  {{ folder.name }}
                </button>
              </td>
              <td><span class="tag muted">目录</span></td>
              <td>—</td>
              <td>{{ dataDates(folder) }}</td>
              <td>—</td>
              <td>—</td>
            </tr>
            <tr v-for="file in listing.files" :key="file.key">
              <td class="backup-filename">{{ file.name }}</td>
              <td>文件</td>
              <td class="number">{{ size(file.sizeBytes) }}</td>
              <td>{{ dataDates(file) }}</td>
              <td class="number">{{ file.modifiedAt }}</td>
              <td>
                <button
                  class="layui-btn layui-btn-sm monitor-secondary"
                  :disabled="!!downloadingKey"
                  @click="download(file)"
                >
                  {{ downloadingKey === file.key ? "正在获取…" : "下载" }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="listing.nextMarker" class="more-files">
        <button
          class="layui-btn monitor-secondary"
          :disabled="loading"
          @click="load(listing.nextMarker)"
        >
          加载更多
        </button>
      </div>
      <p class="helper">
        按北京时间最近30天的数据所属日期检索，不按上传时间筛选。binlog
        按每日轮转确定的所属日期查询；缺少所属日期的旧文件不参与筛选。进入目录后可下载原文件。
      </p>
    </section>
  </div>
</template>
<script>
import { ElConfigProvider, ElDatePicker } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import "element-plus/es/components/date-picker/style/css";
import PageHeader from "@/components/PageHeader.vue";
import { databaseBackupApi } from "@/api/databaseBackups";
/**
 * 按北京时间计算查询窗口，与服务端所属日期一致；日期运算使用UTC避免浏览器时区偏移。
 */
function localDay(offset = 0) {
  const day = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Shanghai",
  }).format(new Date());
  const date = new Date(day + "T00:00:00Z");
  date.setUTCDate(date.getUTCDate() + offset);
  return date.toISOString().slice(0, 10);
}
/** 复用监控管理样式，目录只读；手动下载原文件，不创建打包任务。 */
export default {
  components: { PageHeader, ElConfigProvider, ElDatePicker },
  data: () => ({
    path: "",
    dateRange: [localDay(-29), localDay()],
    elementLocale: zhCn,
    listing: { folders: [], files: [], nextMarker: "" },
    loading: false,
    failed: false,
    downloadingKey: "",

    sequence: 0,
  }),
  computed: {
    /** 构建可返回上级的目录导航，不把对象路径当HTML执行。 */
    breadcrumbs() {
      return this.path
        .split("/")
        .filter(Boolean)
        .map((name, index, parts) => ({
          name,
          path: parts.slice(0, index + 1).join("/") + "/",
        }));
    },
  },
  mounted() {
    this.load();
  },
  methods: {
    /** UI只允许最近30个自然日，服务端仍独立校验避免绕过。 */
    disabledDate(date) {
      const day = [
        date.getFullYear(),
        String(date.getMonth() + 1).padStart(2, "0"),
        String(date.getDate()).padStart(2, "0"),
      ].join("-");
      return day < localDay(-29) || day > localDay();
    },
    /** 新日期查询清空分页标记，不把旧范围的数据追加到新结果。 */
    search() {
      this.listing = { folders: [], files: [], nextMarker: "" };
      this.load();
    },
    /** 恢复业务允许的完整30天窗口，保留当前目录。 */
    resetDates() {
      this.dateRange = [localDay(-29), localDay()];
      this.search();
    },
    /** 日期来自清单，不能用最后修改时间补齐未知数据归属。 */
    dataDates(item) {
      return item.dataRangeKnown
        ? item.dataStartDate === item.dataEndDate
          ? item.dataStartDate
          : item.dataStartDate + " 至 " + item.dataEndDate
        : "—";
    },
    /** 目录翻页复用索引游标，旧响应不得覆盖新路径。 */
    async load(marker = "") {
      const seq = ++this.sequence;
      this.loading = true;
      this.failed = false;
      try {
        const data = await databaseBackupApi.files({
          path: this.path,
          marker,
          startDate: this.dateRange[0],
          endDate: this.dateRange[1],
        });
        if (seq === this.sequence)
          this.listing = marker
            ? {
                ...data,
                folders: [...this.listing.folders, ...data.folders],
                files: [...this.listing.files, ...data.files],
              }
            : data;
      } catch {
        if (seq === this.sequence) this.failed = true;
      } finally {
        if (seq === this.sequence) this.loading = false;
      }
    },
    /** 切换目录时丢弃旧分页状态。 */
    open(path) {
      this.path = path;
      this.listing = { folders: [], files: [], nextMarker: "" };
      this.load();
    },
    /** 每次手动选择文件时申请新链接，失败由统一请求层提示，不缓存过期URL。 */
    async download(file) {
      this.downloadingKey = file.key;
      try {
        const result = await databaseBackupApi.download(file.key);
        const anchor = document.createElement("a");
        anchor.href = result.downloadUrl;
        anchor.rel = "noopener";
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
      } catch {
        /* 请求层已显示失败，页面保留目录供重试。 */
      } finally {
        this.downloadingKey = "";
      }
    },
    /** 用二进制单位显示容量，不改变后端字节计数。 */
    size(value) {
      let n = Number(value || 0);
      const units = ["B", "KiB", "MiB", "GiB"];
      let i = 0;
      while (n >= 1024 && i < 3) {
        n /= 1024;
        i++;
      }
      return `${n.toFixed(i ? 2 : 0)} ${units[i]}`;
    },
  },
};
</script>
<style scoped>
.backup-date-filter {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 18px;
}
.backup-date-filter label {
  font-weight: 600;
  color: var(--text-secondary);
}

.backup-path {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.path-button {
  border: 0;
  background: transparent;
  color: var(--primary);
  padding: 6px 2px;
  min-height: 36px;
  text-align: left;
}
.path-button:hover {
  text-decoration: underline;
}
.backup-filename {
  overflow-wrap: anywhere;
}
.more-files {
  display: flex;
  justify-content: center;
  padding: 16px;
}
.query-panel > .helper {
  margin-top: 18px;
}
</style>
