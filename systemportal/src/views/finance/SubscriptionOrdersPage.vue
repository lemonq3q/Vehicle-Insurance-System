<template>
  <section>
    <div class="section-title">
      <div>
        <h1>订阅订单</h1>
        <p>记录购买、续费、自动续费和套餐变更动作</p>
      </div>
    </div>
    <article class="portal-card panel">
      <div class="filters">
        <input v-model="query.orderNo" class="layui-input" placeholder="订阅订单号" @keyup.enter="loadData" />
        <select v-model="query.orderType" class="layui-select">
          <option value="">全部类型</option>
          <option value="BUY">购买</option>
          <option value="RENEW">续费</option>
          <option value="AUTO_RENEW">自动续费</option>
          <option value="CHANGE_PLAN">套餐变更</option>
        </select>
        <button class="layui-btn portal-btn portal-btn-primary" @click="loadData">查询</button>
      </div>
      <div class="data-table-wrap">
        <table class="layui-table portal-table">
          <thead>
            <tr>
              <th>订单号</th>
              <th>类型</th>
              <th>套餐</th>
              <th>周期数</th>
              <th>套餐金额</th>
              <th>抵扣 / 退款</th>
              <th>应付金额</th>
              <th>实付金额</th>
              <th>自动续费</th>
              <th>状态</th>
              <th>创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in rows" :key="item.id">
              <td>{{ item.orderNo }}</td>
              <td>{{ typeName(item.orderType) }}</td>
              <td>{{ item.planName }}</td>
              <td>{{ item.periodCount || 1 }}</td>
              <td>¥{{ money(item.priceAmount ?? item.payableAmount) }}</td>
              <td>{{ adjustmentText(item) }}</td>
              <td>¥{{ item.payableAmount }}</td>
              <td>¥{{ item.paidAmount }}</td>
              <td>{{ item.autoRenew ? '是' : '否' }}</td>
              <td><span class="portal-tag" :class="{ warn: item.status === 1 }">{{ statusName('order', item.status) }}</span></td>
              <td>{{ item.createdAt }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>

<script>
import { getSubscriptionOrders } from '@/api/portal';
import { getStatusName } from '@/mock/portalMock';

export default {
  name: 'SubscriptionOrdersPage',
  data() {
    return {
      query: { pageNum: 1, pageSize: 20, orderNo: '', orderType: '' },
      rows: []
    };
  },
  created() {
    this.loadData();
  },
  methods: {
    statusName: getStatusName,
    typeName(type) {
      return { BUY: '购买', RENEW: '续费', AUTO_RENEW: '自动续费', CHANGE_PLAN: '套餐变更' }[type] || type;
    },
    money(value) {
      return Number(value || 0).toFixed(2);
    },
    adjustmentText(item) {
      if (Number(item.refundAmount || 0) > 0) return `退款 ¥${this.money(item.refundAmount)}`;
      if (Number(item.creditAmount || 0) > 0) return `抵扣 ¥${this.money(item.creditAmount)}`;
      return '-';
    },
    async loadData() {
      const response = await getSubscriptionOrders(this.query);
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
  width: 140px;
  height: 38px;
  border: 1px solid var(--portal-border);
  border-radius: 4px;
}
</style>
