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
  data() {
    return {
      query: { pageNum: 1, pageSize: 10, transactionNo: '', direction: '', transactionType: '', dateRange: defaultMonthRange() },
      rows: [],
      total: 0
    };
  },
  created() {
    this.loadData();
  },
  methods: {
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
    async loadData() {
      const response = await getWalletTransactions({ ...this.query, ...rangeParams(this.query.dateRange), dateRange: undefined });
      this.rows = response.data.table;
      this.total = Number(response.data.total || 0);
    },
    search() {
      this.query.pageNum = 1;
      this.loadData();
    },
    resetQuery() {
      this.query = { ...this.query, pageNum: 1, transactionNo: '', direction: '', transactionType: '', dateRange: defaultMonthRange() };
      this.loadData();
    },
    changePage(pageNum) {
      this.query.pageNum = pageNum;
      this.loadData();
    },
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
