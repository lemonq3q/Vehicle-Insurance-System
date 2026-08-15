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
  /**
   * DOM 容器就绪后加载 layui 分页模块，并进行首次渲染。
   */
  mounted() {
    window.layui.use('laypage', () => this.render());
  },
  methods: {
    /**
     * 根据父组件传入的总数、页码和每页条数重建 layui 分页器。首次渲染不发送事件；
     * 用户调整条数时优先发送 size-change，否则只在页码真正变化时发送 change，避免查询循环。
     */
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
