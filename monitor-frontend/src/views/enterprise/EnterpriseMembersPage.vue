<template>
  <div>
    <PageHeader
      eyebrow="Enterprise Detail"
      title="企业成员"
      :description="`${enterprise?.name || '企业'} · 只读成员信息`"
      ><router-link
        class="layui-btn monitor-secondary"
        to="/enterprises"
        >返回企业列表</router-link
      ></PageHeader
    ><EnterpriseNav />
    <section class="panel query-panel">
      <div class="query-toolbar">
        <h2>企业成员</h2>
        <form class="query-filters" @submit.prevent="search">
          <input v-model.trim="query.keyword" class="layui-input" placeholder="姓名、账号或手机" aria-label="姓名、账号或手机" />
          <select v-model="query.roleName" class="layui-select" aria-label="企业角色">
            <option value="">全部角色</option>
            <option value="拥有者">企业拥有者</option>
            <option>管理员</option>
            <option>出单员</option>
          </select>
          <select v-model="query.status" class="layui-select" aria-label="成员状态">
            <option value="">全部状态</option>
            <option value="1">启用</option>
            <option value="0">停用</option>
          </select>
          <button class="layui-btn monitor-primary">查询</button>
          <button type="button" class="layui-btn monitor-secondary" @click="reset">重置</button>
        </form>
      </div>
      <div v-if="loading" class="loading-state">正在加载成员…</div>
      <div v-else-if="!result.list.length" class="empty-state">
        没有符合条件的成员。
      </div>
      <template v-else
        ><div class="table-wrap">
          <table class="layui-table monitor-table">
            <thead>
              <tr>
                <th>成员</th>
                <th>登录账号</th>
                <th>手机号码</th>
                <th>企业角色</th>
                <th>状态</th>
                <th>加入时间</th>
                <th>最后登录</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in result.list" :key="item.id">
                <td>
                  <strong>{{ item.realName }}</strong>
                </td>
                <td>{{ item.username }}</td>
                <td>{{ item.phone }}</td>
                <td>
                  <span :class="['tag', roleTagClass(item.roleName)]">{{ roleDisplayName(item.roleName) }}</span>
                </td>
                <td>
                  <span :class="['tag', item.status ? 'success' : 'muted']">{{
                    item.status ? "启用" : "停用"
                  }}</span>
                </td>
                <td>{{ item.joinedAt }}</td>
                <td>{{ item.lastLoginAt || "从未登录" }}</td>
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
  </div>
</template>
<script>
import PageHeader from "@/components/PageHeader.vue";
import AppPagination from "@/components/AppPagination.vue";
import EnterpriseNav from "./EnterpriseNav.vue";
import { enterpriseApi } from "@/api/monitor";

export default {
  name: "EnterpriseMembersPage",
  components: { PageHeader, AppPagination, EnterpriseNav },
  /**
   * 保存企业摘要、成员筛选条件和分页结果。监控端只查看 SaaS 成员状态，不在此页面修改企业内部角色。
   */
  data: () => ({
    enterprise: null,
    query: { keyword: "", roleName: "", status: "", pageNo: 1, pageSize: 10 },
    result: { list: [], pageNo: 1, pageSize: 10, total: 0 },
    loading: false,
  }),
  computed: {
    /**
     * 将路由企业标识作为字符串传给成员接口，避免超出安全整数范围的 Java Long 在浏览器中丢失精度。
     */
    id() {
      return String(this.$route.params.id || '');
    },
  },
  /**
   * 页面挂载时读取企业标题资料并加载第一页成员。
   */
  mounted() {
    enterpriseApi.detail(this.id).then((data) => {
      this.enterprise = data;
    });
    this.load();
  },
  methods: {
    /**
     * 将后端历史角色文案统一为 SaaS 门户使用的展示名称，仅调整界面文案而不改动查询参数和接口契约。
     * 已经返回“企业拥有者”的新接口可直接透传，其余角色保持原名称，避免影响管理员和出单员的业务语义。
     */
    roleDisplayName(roleName) {
      return roleName === "拥有者" ? "企业拥有者" : roleName;
    },
    /**
     * 复用 SaaS 门户企业成员页面的角色语义配色，确保运营人员在门户与监控系统之间切换时保持一致认知。
     * 同时兼容后端可能返回的“拥有者”和“企业拥有者”文案；未知角色使用中性色，避免扩展枚举被误判为既有权限。
     */
    roleTagClass(roleName) {
      return {
        拥有者: "role-owner",
        企业拥有者: "role-owner",
        管理员: "role-admin",
        出单员: "role-issuer",
      }[roleName] || "role-unknown";
    },
    /**
     * 按关键字、角色、状态及分页条件查询指定企业成员，并可靠恢复加载状态。
     */
    async load() {
      this.loading = true;
      try {
        this.result = await enterpriseApi.members(this.id, this.query);
      } finally {
        this.loading = false;
      }
    },
    /**
     * 应用筛选时回到第一页，避免旧页码超出筛选后的结果范围。
     */
    search() {
      this.query.pageNo = 1;
      this.load();
    },
    /**
     * 清空成员筛选但保留每页条数，并重新查询首页。
     */
    reset() {
      Object.assign(this.query, {
        keyword: "",
        roleName: "",
        status: "",
        pageNo: 1,
      });
      this.load();
    },
    /**
     * 响应分页组件页码变化并加载对应成员页。
     */
    changePage(pageNo) {
      this.query.pageNo = pageNo;
      this.load();
    },
  },
};
</script>
<style scoped>
.member-filter {
  grid-template-columns: repeat(3, 1fr) auto;
}

/* 企业拥有者复用 SaaS 门户的浅蓝底与深蓝文字，表达企业最高管理权限。 */
.tag.role-owner {
  color: #1d4ed8;
  background: #eaf1ff;
}

/* 管理员复用 SaaS 门户的绿色标签，表达企业日常管理权限。 */
.tag.role-admin {
  color: #166534;
  background: #e8f7ee;
}

/* 出单员复用 SaaS 门户的中性灰色，体现业务执行角色并与管理角色区分。 */
.tag.role-issuer {
  color: #475569;
  background: #f1f5f9;
}

.tag.role-unknown {
  color: #475569;
  background: #e2e8f0;
}
</style>
