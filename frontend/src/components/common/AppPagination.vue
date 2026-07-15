<template>
  <div class="pagination_container">
    <el-pagination
      size="small"
      :current-page="pageNum"
      :page-size="pageSize"
      :page-sizes="pageSizes"
      :background="true"
      layout="total, prev, pager, next, jumper, sizes"
      :total="total"
      @update:current-page="$emit('update:pageNum', $event)"
      @update:page-size="$emit('update:pageSize', $event)"
      @change="$emit('change')"
    />
  </div>
</template>

<script setup>
/* global defineProps, defineEmits */
defineProps({
  pageNum: { type: Number, required: true },
  pageSize: { type: Number, required: true },
  total: { type: Number, default: 0 },
  pageSizes: { type: Array, default: () => [5, 10, 25, 50, 100] }
});

defineEmits(['update:pageNum', 'update:pageSize', 'change']);
</script>
