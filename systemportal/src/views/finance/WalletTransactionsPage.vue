<template>
  <section>
    <div class="section-title">
      <div>
        <h1>资金明细</h1>
        <p>记录企业余额每一次入账和出账</p>
      </div>
    </div>
    <article class="portal-card panel">
      <div class="filters">
        <input v-model="query.transactionNo" class="layui-input" placeholder="流水号" @keyup.enter="search" />
        <select v-model="query.direction" class="layui-select">
          <option value="">全部方向</option>
          <option value="IN">入账</option>
          <option value="OUT">出账</option>
        </select>
        <select v-model="query.transactionType" class="layui-select">
          <option value="">全部类型</option>
          <option value="RECHARGE">充值</option>
          <option value="BUY_PLAN">购买套餐</option>
          <option value="RENEW_PLAN">续费</option>
          <option value="AUTO_RENEW">自动续费</option>
          <option value="CHANGE_PLAN">套餐变更</option>
        </select>
        <LayDatePicker v-model="query.dateRange" range placeholder="请选择交易时间范围" />
        <button class="layui-btn portal-btn portal-btn-primary" @click="search">查询</button>
        <button class="layui-btn layui-btn-primary portal-btn" @click="resetQuery">重置</button>
      </div>
      <div class="data-table-wrap">
        <table class="layui-table portal-table">
          <thead>
            <tr>
              <th>流水号</th>
              <th>方向</th>
              <th>类型</th>
              <th>金额</th>
              <th>变动前</th>
              <th>变动后</th>
              <th>说明</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in rows" :key="item.id">
              <td>{{ item.transactionNo }}</td>
              <td><span class="portal-tag" :class="{ warn: item.direction === 'OUT' }">{{ item.direction === 'IN' ? '入账' : '出账' }}</span></td>
              <td>{{ typeName(item.transactionType) }}</td>
              <td>¥{{ item.amount }}</td>
              <td>¥{{ item.balanceBefore }}</td>
              <td>¥{{ item.balanceAfter }}</td>
              <td>{{ item.remark }}</td>
              <td>{{ item.createdAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <LayPagination :total="total" :page-num="query.pageNum" :page-size="query.pageSize" @change="changePage" @size-change="changePageSize" />
    </article>
  </section>
</template>

<script>
import { getWalletTransactions } from '@/api/portal';
import LayDatePicker from '@/components/LayDatePicker.vue';
import LayPagination from '@/components/LayPagination.vue';
import { defaultMonthRange, rangeParams } from '@/utils/dateRange';

export default {
  name: 'WalletTransactionsPage',
  components: { LayDatePicker, LayPagination },
  /**
   * 保存流水号、收支方向、业务类型和日期范围等筛选条件，以及企业钱包流水分页结果。
   */
  data() {
    return {
      query: { pageNum: 1, pageSize: 10, transactionNo: '', direction: '', transactionType: '', dateRange: defaultMonthRange() },
      rows: [],
      total: 0
    };
  },
  /**
   * 页面创建后查询当前自然月的企业钱包流水。
   */
  created() {
    this.loadData();
  },
  methods: {
    /**
     * 将钱包流水业务类型映射为充值、套餐购买、续费、退款等可读名称，未知枚举原样显示。
     */
    typeName(type) {
      return {
        RECHARGE: '充值',
        BUY_PLAN: '购买套餐',
        RENEW_PLAN: '续费',
        AUTO_RENEW: '自动续费',
        CHANGE_PLAN: '套餐变更',
        REFUND: '退款',
        ADJUST: '调整'
      }[type] || type;
    },
    /**
     * 将页面日期范围转换为接口起止时间后查询流水分页，并同步服务端总记录数。
     */
    async loadData() {
      const response = await getWalletTransactions({ ...this.query, ...rangeParams(this.query.dateRange), dateRange: undefined });
      this.rows = response.data.table;
      this.total = Number(response.data.total || 0);
    },
    /**
     * 应用流水筛选条件时从第一页开始展示结果。
     */
    search() {
      this.query.pageNum = 1;
      this.loadData();
    },
    /**
     * 清空流水号、收支方向和业务类型，恢复当前月查询范围且保留每页条数。
     */
    resetQuery() {
      this.query = { ...this.query, pageNum: 1, transactionNo: '', direction: '', transactionType: '', dateRange: defaultMonthRange() };
      this.loadData();
    },
    /**
     * 响应流水分页页码变化并重新查询。
     */
    changePage(pageNum) {
      this.query.pageNum = pageNum;
      this.loadData();
    },
    /**
     * 调整每页条数后重置页码并刷新流水列表。
     */
    changePageSize(pageSize) {
      this.query.pageNum = 1;
      this.query.pageSize = pageSize;
      this.loadData();
    }
  }
};
</script>

<style scoped>
.panel {
  padding: 22px;
}

.filters {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.filters .layui-input {
  width: 220px;
}

.filters .layui-select {
  width: 140px;
  height: 38px;
  border: 1px solid var(--portal-border);
  border-radius: 4px;
}
</style>
