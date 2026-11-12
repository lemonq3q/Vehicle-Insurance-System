<template>
  <div v-if="candidates.length" class="ocr-candidates">
    <div v-for="candidate in candidates" :key="candidate.source" class="ocr-candidate">
      <el-tooltip :content="displayValue(candidate.value)" placement="top" :show-after="300">
        <span class="ocr-candidate-value" tabindex="0">{{ displayValue(candidate.value) }}</span>
      </el-tooltip>
      <OcrApplyButton @click="$emit('apply', candidate.value)" />
    </div>
  </div>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue';
import OcrApplyButton from './OcrApplyButton.vue';

/**
 * 在工单原输入框旁仅展示 OCR 候选内容，不显示来源文件名前缀，不维护业务字段。
 * 长识别值限制在输入区宽度内并省略展示，鼠标悬停或键盘聚焦可查看全文，不撑开原表单列宽。
 * 仅在用户点击应用时交给编辑器写入，应用后的候选由编辑器移除。
 */
defineProps({ candidates: { type: Array, default: () => [] } });
defineEmits(['apply']);

/**
 * 将候选日期转换成本地日历日期，其余值保留识别文本；数值零也作为有效候选显示。
 * @param {*} value 编辑器已按表单类型转换的识别候选。
 * @returns {string} 用于人工复核的文字，不改变原候选类型。
 */
function displayValue(value) {
  if (value instanceof Date) {
    return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
  }
  return String(value);
}
</script>

<style scoped>
/* 候选与输入框保留独立行距，识别文本略放大以便人工核对，应用按钮随候选一起下移。 */
.ocr-candidates { width: 100%; max-width: 100%; min-width: 0; margin-top: 8px; font-size: 13px; line-height: 1.5; color: var(--el-color-primary); }
.ocr-candidate { display: flex; align-items: center; width: 100%; min-width: 0; gap: 8px; }
.ocr-candidate-value { flex: 1; min-width: 0; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
