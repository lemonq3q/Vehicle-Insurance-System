<template><teleport to="body"><div v-if="modelValue" class="modal-mask" @mousedown.self="close"><section class="modal-card" role="dialog" aria-modal="true" :aria-labelledby="titleId"><header><h2 :id="titleId">{{title}}</h2><button type="button" class="icon-button" aria-label="关闭" @click="close">×</button></header><div class="modal-body"><slot /></div><footer><button type="button" class="layui-btn monitor-secondary" @click="close">取消</button><button type="button" class="layui-btn" :class="danger?'monitor-danger':'monitor-primary'" :disabled="loading" @click="$emit('confirm')">{{loading?'处理中…':confirmText}}</button></footer></section></div></teleport></template>
<script>
export default {
  name: 'AppModal',
  props: { modelValue: Boolean, title: { type: String, required: true }, confirmText: { type: String, default: '确认' }, loading: Boolean, danger: Boolean },
  emits: ['update:modelValue', 'confirm'],
  /**
   * 为每个弹窗生成独立标题 ID，供 aria-labelledby 建立无冲突的辅助技术关联。
   */
  data: () => ({ titleId: `modal-title-${Math.random().toString(16).slice(2)}` }),
  methods: {
    /**
     * 仅在确认请求未执行时关闭弹窗，避免后台操作仍在进行而界面提前消失。
     */
    close() { if (!this.loading) this.$emit('update:modelValue', false); }
  }
};
</script>
