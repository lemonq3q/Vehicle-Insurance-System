<template>
  <div class="recipient-block">
    <div v-if="showHeading" class="table-heading"><div><h3>{{ title }}</h3><p>仅展示当前推广渠道可触达的信息</p></div></div>
    <div class="table-wrap">
      <table class="layui-table monitor-table recipient-table">
        <thead><tr><th class="check-column"><label v-if="showSelectAll" class="select-all"><input type="checkbox" :checked="allChecked" :disabled="!rows.length" aria-label="全选当前结果" @change="$emit('toggle-all', $event)"> 全选</label></th><th>名称</th><th>电话</th><th>邮箱</th><th>说明</th></tr></thead>
        <tbody><tr v-if="!rows.length"><td colspan="5" class="empty-cell">{{ emptyText }}</td></tr><tr v-for="item in rows" :key="item._key"><td><input type="checkbox" :checked="selectedKeys.includes(item._key)" :disabled="!eligible(item) || (limitReached && !selectedKeys.includes(item._key))" :aria-label="`选择${item.name}`" @change="$emit('toggle-row', item, $event.target.checked)"></td><td>{{ item.name || '—' }}</td><td>{{ item.phone || '—' }}</td><td>{{ item.email || '—' }}</td><td><span v-if="item._valid===false" class="invalid-reason">第 {{ item.rowNumber }} 行：{{ item.reason }}</span><span v-else>{{ item._source === 'EXCEL' ? '本次 Excel' : '系统资料' }}</span></td></tr></tbody>
      </table>
    </div>
  </div>
</template>

<script>
/**
 * 展示推广候选或已选目标的通用表格。
 * 父页面持有跨分页选择状态，本组件只校验当前渠道是否可触达并派发用户的勾选意图。
 */
export default {
  name: 'PromotionRecipientTable',
  props: { title: String, rows: { type: Array, default: () => [] }, selectedKeys: { type: Array, default: () => [] }, channel: String, allChecked: Boolean, limitReached: Boolean, showHeading: { type: Boolean, default: true }, showSelectAll: { type: Boolean, default: true }, emptyText: String },
  emits: ['toggle-all', 'toggle-row'],
  methods: { eligible(row) { return row._valid !== false && Boolean(this.channel === 'PHONE' ? row.phone : row.email); } }
};
</script>

<style scoped>
.table-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:10px}.table-heading h3{margin:0;font-size:16px}.table-heading p{margin:3px 0 0;color:var(--muted);font-size:13px}.recipient-table{min-width:760px}.recipient-table .check-column{width:90px}.select-all{display:inline-flex;align-items:center;gap:6px;cursor:pointer}.empty-cell{padding:34px!important;text-align:center;color:var(--muted)}.invalid-reason{color:var(--danger)}
</style>
