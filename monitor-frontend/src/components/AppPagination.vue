<template>
  <div class="pagination">
    <span>第 {{ start }}–{{ end }} 条，共 {{ total }} 条</span>
    <div class="pagination-controls">
      <label class="page-size">每页
        <select :value="pageSize" aria-label="每页数据数量" @change="changeSize($event.target.value)">
          <option v-for="size in [10, 20, 50, 100]" :key="size" :value="size">{{ size }} 条</option>
        </select>
      </label>
      <div class="pages">
        <button class="page-button" aria-label="上一页" :disabled="pageNo <= 1" @click="change(pageNo - 1)">‹</button>
        <button v-for="page in pages" :key="page" class="page-button" :class="{ active: page === pageNo }" :aria-current="page === pageNo ? 'page' : undefined" @click="change(page)">{{ page }}</button>
        <button class="page-button" aria-label="下一页" :disabled="pageNo >= pageCount" @click="change(pageNo + 1)">›</button>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  name: 'AppPagination',
  props: { pageNo: { type: Number, default: 1 }, pageSize: { type: Number, default: 10 }, total: { type: Number, default: 0 } },
  emits: ['change'],
  computed: {
    /**
     * 根据总数和每页条数计算总页数，空列表仍保留一页以稳定分页布局。
     */
    pageCount() { return Math.max(1, Math.ceil(this.total / this.pageSize)); },
    /**
     * 生成最多五个、围绕当前页移动的连续页码窗口，并限制起点不超出合法范围。
     */
    pages() {
      const start = Math.max(1, Math.min(this.pageNo - 2, this.pageCount - 4));
      return Array.from({ length: Math.min(5, this.pageCount) }, (_, index) => start + index);
    },
    /**
     * 计算当前页第一条记录在总结果中的一基序号，空列表返回零。
     */
    start() { return this.total ? (this.pageNo - 1) * this.pageSize + 1 : 0; },
    /**
     * 计算当前页最后一条记录序号，并限制不超过总记录数。
     */
    end() { return Math.min(this.pageNo * this.pageSize, this.total); }
  },
  methods: {
    /**
     * 仅对范围内且不同于当前页的页码发送 change，避免无效请求和重复刷新。
     */
    change(page) { if (page >= 1 && page <= this.pageCount && page !== this.pageNo) this.$emit('change', page, this.pageSize); },
    /**
     * 将原生选择框的字符串转换为接口使用的条数；仅允许接口上限内的固定选项。
     * 条数变化时一次性通知父页面更新页码与条数，从第一页重新查询，避免越界和重复请求。
     */
    changeSize(value) {
      const size = Number(value);
      if ([10, 20, 50, 100].includes(size) && size !== this.pageSize) this.$emit('change', 1, size);
    }
  }
};
</script>
<style scoped>
.pagination { gap: 12px; flex-wrap: wrap; }
.pagination-controls { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.page-size { display: flex; align-items: center; gap: 8px; white-space: nowrap; }
.page-size select { width: auto; min-width: 84px; height: 36px; padding: 0 8px; border: 1px solid var(--border); border-radius: 6px; background: #fff; color: var(--text); cursor: pointer; }
.page-size select:focus-visible, .page-button:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
@media (max-width: 600px) {
  .pagination-controls { gap: 12px; }
  .pagination .pages { flex-wrap: wrap; }
}
</style>
