<template>
  <div>
    <PageHeader title="信息推广" subtitle="选择电话或邮箱渠道，通过系统资料或 Excel 名单执行模拟群发。" />
    <section class="panel promotion-builder">
      <div class="step"><span>1</span><div><h2>选择推广渠道</h2></div></div>
      <div class="channel-grid">
        <label :class="['choice-card',{active:form.channel==='PHONE'}]"><input v-model="form.channel" type="radio" value="PHONE" @change="changeChannel"><i class="layui-icon layui-icon-cellphone"></i><strong>电话推广</strong><small>仅选择已录入有效电话的目标</small></label>
        <label :class="['choice-card',{active:form.channel==='EMAIL'}]"><input v-model="form.channel" type="radio" value="EMAIL" @change="changeChannel"><i class="layui-icon layui-icon-email"></i><strong>邮件推广</strong><small>仅选择已录入有效邮箱的目标</small></label>
      </div>

      <div class="step"><span>2</span><div><h2>选择推广对象</h2></div></div>
      <div class="source-tabs"><button type="button" :class="{active:source==='SYSTEM'}" @click="switchSource('SYSTEM')">系统已录入信息</button><button type="button" :class="{active:source==='EXCEL'}" @click="switchSource('EXCEL')">导入 Excel</button></div>

      <div v-if="source==='EXCEL'" class="source-panel">
        <div class="upload-toolbar"><label class="layui-btn monitor-secondary file-button"><i class="layui-icon layui-icon-upload-drag"></i>{{importing?'正在解析':'选择推广 Excel'}}<input type="file" accept=".xlsx" :disabled="importing" @change="previewImport"></label><span v-if="importResult">导入 {{importResult.totalRows}} 行，成功 {{importResult.successRows}} 行，失败 {{importResult.failureRows}} 行</span><span v-else>文件结构与“信息录入”页面下载的官方模板一致</span></div>
        <RecipientTable title="Excel 导入结果" :rows="importPageRows" :selected-keys="selectedKeys" :channel="form.channel" :all-checked="excelAllSelected" :limit-reached="limitReached" empty-text="请先导入 Excel 文件" @toggle-all="toggleExcelAll" @toggle-row="toggleRow" />
        <AppPagination :page-no="importPageNo" :page-size="pageSize" :total="importRows.length" @change="importPageNo=$event" />
      </div>

      <div v-else class="source-panel">
        <form class="system-filters" @submit.prevent="search"><input v-model.trim="query.keyword" class="layui-input" aria-label="名称电话或邮箱" placeholder="名称、电话或邮箱"><select v-model="query.sourceType" class="layui-select" aria-label="数据来源"><option value="">全部来源</option><option value="MANUAL">手工录入</option><option value="EXCEL_IMPORT">Excel 导入</option></select><button class="layui-btn monitor-primary" :disabled="loading">查询</button><button class="layui-btn monitor-secondary" type="button" @click="reset">重置</button><button class="layui-btn monitor-secondary unconditional-button" type="button" :disabled="selectingAll||limitReached" @click="selectUnconditional">{{selectingAll?'选择中…':'无条件全选'}}</button></form>
        <div v-if="loading" class="loading-state">正在查询推广对象…</div>
        <template v-else><RecipientTable title="搜索结果" :rows="result.list" :selected-keys="selectedKeys" :channel="form.channel" :all-checked="searchAllSelected" :limit-reached="limitReached" empty-text="没有符合当前渠道和搜索条件的推广目标" @toggle-all="toggleSearchAll" @toggle-row="toggleRow" /><AppPagination :page-no="result.pageNo" :page-size="result.pageSize" :total="result.total" @change="changePage" /></template>
      </div>

      <section class="selected-section" aria-live="polite">
        <div class="table-heading"><div><h3>已勾选信息</h3></div><strong :class="{limit:limitReached}">已选 {{selectedItems.length}} / {{maxTargets}}</strong></div>
        <p v-if="limitReached" class="limit-notice"><i class="layui-icon layui-icon-notice"></i>勾选数量已达上限，无法继续添加推广对象。</p>
        <RecipientTable :show-heading="false" :show-select-all="false" :rows="selectedPageRows" :selected-keys="selectedKeys" :channel="form.channel" :limit-reached="limitReached" empty-text="尚未勾选推广对象" @toggle-row="toggleRow" />
        <AppPagination :page-no="selectedPageNo" :page-size="pageSize" :total="selectedItems.length" @change="selectedPageNo=$event" />
      </section>

      <div class="step"><span>3</span><div><h2>填写推广信息</h2></div></div>
      <div class="message-field"><label for="promotion-content">自定义推广信息 <b>*</b></label><textarea id="promotion-content" v-model.trim="form.content" class="layui-textarea" maxlength="2000" placeholder="请输入需要群发的推广内容"></textarea><small>{{form.content.length}} / 2000</small></div>
      <div class="action-panel"><button class="layui-btn monitor-primary" type="button" :disabled="sending||!selectedItems.length||!form.content" @click="openConfirmation">{{sending?'推广中':'开始推广'}}</button></div>
    </section>

    <AppModal v-model="confirmVisible" title="确认执行推广？" confirm-text="确认推广" :loading="sending" @confirm="send"><p>本次将通过 <strong>{{form.channel==='PHONE'?'电话':'邮件'}}</strong> mock 渠道发送给 <strong>{{selectedItems.length}}</strong> 个目标。</p></AppModal>
    <AppModal v-model="resultVisible" title="模拟推广完成" confirm-text="知道了" @confirm="resultVisible=false"><div v-if="sendResult" class="send-result"><i class="layui-icon layui-icon-ok-circle"></i><strong>成功模拟发送 {{sendResult.successCount}} 条</strong><p>批次号：{{sendResult.batchNo}}</p><p>未连接真实电话或邮件 API，没有实际消息发出。</p></div></AppModal>
    <AppToast :message="toastMessage" :type="toastType" />
  </div>
</template>

<script>
import PageHeader from '@/components/PageHeader.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppModal from '@/components/AppModal.vue';
import AppToast from '@/components/AppToast.vue';
import feedback from '@/mixins/feedback';
import { promotionApi } from '@/api/monitor';
import RecipientTable from './PromotionRecipientTable.vue';

const MAX_TARGETS = 6000;
const PAGE_SIZE = 10;

export default {
  name: 'PromotionSendPage',
  components: { PageHeader, AppPagination, AppModal, AppToast, RecipientTable },
  mixins: [feedback],
  data: () => ({ source: 'SYSTEM', query: { keyword: '', sourceType: '', status: 1, pageNo: 1, pageSize: PAGE_SIZE }, result: { list: [], pageNo: 1, pageSize: PAGE_SIZE, total: 0 }, form: { channel: 'PHONE', content: '' }, selectedItems: [], importRows: [], importResult: null, importPageNo: 1, selectedPageNo: 1, bulkSearchKeys: [], loading: false, importing: false, selectingAll: false, sending: false, confirmVisible: false, resultVisible: false, sendResult: null, maxTargets: MAX_TARGETS, pageSize: PAGE_SIZE }),
  computed: {
    selectedKeys() { return this.selectedItems.map(item => item._key); },
    limitReached() { return this.selectedItems.length >= this.maxTargets; },
    importPageRows() { const start = (this.importPageNo - 1) * this.pageSize; return this.importRows.slice(start, start + this.pageSize); },
    selectedPageRows() { const start = (this.selectedPageNo - 1) * this.pageSize; return this.selectedItems.slice(start, start + this.pageSize); },
    excelEligibleRows() { return this.importRows.filter(item => this.hasChannel(item)); },
    excelAllSelected() { return this.excelEligibleRows.length > 0 && this.excelEligibleRows.every(item => this.selectedKeys.includes(item._key)); },
    searchAllSelected() { return this.bulkSearchKeys.length > 0 && this.bulkSearchKeys.every(key => this.selectedKeys.includes(key)); }
  },
  mounted() { this.load(); },
  methods: {
    /** 为后端记录和浏览器临时记录生成稳定且互不冲突的勾选键。 */
    normalizeSystem(item) { return { ...item, _key: `SYSTEM:${item.id}`, _source: 'SYSTEM' }; },
    normalizeImport(item, index) { return { ...item, _valid: item.valid !== false, _key: `EXCEL:${index}`, _source: 'EXCEL' }; },
    hasChannel(item) { return item._valid !== false && Boolean(this.form.channel === 'PHONE' ? item.phone : item.email); },
    /** 查询时把渠道直接交给后端，确保结果不包含当前渠道联系方式为空的数据。 */
    async load() { this.loading = true; try { const data = await promotionApi.targets({ ...this.query, channel: this.form.channel }); this.result = { ...data, list: data.list.map(this.normalizeSystem) }; this.bulkSearchKeys = []; } catch (e) { this.errorMessage(e); } finally { this.loading = false; } },
    search() { this.query.pageNo = 1; this.load(); },
    reset() { Object.assign(this.query, { keyword: '', sourceType: '', status: 1, pageNo: 1 }); this.load(); },
    changePage(pageNo) { this.query.pageNo = pageNo; this.load(); },
    switchSource(source) { this.source = source; },
    /** 切换渠道后剔除已选但无法通过新渠道触达的数据，并重新查询系统候选。 */
    changeChannel() { const before = this.selectedItems.length; this.selectedItems = this.selectedItems.filter(this.hasChannel); this.selectedPageNo = 1; this.bulkSearchKeys = []; if (before !== this.selectedItems.length) this.notify('已移除当前渠道联系方式为空的已选目标'); this.load(); },
    /** 单行勾选与取消同时更新已选表；达到上限后拒绝新增，但始终允许取消。 */
    toggleRow(item, checked) { const exists = this.selectedKeys.includes(item._key); if (!checked) { this.selectedItems = this.selectedItems.filter(row => row._key !== item._key); this.fixSelectedPage(); return; } if (exists) return; if (this.limitReached) { this.notify(`勾选数量已达 ${this.maxTargets} 条上限`, 'error'); return; } this.selectedItems.push(item); },
    /** 将一组候选按原有稳定顺序加入已选列表，并统一处理剩余额度和截断提示。 */
    addRows(rows, total, actionLabel) { const additions = rows.filter(item => this.hasChannel(item) && !this.selectedKeys.includes(item._key)); const capacity = this.maxTargets - this.selectedItems.length; this.selectedItems.push(...additions.slice(0, capacity)); if (total > capacity) this.notify(`${actionLabel}共有 ${total} 条可推广信息，超出上限，本次只勾选前 ${capacity} 条`, 'error'); },
    /** Excel 全选在浏览器内处理全部导入结果，不向后端创建任何临时名单。 */
    toggleExcelAll(event) { if (!event.target.checked) { const keys = new Set(this.excelEligibleRows.map(item => item._key)); this.selectedItems = this.selectedItems.filter(item => !keys.has(item._key)); this.fixSelectedPage(); return; } this.addRows(this.excelEligibleRows, this.excelEligibleRows.filter(item => !this.selectedKeys.includes(item._key)).length, '当前 Excel 名单'); },
    /** 搜索结果全选读取当前查询的全部候选，而非只处理当前分页。 */
    async toggleSearchAll(event) { if (!event.target.checked) { const keys = new Set(this.bulkSearchKeys); this.selectedItems = this.selectedItems.filter(item => !keys.has(item._key)); this.bulkSearchKeys = []; this.fixSelectedPage(); return; } await this.selectSystemRows(false); },
    /** 无条件全选忽略关键词和来源，只读取启用且当前渠道联系方式非空的前 6000 条。 */
    async selectUnconditional() { await this.selectSystemRows(true); },
    /**
     * 批量选择始终使用后端允许的 100 条分页循环读取，避免把 6000 误作为单页大小触发参数校验。
     * 首次请求取得总数后只补齐推广上限内所需页面，最终仍按后端 id 倒序的稳定顺序加入已选表。
     */
    async selectSystemRows(unconditional) { this.selectingAll = true; try { const base = unconditional ? { keyword: '', sourceType: '', status: 1, channel: this.form.channel } : { keyword: this.query.keyword, sourceType: this.query.sourceType, status: 1, channel: this.form.channel }; const first = await promotionApi.targets({ ...base, pageNo: 1, pageSize: 100 }); const pageCount = Math.ceil(Math.min(first.total, this.maxTargets) / 100); const requests = Array.from({ length: Math.max(0, pageCount - 1) }, (_, index) => promotionApi.targets({ ...base, pageNo: index + 2, pageSize: 100 })); const pages = requests.length ? await Promise.all(requests) : []; const rows = [first, ...pages].flatMap(page => page.list).slice(0, this.maxTargets).map(this.normalizeSystem); this.bulkSearchKeys = rows.map(item => item._key); this.addRows(rows, first.total, unconditional ? '系统全部信息' : '当前搜索结果'); } catch (e) { this.errorMessage(e); } finally { this.selectingAll = false; } },
    /** 后端仅解析 Excel 并回传有效行；页面保留全部数据并在本地进行分页展示。 */
    async previewImport(event) { const file = event.target.files?.[0]; event.target.value = ''; if (!file) return; this.importing = true; try { this.importResult = await promotionApi.previewImport(file); this.importRows = (this.importResult.list || []).map(this.normalizeImport); this.importPageNo = 1; this.notify(`Excel 导入完成，共展示 ${this.importRows.length} 条有效信息`); } catch (e) { this.errorMessage(e); } finally { this.importing = false; } },
    fixSelectedPage() { const pages = Math.max(1, Math.ceil(this.selectedItems.length / this.pageSize)); this.selectedPageNo = Math.min(this.selectedPageNo, pages); },
    /** 发送请求只携带最终勾选的系统 ID 和浏览器暂存 Excel 行，不再依赖数量预览或临时令牌。 */
    payload() { return { channel: this.form.channel, content: this.form.content, selectionMode: 'MIXED', targetIds: this.selectedItems.filter(item => item._source === 'SYSTEM').map(item => item.id), importedTargets: this.selectedItems.filter(item => item._source === 'EXCEL').map(({ name, phone, email, remark }) => ({ name, phone, email, remark })) }; },
    openConfirmation() { if (!this.selectedItems.length) { this.notify('请至少勾选一个推广目标', 'error'); return; } if (!this.form.content) { this.notify('请填写推广信息', 'error'); return; } this.confirmVisible = true; },
    /** 确认后调用 mock 渠道，成功结果明确标识没有真实消息发出。 */
    async send() { this.sending = true; try { this.sendResult = await promotionApi.send(this.payload()); this.confirmVisible = false; this.resultVisible = true; this.notify('模拟推广已完成'); } catch (e) { this.errorMessage(e); } finally { this.sending = false; } }
  }
};
</script>

<style scoped>
.promotion-builder{padding:24px}.step{display:flex;align-items:flex-start;gap:12px;margin:2px 0 14px}.step>span{display:grid;place-items:center;width:28px;height:28px;border-radius:50%;background:var(--primary);color:#fff;font-weight:700}.step h2{margin:1px 0 2px;font-size:18px}.step p{margin:0;color:var(--muted)}.channel-grid{display:grid;grid-template-columns:repeat(2,minmax(0,320px));gap:12px;margin:0 0 28px 40px}.choice-card{position:relative;display:grid;grid-template-columns:40px 1fr;gap:2px 10px;padding:15px;border:1px solid var(--border);border-radius:8px;cursor:pointer;transition:border-color .2s,background .2s}.choice-card.active{border-color:var(--primary);background:#eff6ff}.choice-card input{position:absolute;top:12px;right:12px}.choice-card i{grid-row:1/3;align-self:center;color:var(--primary);font-size:25px}.choice-card small{color:var(--muted)}.source-tabs{display:flex;margin-left:40px;border-bottom:1px solid var(--border)}.source-tabs button{min-height:44px;padding:0 18px;border:0;border-bottom:2px solid transparent;background:transparent;cursor:pointer}.source-tabs button.active{border-color:var(--primary);color:var(--primary);font-weight:700}.source-panel{margin:0 0 20px 40px;padding:18px;background:var(--background);border-radius:8px}.upload-toolbar,.system-filters{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:16px}.upload-toolbar span{color:var(--muted)}.file-button{position:relative;overflow:hidden}.file-button input{position:absolute;inset:0;opacity:0;cursor:pointer}.system-filters .layui-input{width:220px}.system-filters .layui-select{width:140px;height:38px;border:1px solid var(--border)}.unconditional-button{margin-left:auto}.selected-section{margin:0 0 28px 40px;padding:18px;border:1px solid var(--border);border-radius:8px}.table-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:10px}.table-heading h3{margin:0;font-size:16px}.table-heading p{margin:3px 0 0;color:var(--muted);font-size:13px}.table-heading strong{font-variant-numeric:tabular-nums;color:var(--primary)}.table-heading strong.limit{color:var(--danger)}.recipient-table{min-width:760px}.recipient-table .check-column{width:90px}.select-all{display:inline-flex;align-items:center;gap:6px;cursor:pointer}.empty-cell{padding:34px!important;text-align:center;color:var(--muted)}.limit-notice{display:flex;align-items:center;gap:7px;margin:0 0 12px;padding:9px 12px;border-radius:6px;background:#fff7ed;color:#9a3412}.message-field{position:relative;margin:0 0 28px 40px}.message-field label{display:block;margin-bottom:7px;font-weight:600}.message-field b{color:var(--danger)}.message-field textarea{min-height:130px;border-color:var(--border);border-radius:6px;resize:vertical}.message-field small{position:absolute;right:10px;bottom:8px;color:var(--muted)}.action-panel{display:flex;align-items:center;justify-content:flex-end;gap:18px;margin-left:40px;padding-top:20px;border-top:1px solid var(--border)}.send-result{text-align:center}.send-result i{display:block;color:#16a34a;font-size:46px}.send-result strong{display:block;margin:10px;font-size:20px}@media(max-width:720px){.promotion-builder{padding:16px}.channel-grid{grid-template-columns:1fr}.channel-grid,.source-tabs,.source-panel,.selected-section,.message-field,.action-panel{margin-left:0}.system-filters{align-items:stretch;flex-direction:column}.system-filters .layui-input,.system-filters .layui-select,.system-filters .layui-btn{width:100%;min-height:44px}.unconditional-button{margin-left:0}.action-panel{align-items:stretch;flex-direction:column}.choice-card{min-height:72px}}
</style>
