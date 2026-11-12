<template>
  <section v-for="group in visibleGroups" :key="group.type" class="policy-section">
    <div class="policy-section-heading">
      <h3>{{ group.title }}</h3>
    </div>
    <el-form-item v-if="editable" class="policy-upload-field">
      <div class="policy-upload-row">
        <el-upload class="policy-upload" drag action="#" :auto-upload="false" accept=".pdf,.jpg,.jpeg,.png,.webp,.bmp,.tif,.tiff" :limit="group.maxFiles || 1" :multiple="!!group.maxFiles" :file-list="files[group.fileType + 'File'] || []"
          :disabled="!!loading[group.loading]" v-loading="!!loading[group.loading]"
          :on-change="(file, list) => emit('upload', group, file, list)"
          :on-remove="(file, list) => emit('remove', group, file, list)">
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖动文件到这里 或者 <em>点击上传</em></div>
        </el-upload>
        <div class="policy-upload-actions" v-if="ocrPendingCount(group) || hasGroupOcrCandidates(group)">
          <div v-if="ocrPendingCount(group)" class="policy-ocr-loading" role="status" aria-live="polite">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>OCR正在识别{{ ocrPendingCount(group) > 1 ? `（${ocrPendingCount(group)}个文件）` : '' }}，请稍候</span>
          </div>
          <OcrApplyButton v-if="hasGroupOcrCandidates(group)" size="large" @click="emit('apply-all-ocr', group)">全部应用识别结果</OcrApplyButton>
        </div>
      </div>
      <span v-if="group.maxFiles" class="policy-upload-hint">最多上传{{ group.maxFiles }}个文件</span>
    </el-form-item>
    <div v-else class="policy-file">
      <span v-if="hasFiles(group)" class="form_text workorder_detail_form_title file_download_text no_select" @click="emit('download', group.fileType)">点击下载{{ group.title }}</span>
      <span v-else>暂无文件</span>
    </div>
    <el-row v-if="editable" :gutter="20">
      <el-col v-for="field in policyFields(group)" :key="field.key" :xs="24" :sm="12" :lg="8">
        <el-form-item :label="field.label" :prop="field.key">
          <el-date-picker v-if="field.kind === 'date'" :model-value="model[field.key]" type="date"
            format="YYYY-MM-DD" placeholder="请选择日期" style="width:100%" @update:model-value="value => emit('change', field.key, value)" />
          <el-input v-else :model-value="model[field.key]" :maxlength="field.max" :placeholder="'请输入' + field.label"
            @update:model-value="value => emit('change', field.key, value)">
            <template v-if="field.kind === 'money'" #suffix>元</template>
          </el-input>
          <OcrCandidate v-if="ocrCandidates(field.key).length" :candidates="ocrCandidates(field.key)"
            @apply="value => emit('apply-ocr', field.key, value)" />
        </el-form-item>
      </el-col>
    </el-row>
    <div v-else class="policy-details">
      <div v-for="field in policyFields(group)" :key="field.key" class="policy-detail-field">
        <span class="policy-detail-label">{{ field.label }}：</span><span>{{ display(field) }}</span>
      </div>
    </div>
  </section>
</template>

<script setup>
/* global defineProps, defineEmits */
import { computed } from 'vue';
import { Loading, UploadFilled } from '@element-plus/icons-vue';
import OcrCandidate from './OcrCandidate.vue';
import OcrApplyButton from './OcrApplyButton.vue';
import { POLICY_GROUPS, policyFields, hasPolicy } from '@/utils/workorderPolicy';
import { formatMoney } from '@/utils/money';
import { formatSecondTimestamp } from '@/utils/time';

/**
 * 同一组字段用于保单编辑和只读详情，避免报价中再次维护保单资料。
 * 模型由父工单表单持有；所有更改和上传通过事件交给既有请求、鉴权及文件流程。
 */
const props = defineProps({
  model: { type: Object, required: true }, editable: Boolean,
  flags: { type: Object, default: () => ({}) }, files: { type: Object, default: () => ({}) },
  urls: { type: Object, default: () => ({}) }, loading: { type: Object, default: () => ({}) },
  ocrLoading: { type: Object, default: () => ({}) },
  ocrCandidates: { type: Function, default: () => [] }
});
const emit = defineEmits(['change', 'upload', 'remove', 'download', 'apply-ocr', 'apply-all-ocr']);
const visibleGroups = computed(() => POLICY_GROUPS.filter(group => props.editable ? props.flags[group.flag] : hasPolicy(props.model, group)));

/**
 * 根据本险种各字段当前剩余的候选决定批量按钮是否显示，与文件上传位置无关。
 * 自动回填的字段没有候选；逐项或批量确认后候选清除，按钮随响应式状态立即消失。
 * @param {object} group 当前保单区域的字段配置。
 * @returns {boolean} 是否还有需要人工确认的识别值。
 */
function hasGroupOcrCandidates(group) {
  return policyFields(group).some(field => props.ocrCandidates(field.key).length > 0);
}

/**
 * 统计当前上传区域仍在识别的文件数，上传完成后即允许用户继续操作上传组件。
 * 一份混合保单虽可能回填三险，进度仍归属原始上传区域，直至其请求结束或附件移除。
 * @param {object} group 当前上传区域的保单配置。
 * @returns {number} 该区域正在处理的识别请求数。
 */
function ocrPendingCount(group) {
  return Object.values(props.ocrLoading).filter(loading => loading === group.loading).length;
}

/**
 * 只读文件区兼容单份保单 URL 与非车险多份单证 URL。
 * 空数组不能作为已上传文件展示，历史旧类型由父组件合并到同一数组。
 * @param {object} group 当前保单组。
 * @returns {boolean} 是否至少存在一份可下载文件。
 */
function hasFiles(group) {
  const value = props.urls[group.fileType];
  return Array.isArray(value) ? value.length > 0 : !!value;
}

/**
 * 保单起期与终期只展示本地年月日，金额保持原有格式；缺失字段留空，不生成 1970 年日期。
 * @param {object} field 字段配置。
 * @returns {string} 用于详情的文本。
 */
function display(field) {
  const value = props.model[field.key];
  if (value === null || value === undefined || value === '') return '';
  if (field.kind === 'money') return formatMoney(value) + ' 元';
  return field.kind === 'date' ? formatSecondTimestamp(value, 'YYYY-MM-DD') : value;
}
</script>

<style scoped>
.policy-section { padding: 20px 0; border-bottom: 1px solid var(--insurance-border, #dcdfe6); text-align:left; }
/* 批量应用放在上传组件右侧，随文件列表的实际高度底部对齐；窄屏允许换行。 */
.policy-section-heading { display:flex; align-items:center; flex-wrap:wrap; gap:8px 16px; margin-bottom:18px; }
.policy-section h3 { margin:0; font-size:16px; }
.policy-file { margin-bottom:16px; color:#909399; }
/* 只读保单字段沿用详情的标签、正文和行距，不使用表单网格的额外内边距。 */
.policy-details { display:flex; flex-wrap:wrap; align-items:baseline; gap:20px 30px; color:var(--insurance-text); }
.policy-detail-field { display:flex; min-width:0; align-items:baseline; line-height:20px; overflow-wrap:anywhere; }
.policy-detail-label { color:var(--insurance-secondary); margin-right:20px; white-space:nowrap; }
.policy-upload-field :deep(.el-form-item__content) { min-width: 0; }
.policy-upload-row { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 12px 20px; width: 100%; }
.policy-upload-actions { display:flex; flex-direction:column; align-items:flex-start; gap:8px; flex:0 0 auto; }
.policy-ocr-loading { display: inline-flex; align-items: center; gap: 6px; min-height: 32px; color: var(--el-color-primary); font-size: 13px; white-space: nowrap; }
.policy-ocr-loading .el-icon { font-size: 17px; }
.policy-upload-hint { display: block; width: 100%; color: #909399; font-size: 12px; }
.policy-upload { flex: 1 1 320px; width: 100%; max-width: 500px; min-width: 0; overflow: hidden; }
.policy-upload :deep(.el-upload),
.policy-upload :deep(.el-upload-dragger) { width: 100%; }
.policy-upload :deep(.el-upload-list) { width: 100%; max-width: 100%; min-width: 0; }
.policy-upload :deep(.el-upload-list__item) { width: 100%; max-width: 100%; min-width: 0; overflow: hidden; }
.policy-upload :deep(.el-upload-list__item) { height: 24px; line-height: 24px; margin-top: 2px; }
/* 全局上传列表将内部 div 居中；此处要让文件名占满受限宽度，文字才能在图标后正确省略。 */
.policy-upload :deep(.el-upload-list__item-info) { width: calc(100% - 30px); min-width: 0; overflow: hidden; align-items: stretch; }
.policy-upload :deep(.el-upload-list__item-name) { width: 100%; min-width: 0; display: flex; align-items: center; line-height: 22px; }
.policy-upload :deep(.el-upload-list__item-file-name) { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>

