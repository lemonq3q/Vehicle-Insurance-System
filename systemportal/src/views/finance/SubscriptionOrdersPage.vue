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
        <input v-model="query.orderNo" class="layui-input" placeholder="订阅订单号" @keyup.enter="search" />
        <select v-model="query.orderType" class="layui-select">
          <option value="">全部类型</option>
          <option value="BUY">购买</option>
          <option value="RENEW">续费</option>
          <option value="AUTO_RENEW">自动续费</option>
          <option value="CHANGE_PLAN">套餐变更</option>
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
              <th>类型</th>
              <th>套餐</th>
              <th>周期数</th>
              <th>套餐金额</th>
              <th>抵扣 / 退款</th>
              <th>超额工单费</th>
              <th>应付金额</th>
              <th>实付金额</th>
              <th>自动续费</th>
              <th>状态</th>
              <th>失败原因</th>
              <th>创建时间</th>
              <th>操作</th>
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
              <td>{{ workorderOverageText(item) }}</td>
              <td>¥{{ item.payableAmount }}</td>
              <td>¥{{ item.paidAmount }}</td>
              <td>{{ item.autoRenew ? '是' : '否' }}</td>
              <td><span class="portal-tag" :class="{ warn: [1, 6].includes(item.status) }">{{ statusName('order', item.status) }}</span></td>
              <td>{{ item.failureReason || '-' }}</td>
              <td>{{ item.createdAt }}</td>
              <td>
                <router-link class="layui-btn layui-btn-xs layui-btn-primary layui-border-green" :to="{ name: 'finance-order-detail', params: { id: item.id } }">
                  查看详情
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
import { getSubscriptionOrders } from '@/api/portal';
import LayDatePicker from '@/components/LayDatePicker.vue';
import LayPagination from '@/components/LayPagination.vue';
import { getStatusName } from '@/utils/portalLabels';
import { defaultMonthRange, rangeParams } from '@/utils/dateRange';

export default {
  name: 'SubscriptionOrdersPage',
  components: { LayDatePicker, LayPagination },
  /**
   * 维护套餐订单筛选条件和分页结果，默认按当前月查询购买、续费、自动续费及套餐变更记录。
   */
  data() {
    return {
      query: { pageNum: 1, pageSize: 10, orderNo: '', orderType: '', dateRange: defaultMonthRange() },
      rows: [],
      total: 0
    };
  },
  /**
   * 页面创建后加载企业当前月的套餐订单。
   */
  created() {
    this.loadData();
  },
  methods: {
    statusName: getStatusName,
    /**
     * 将套餐订单类型转换为列表文案，未知类型保留服务端值以便识别新增枚举。
     */
    typeName(type) {
      return { BUY: '购买', RENEW: '续费', AUTO_RENEW: '自动续费', CHANGE_PLAN: '套餐变更' }[type] || type;
    },
    /**
     * 将套餐价格、退款、抵扣及超额费用统一格式化为两位小数。
     */
    money(value) {
      return Number(value || 0).toFixed(2);
    },
    /**
     * 套餐变更优先展示退款，其次展示余额抵扣；没有费用调整时显示占位符。
     */
    adjustmentText(item) {
      if (Number(item.refundAmount || 0) > 0) return `退款 ¥${this.money(item.refundAmount)}`;
      if (Number(item.creditAmount || 0) > 0) return `抵扣 ¥${this.money(item.creditAmount)}`;
      return '-';
    },
    /**
     * 仅在套餐变更产生超额工单计费时展示工单数和对应金额，保持无额外费用订单的简洁展示。
     */
    workorderOverageText(item) {
      const count = Number(item.workorderOverageCount || 0);
      if (count <= 0) return '-';
      return `${count} 单 / ¥${this.money(item.workorderOverageAmount)}`;
    },
    /**
     * 展开日期范围为后端起止参数并查询套餐订单分页，页面专用 dateRange 不发送到接口。
     */
    async loadData() {
      const response = await getSubscriptionOrders({ ...this.query, ...rangeParams(this.query.dateRange), dateRange: undefined });
      this.rows = response.data.table;
      this.total = Number(response.data.total || 0);
    },
    /**
     * 应用订单号和订单类型筛选时重置到第一页。
     */
    search() {
      this.query.pageNum = 1;
      this.loadData();
    },
    /**
     * 清空筛选并恢复当前月日期范围，同时保留当前每页条数。
     */
    resetQuery() {
      this.query = { ...this.query, pageNum: 1, orderNo: '', orderType: '', dateRange: defaultMonthRange() };
      this.loadData();
    },
    /**
     * 切换套餐订单页码并刷新列表。
     */
    changePage(pageNum) {
      this.query.pageNum = pageNum;
      this.loadData();
    },
    /**
     * 改变每页条数后回到第一页，避免请求超出新总页数。
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
