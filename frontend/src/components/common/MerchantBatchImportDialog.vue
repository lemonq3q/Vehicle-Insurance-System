<template>
  <el-dialog
    v-model="visible"
    :title="`${businessLabel}批量导入`"
    width="760px"
    class="batch-import-dialog"
    :close-on-click-modal="false"
    @closed="resetDialog"
  >
    <div class="dialog-content">
    <template v-if="!resultVisible">
      <div class="template-panel">
        <div>
          <div class="panel-title">使用官方模板填写数据</div>
          <div class="panel-help">选择文件后直接导入，系统会自动校验模板结构和每行业务数据。</div>
        </div>
        <el-button plain type="primary" @click="downloadTemplate">下载规范模板</el-button>
      </div>

      <el-upload
        ref="uploadRef"
        drag
        action="#"
        accept=".xlsx"
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        :on-exceed="handleExceed"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖放 Excel 到这里，或 <em>点击选择文件</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx，单个文件最大 20MB，请勿修改模板 Sheet、表头名称或顺序。</div>
        </template>
      </el-upload>
    </template>

    <div v-else class="result-panel" aria-live="polite">
      <div class="result-summary">
        <div class="summary-item"><span>读取数据</span><strong>{{ result.totalRows }}</strong></div>
        <div class="summary-item success"><span>成功导入</span><strong>{{ result.successRows }}</strong></div>
        <div class="summary-item danger"><span>未导入</span><strong>{{ result.failureRows }}</strong></div>
      </div>
      <el-alert
        :title="result.failureRows ? '部分数据未导入，请根据下方原因修正后重新上传。' : '全部数据已成功导入。'"
        :type="result.failureRows ? 'warning' : 'success'"
        :closable="false"
        show-icon
      />
      <el-table v-if="result.failureRows" :data="result.failures" border stripe max-height="340" class="failure-table">
        <el-table-column prop="sheetName" label="Sheet" width="110" align="center" />
        <el-table-column prop="rowNumber" label="行号" width="72" align="center" />
        <el-table-column label="原始数据" min-width="230" align="center" show-overflow-tooltip>
          <template #default="scope">{{ formatRowData(scope.row.rowData) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="未导入原因" min-width="250" align="center" />
      </el-table>
    </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button v-if="!resultVisible" @click="visible = false">取消</el-button>
        <el-button v-if="!resultVisible" type="primary" :loading="importing" :disabled="!selectedFile" @click="confirmImport">
          开始导入
        </el-button>
        <el-button v-if="resultVisible" @click="visible = false">关闭</el-button>
        <el-button v-if="resultVisible && result.failureRows" type="primary" @click="startAgain">重新选择文件</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
/* global defineProps, defineEmits, defineExpose */
import { computed, reactive, ref } from 'vue';
import { UploadFilled } from '@element-plus/icons-vue';
import { getBatchImportTemplates, submitBatchImport } from '@/api/batchImport';
import { downloadByUrl } from '@/api/file';
import Message from '@/utils/message';

const props = defineProps({
  businessType: { type: String, required: true, validator: value => ['upstream', 'downstream'].includes(value) }
});
const emit = defineEmits(['success']);

const visible = ref(false);
const resultVisible = ref(false);
const selectedFile = ref(null);
const uploadRef = ref();
const importing = ref(false);
const result = reactive({ totalRows: 0, successRows: 0, failureRows: 0, failures: [] });
const templates = reactive({ upstreamUrl: '', downstreamUrl: '' });
const businessLabel = computed(() => props.businessType === 'upstream' ? '上游机构' : '下游机构及商户人员');

/**
 * 由列表页公开调用，打开弹窗时读取最新模板地址，使运维替换配置后无需刷新整个页面。
 */
async function open() {
  resetDialog();
  visible.value = true;
  try {
    const response = await getBatchImportTemplates();
    if (response?.data?.code === 200) Object.assign(templates, response.data.data || {});
  } catch (error) {
    Message.warning('暂时无法获取模板下载地址');
  }
}

/** 文件改变后清除旧结果，用户可直接发起包含模板校验的数据导入。 */
function handleFileChange(uploadFile) {
  const raw = uploadFile.raw;
  if (!raw?.name?.toLowerCase().endsWith('.xlsx')) {
    Message.warning('请选择 .xlsx 格式的文件');
    uploadRef.value?.clearFiles();
    selectedFile.value = null;
    return;
  }
  if (raw.size > 20 * 1024 * 1024) {
    Message.warning('文件不能超过 20MB');
    uploadRef.value?.clearFiles();
    selectedFile.value = null;
    return;
  }
  selectedFile.value = raw;
  resultVisible.value = false;
}

/** 移除文件后恢复不可导入状态。 */
function handleFileRemove() {
  selectedFile.value = null;
  resultVisible.value = false;
}

/** 单文件模式下明确提示用户先移除当前文件。 */
function handleExceed() {
  Message.warning('一次只能上传一个文件，请先移除当前文件');
}

/**
 * 一次提交完成模板结构校验、逐行业务校验和批量入库。模板不合格时后端返回业务警告且不会写库；
 * 校验通过时展示成功统计和全部未导入行，避免用户重复点击独立校验步骤。
 */
async function confirmImport() {
  if (!selectedFile.value) return;
  importing.value = true;
  try {
    const response = await submitBatchImport(props.businessType, selectedFile.value);
    if (response?.data?.code !== 200) return;
    Object.assign(result, response.data.data || {});
    result.failures = response.data.data?.failures || [];
    resultVisible.value = true;
    if (result.successRows > 0) emit('success', result);
  } finally {
    importing.value = false;
  }
}

/** 使用项目已有 OSS 远程文件下载逻辑保存对应业务模板。 */
function downloadTemplate() {
  const url = props.businessType === 'upstream' ? templates.upstreamUrl : templates.downstreamUrl;
  if (!url) return Message.warning('模板下载地址尚未配置');
  const name = props.businessType === 'upstream' ? '车险系统_上游批量导入模板.xlsx' : '车险系统_下游及商户人员批量导入模板.xlsx';
  downloadByUrl(url, name);
}

/** 把失败行字段压缩成易扫描文本，完整内容仍可通过表格 tooltip 查看。 */
function formatRowData(rowData) {
  return Object.entries(rowData || {}).filter(([, value]) => value).map(([key, value]) => `${key}：${value}`).join('；');
}

/** 保持弹窗开启并清理旧结果，让用户选择修正后的文件再次导入。 */
function startAgain() {
  resetDialog();
}

/** 关闭或重新开始时彻底清除文件、异步状态和结果，避免跨次导入污染。 */
function resetDialog() {
  resultVisible.value = false;
  selectedFile.value = null;
  importing.value = false;
  Object.assign(result, { totalRows: 0, successRows: 0, failureRows: 0, failures: [] });
  uploadRef.value?.clearFiles();
}

defineExpose({ open });
</script>

<style scoped>
.dialog-content { width: 100%; text-align: center; }
:global(.batch-import-dialog .el-dialog__header) { padding-right: var(--el-dialog-padding-primary); text-align: center; }
:global(.batch-import-dialog .el-dialog__title) { display: inline-block; width: 100%; text-align: center; }
.template-panel { display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 12px; padding: 16px; margin-bottom: 16px; border: 1px solid #d9e5f2; border-radius: 8px; background: #f5f9fd; text-align: center; }
.panel-title { color: #24364b; font-size: 15px; font-weight: 600; }
.panel-help { margin-top: 5px; color: #66758a; font-size: 13px; line-height: 1.5; }
.upload-icon { color: var(--el-color-primary); font-size: 48px; }
.result-panel { text-align: center; }
.result-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 16px; }
.summary-item { padding: 14px 16px; border: 1px solid #e2e7ed; border-radius: 8px; background: #fafbfc; text-align: center; }
.summary-item span { display: block; color: #66758a; font-size: 13px; }
.summary-item strong { display: block; margin-top: 6px; color: #26374a; font-size: 26px; font-variant-numeric: tabular-nums; }
.summary-item.success strong { color: var(--el-color-success); }
.summary-item.danger strong { color: var(--el-color-danger); }
.failure-table { margin: 16px auto 0; }
.dialog-footer { display: flex; justify-content: center; gap: 8px; }
:deep(.el-upload) { display: flex; justify-content: center; width: 100%; }
:deep(.el-upload-dragger) { display: flex; align-items: center; justify-content: center; flex-direction: column; width: 100%; text-align: center; }
:deep(.el-upload-list) { max-width: 620px; margin-right: auto; margin-left: auto; text-align: center; }
:deep(.el-upload-list__item-name) { justify-content: center; }
:deep(.el-upload__tip) { text-align: center; }
:deep(.el-alert__content) { width: 100%; text-align: center; }
@media (max-width: 768px) {
  .result-summary { grid-template-columns: 1fr; }
}
</style>
