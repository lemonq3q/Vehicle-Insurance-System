<template>
  <nav ref="container" class="portal-pagination" aria-label="数据分页"></nav>
</template>

<script>
import 'layui';

export default {
  name: 'LayPagination',
  props: {
    total: { type: Number, default: 0 },
    pageNum: { type: Number, default: 1 },
    pageSize: { type: Number, default: 10 },
    limits: { type: Array, default: () => [5, 10, 20, 50] }
  },
  emits: ['change', 'size-change'],
  watch: {
    total: 'render',
    pageNum: 'render',
    pageSize: 'render'
  },
  mounted() {
    window.layui.use('laypage', () => this.render());
  },
  methods: {
    render() {
      if (!this.$refs.container || !window.layui?.laypage) return;
      window.layui.laypage.render({
        elem: this.$refs.container,
        count: this.total,
        limit: this.pageSize,
        limits: this.limits,
        curr: this.pageNum,
        layout: ['prev', 'page', 'next', 'limit'],
        jump: (object, first) => {
          if (first) return;
          if (object.limit !== this.pageSize) {
            this.$emit('size-change', object.limit);
          } else if (object.curr !== this.pageNum) {
            this.$emit('change', object.curr);
          }
        }
      });
    }
  }
};
</script>

<style scoped>
.portal-pagination {
  min-height: 32px;
  margin-top: 16px;
  text-align: right;
}

.portal-pagination :deep(.layui-laypage) {
  margin: 0;
}

.portal-pagination :deep(.layui-laypage-curr .layui-laypage-em) {
  background-color: var(--portal-accent);
}

.portal-pagination :deep(.layui-laypage-limits select:focus) {
  border-color: var(--portal-accent) !important;
}
</style>
