<template>
  <el-button
    type="primary"
    link
    class="ocr-apply-button"
    :class="{ 'ocr-apply-button--large': size === 'large' }"
    @click="$emit('click', $event)"
  ><slot>应用</slot></el-button>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue';

/**
 * OCR逐项和批量确认共用紧凑操作按钮，通过减少内边距缩小按钮，保留清晰字号和悬停反馈。
 * 仅向编辑器传递点击事件，不维护识别或表单状态；large用于标题旁的全部应用操作。
 */
defineProps({ size: { type: String, default: 'small' } });
defineEmits(['click']);
</script>

<style scoped>
.ocr-apply-button.el-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-sizing: border-box;
  height: auto;
  min-height: 24px;
  min-width: 0;
  margin: 0;
  padding: 2px 6px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--el-color-primary);
  font-size: 14px;
  line-height: 16px;
  text-align: center;
  vertical-align: middle;
  transition: background-color .16s ease;
}
.ocr-apply-button.el-button.ocr-apply-button--large {
  min-height: 28px;
  padding: 4px 8px;
  font-size: 14px;
  line-height: 18px;
}
/*
 * 明确覆盖系统主按钮的悬停底色与链接按钮文字色，确保深色背景上始终显示白字。
 * 键盘聚焦采用同一反馈并保留轮廓，不靠改变尺寸形成交互效果。
 */
.ocr-apply-button.el-button:not(.is-disabled):hover,
.ocr-apply-button.el-button:not(.is-disabled):focus-visible {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
}
.ocr-apply-button.el-button:not(.is-disabled):active {
  background: var(--el-color-primary-dark-2);
  color: #fff;
}
.ocr-apply-button.el-button:focus-visible { outline: 2px solid var(--el-color-primary-light-5); outline-offset: 2px; }
.ocr-apply-button :deep(span) { display: inline-flex; align-items: center; justify-content: center; line-height: inherit; }
</style>
