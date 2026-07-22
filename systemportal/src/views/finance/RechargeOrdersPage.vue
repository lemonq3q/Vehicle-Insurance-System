<template>
  <section>
    <div class="section-title">
      <div>
        <h1>充值订单</h1>
        <p>追踪企业外部支付换余额的记录</p>
      </div>
    </div>
    <article class="portal-card panel">
      <div class="filters">
        <input v-model="query.rechargeNo" class="layui-input" placeholder="充值订单号" @keyup.enter="search" />
        <select v-model="query.status" class="layui-select">
          <option value="">全部状态</option>
          <option value="1">待支付</option>
          <option value="2">已支付</option>
          <option value="3">已取消</option>
          <option value="4">支付失败</option>
        </select>
        <LayDatePicker v-model="query.dateRange" range placeholder="请选择创建时间范围" />
        <button class="layui-btn portal-btn portal-btn-primary" @click="search">查询</button>
        <button class="layui-btn layui-btn-primary portal-btn" @click="resetQuery">重置</button>
      </div>
      <div class="data-table-wrap">
        <table class="layui-table portal-table">
          <thead>
            <tr>
              <th>订单号</th>
              <th>金额</th>
              <th>支付渠道</th>
              <th>第三方交易号</th>
              <th>状态</th>
              <th>支付时间</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in rows" :key="item.id">
              <td>{{ item.rechargeNo }}</td>
              <td>¥{{ item.amount }}</td>
              <td>{{ channelName(item.payChannel) }}</td>
              <td>{{ item.payTradeNo || '-' }}</td>
              <td><span class="portal-tag" :class="{ warn: item.status === 1 }">{{ statusName('recharge', item.status) }}</span></td>
              <td>{{ item.paidAt || '-' }}</td>
              <td>{{ item.createdAt }}</td>
              <td>
                <router-link class="layui-btn layui-btn-xs layui-btn-primary layui-border-green" :to="{ name: 'finance-recharge-detail', params: { id: item.id } }">
                  {{ item.status === 1 ? '继续支付' : '查看详情' }}
                </router-link>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <LayPagination :total="total" :page-num="query.pageNum" :page-size="query.pageSize" @change="changePage" @size-change="changePageSize" />
    </article>
  </section>
</template>

<script>
import { getRechargeOrders } from '@/api/portal';
import LayDatePicker from '@/components/LayDatePicker.vue';
import LayPagination from '@/components/LayPagination.vue';
import { getStatusName } from '@/utils/portalLabels';
import { defaultMonthRange, rangeParams } from '@/utils/dateRange';

export default {
  name: 'RechargeOrdersPage',
  components: { LayDatePicker, LayPagination },
  data() {
    return {
      query: { pageNum: 1, pageSize: 10, rechargeNo: '', status: '', dateRange: defaultMonthRange() },
      rows: [],
      total: 0
    };
  },
  created() {
    this.loadData();
  },
  methods: {
    statusName: getStatusName,
    channelName(channel) {
      return { WECHAT: '微信', ALIPAY: '支付宝', BANK: '银行转账' }[channel] || channel;
    },
    async loadData() {
      const response = await getRechargeOrders({ ...this.query, ...rangeParams(this.query.dateRange), dateRange: undefined });
      this.rows = response.data.table;
      this.total = Number(response.data.total || 0);
    },
    search() {
      this.query.pageNum = 1;
      this.loadData();
    },
    resetQuery() {
      this.query = { ...this.query, pageNum: 1, rechargeNo: '', status: '', dateRange: defaultMonthRange() };
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
  width: 130px;
  height: 38px;
  border: 1px solid var(--portal-border);
  border-radius: 4px;
}
</style>
