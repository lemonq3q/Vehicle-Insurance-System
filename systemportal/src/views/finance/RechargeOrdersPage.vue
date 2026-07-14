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
        <input v-model="query.rechargeNo" class="layui-input" placeholder="充值订单号" @keyup.enter="loadData" />
        <select v-model="query.status" class="layui-select">
          <option value="">全部状态</option>
          <option value="1">待支付</option>
          <option value="2">已支付</option>
          <option value="3">已取消</option>
          <option value="4">支付失败</option>
        </select>
        <button class="layui-btn portal-btn portal-btn-primary" @click="loadData">查询</button>
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
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>

<script>
import { getRechargeOrders } from '@/api/portal';
import { getStatusName } from '@/mock/portalMock';

export default {
  name: 'RechargeOrdersPage',
  data() {
    return {
      query: { pageNum: 1, pageSize: 20, rechargeNo: '', status: '' },
      rows: []
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
      const response = await getRechargeOrders(this.query);
      this.rows = response.data.table;
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
