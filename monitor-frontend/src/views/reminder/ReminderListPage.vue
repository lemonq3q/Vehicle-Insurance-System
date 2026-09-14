<template>
  <div>
    <PageHeader eyebrow="Risk Operations" title="提醒处理" description="集中查看企业风险提醒，并对已经完成跟进的事项进行确认处理。">
      <button class="layui-btn monitor-secondary" type="button" :disabled="loading" @click="load">刷新列表</button>
    </PageHeader>

    <section class="panel reminder-overview" aria-label="提醒状态说明">
      <div><span class="overview-icon critical"><i class="layui-icon layui-icon-notice"></i></span><p><strong>优先处理紧急提醒</strong><small>列表默认按严重等级和最近触发时间排序</small></p></div>
      <div><span class="overview-icon pending"><i class="layui-icon layui-icon-list"></i></span><p><strong>升级后重新进入待办</strong><small>已经处理的事项若风险升级，将自动恢复为待处理</small></p></div>
    </section>

    <section class="panel query-panel reminders-panel">
      <div class="query-toolbar table-toolbar">
        <h2>系统提醒</h2>
        <form class="query-filters filters" @submit.prevent="search">
          <select id="severity" v-model="query.severity" class="layui-select" aria-label="严重等级"><option value="">全部等级</option><option value="CRITICAL">紧急</option><option value="WARNING">重要</option><option value="NOTICE">提醒</option></select>
          <ReminderCategoryPicker v-model="reminderPath" class="reminder-cascader" :categories="options.categories" />
          <div class="enterprise-combobox"><input id="enterprise" v-model="enterpriseKeyword" class="layui-input" type="text" autocomplete="off" placeholder="输入企业名称或编码" aria-label="所属企业" role="combobox" aria-autocomplete="list" :aria-expanded="enterpriseOpen" aria-controls="enterprise-options" @input="onEnterpriseInput" @focus="onEnterpriseFocus" @keydown.esc="enterpriseOpen=false"><i v-if="enterpriseSearching" class="layui-icon layui-icon-loading layui-anim layui-anim-rotate layui-anim-loop" aria-hidden="true"></i><ul v-if="enterpriseOpen" id="enterprise-options" role="listbox"><li v-if="enterpriseSearching" class="option-state">正在搜索…</li><li v-else-if="!enterpriseOptions.length" class="option-state">没有匹配的企业</li><li v-for="item in enterpriseOptions" :key="item.id" role="option" tabindex="0" @mousedown.prevent="selectEnterprise(item)" @keydown.enter.prevent="selectEnterprise(item)"><strong>{{item.name}}</strong><small>{{item.code}}</small></li></ul></div>
          <select id="status" v-model="query.processStatus" class="layui-select" aria-label="处理状态"><option value="">全部状态</option><option value="0">待处理</option><option value="1">已处理</option></select>
          <button class="layui-btn monitor-primary" :disabled="loading">查询</button>
          <button class="layui-btn monitor-secondary" type="button" @click="reset">重置</button>
        </form>
      </div>
      <div v-if="loading" class="loading-state">正在查询提醒…</div>
      <div v-else-if="!result.list.length" class="empty-state">没有符合条件的提醒，请调整筛选条件。</div>
      <template v-else>
        <div class="table-wrap"><table class="layui-table monitor-table reminder-table"><thead><tr><th>提醒内容</th><th>类别 / 类型</th><th>所属企业</th><th>联系方式</th><th>等级</th><th>当前阶段</th><th>最近触发</th><th>处理状态</th><th>操作</th></tr></thead><tbody>
          <tr v-for="item in result.list" :key="item.id">
            <td class="reminder-content"><strong>{{item.title}}</strong><small :title="item.content">{{item.content}}</small></td>
            <td><span>{{item.categoryName || '未分类'}}</span><small>{{item.typeName}}</small></td>
            <td class="enterprise-info"><strong>{{item.enterpriseNameSnapshot || '—'}}</strong><small>编码：{{item.enterpriseCode || '—'}}</small></td>
            <td class="enterprise-contact"><span>{{item.enterpriseContactName || '—'}}</span><span>{{item.enterpriseContactPhone || '—'}}</span></td>
            <td><span :class="['tag', severityMeta(item.severity).class]">{{severityMeta(item.severity).text}}</span></td>
            <td><strong>{{item.reminderStage}}</strong><small>第 {{item.revision}} 版</small></td>
            <td class="number">{{dateTime(item.lastTriggeredAt)}}</td>
            <td><span :class="['tag', Number(item.processStatus) === 1 ? 'success' : 'warning']">{{Number(item.processStatus) === 1 ? '已处理' : '待处理'}}</span></td>
            <td><div class="row-actions"><router-link class="layui-btn monitor-secondary reminder-action" :to="`/enterprises/${item.enterpriseId}/overview`">企业详情</router-link><button v-if="item.processStatus===0" class="layui-btn monitor-primary reminder-action" type="button" @click="openConfirm(item)">处理</button><template v-else><button class="layui-btn reminder-action reminder-restore" type="button" @click="openRestore(item)">恢复未处理</button><span class="processed-note">{{dateTime(item.processedAt)}}</span></template></div></td>
          </tr>
        </tbody></table></div>
        <AppPagination :page-no="result.pageNo" :page-size="result.pageSize" :total="result.total" @change="changePage" />
      </template>
    </section>

    <AppModal v-model="confirmVisible" title="确认标记为已处理？" confirm-text="确认处理" :danger="true" :loading="submitting" @confirm="markProcessed">
      <div class="warning-box"><i class="layui-icon layui-icon-notice"></i><div><strong>此操作会从当前待处理队列中移除该提醒</strong><p>如果后续风险升级，系统仍会重新将它恢复为待处理。请确认你已经完成必要的企业跟进。</p></div></div>
      <div class="confirm-target" v-if="selected"><span>企业</span><strong>{{selected.enterpriseNameSnapshot}}</strong><span>提醒</span><strong>{{selected.title}}</strong></div>
      <div class="field"><label for="remark">处理备注（可选）</label><textarea id="remark" v-model.trim="remark" class="layui-textarea" maxlength="500" placeholder="记录已采取的措施或沟通结果"></textarea></div>
    </AppModal>
    <AppModal v-model="restoreVisible" title="确认恢复为未处理？" confirm-text="确认恢复" :loading="submitting" @confirm="restoreUnprocessed">
      <div class="restore-box"><i class="layui-icon layui-icon-refresh"></i><div><strong>该提醒将重新进入待处理队列</strong><p>原处理时间、处理人和备注会保留为最近一次处理记录，当前处理状态将被清空。</p></div></div>
      <div class="confirm-target" v-if="selected"><span>企业</span><strong>{{selected.enterpriseNameSnapshot}}</strong><span>提醒</span><strong>{{selected.title}}</strong></div>
    </AppModal>
    <AppToast :message="toastMessage" :type="toastType" />
  </div>
</template>

<script>
import PageHeader from '@/components/PageHeader.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppModal from '@/components/AppModal.vue';
import AppToast from '@/components/AppToast.vue';
import ReminderCategoryPicker from '@/components/ReminderCategoryPicker.vue';
import feedback from '@/mixins/feedback';
import { reminderApi } from '@/api/monitor';

export default {
  name: 'ReminderListPage', components: { PageHeader, AppPagination, AppModal, AppToast, ReminderCategoryPicker }, mixins: [feedback],
  /** 维护组合筛选、服务端分页和人工处理确认状态；页面不缓存提醒，处理成功后重新读取数据库事实。 */
  data: () => ({ query: { severity: '', categoryCode: '', typeCodes: '', enterpriseId: '', processStatus: '0', pageNo: 1, pageSize: 10 }, options: { categories: [] }, enterpriseKeyword: '', enterpriseOptions: [], enterpriseSearching: false, enterpriseOpen: false, enterpriseSearchTimer: null, result: { list: [], pageNo: 1, pageSize: 10, total: 0 }, loading: true, submitting: false, confirmVisible: false, restoreVisible: false, selected: null, remark: '' }),
  computed: {
    /** 将后端类别与类型字典转换为 Element Plus 二级级联树。 */
    reminderCascaderOptions() { return this.options.categories.map(category => ({ value: category.code, label: category.name, children: category.types.map(type => ({ value: type.code, label: type.name })) })); },
    /**
     * 级联选择器采用父子联动多选：勾选大类时 Element Plus 会自动勾选其全部小类。
     * 页面把同一大类下的叶子编码合并提交；全部叶子均选中时只提交类别编码，保持查询语义简洁。
     */
    reminderPath: {
      get() {
        if (!this.query.categoryCode) return [];
        const category = this.options.categories.find(item => item.code === this.query.categoryCode);
        const selectedTypes = this.query.typeCodes ? this.query.typeCodes.split(',') : category?.types.map(type => type.code) || [];
        return selectedTypes.map(typeCode => [this.query.categoryCode, typeCode]);
      },
      set(value) {
        const paths = Array.isArray(value) ? value : [];
        if (!paths.length) { this.query.categoryCode = ''; this.query.typeCodes = ''; return; }
        const categoryCode = paths[0][0];
        const category = this.options.categories.find(item => item.code === categoryCode);
        const selectedTypes = [...new Set(paths.filter(path => path[0] === categoryCode).map(path => path[1]).filter(Boolean))];
        this.query.categoryCode = categoryCode;
        this.query.typeCodes = selectedTypes.length === category?.types.length ? '' : selectedTypes.join(',');
      }
    }
  },
  /** 并行获取筛选字典和首屏提醒，减少页面初始化等待。 */
  mounted() { Promise.all([this.loadOptions(), this.load()]).finally(() => { this.loading = false; }); },
  /** 页面销毁时取消尚未触发的联想请求，避免路由切换后继续修改旧页面状态。 */
  beforeUnmount() { clearTimeout(this.enterpriseSearchTimer); },
  methods: {
    /** 将空字符串条件移除后查询真实后端，避免 Spring 将空企业 ID 转换失败。 */
    async load() { this.loading = true; try { const params = Object.fromEntries(Object.entries(this.query).filter(([, value]) => value !== '')); const data = await reminderApi.list(params); this.result = { ...data, list: data.list.map(this.normalizeReminder) }; } catch (error) { this.errorMessage(error); } finally { this.loading = false; } },
    /**
     * 兼容尚未重启的旧后端所返回的下划线字段，确保企业 ID 不会转换成 NaN，
     * 同时把待处理状态固定转换为数值，使处理按钮不受 JDBC Map 命名方式影响。
     */
    normalizeReminder(item) { return { ...item, enterpriseId: Number(item.enterpriseId ?? item.enterprise_id), enterpriseNameSnapshot: item.enterpriseNameSnapshot ?? item.enterprise_name_snapshot, enterpriseCode: item.enterpriseCode ?? item.enterprise_code, enterpriseContactName: item.enterpriseContactName ?? item.enterprise_contact_name, enterpriseContactPhone: item.enterpriseContactPhone ?? item.enterprise_contact_phone, reminderType: item.reminderType ?? item.reminder_type, typeName: item.typeName ?? item.type_name, categoryCode: item.categoryCode ?? item.category_code, categoryName: item.categoryName ?? item.category_name, reminderStage: item.reminderStage ?? item.reminder_stage, stageLevel: item.stageLevel ?? item.stage_level, triggerCount: item.triggerCount ?? item.trigger_count, firstTriggeredAt: item.firstTriggeredAt ?? item.first_triggered_at, lastTriggeredAt: item.lastTriggeredAt ?? item.last_triggered_at, processStatus: Number(item.processStatus ?? item.process_status), processedAt: item.processedAt ?? item.processed_at, processedBy: item.processedBy ?? item.processed_by, processRemark: item.processRemark ?? item.process_remark }; },
    /** 获取后端返回的级联提醒字典；企业选项仅在输入关键词后查询。 */
    async loadOptions() { try { this.options = await reminderApi.filterOptions(); } catch (error) { this.errorMessage(error); } },
    /** 输入变化立即清除旧企业 ID，并使用短防抖减少连续击键产生的数据库查询。 */
    onEnterpriseInput() { this.query.enterpriseId = ''; clearTimeout(this.enterpriseSearchTimer); const keyword = this.enterpriseKeyword.trim(); if (!keyword) { this.enterpriseOptions = []; this.enterpriseOpen = false; return; } this.enterpriseSearchTimer = setTimeout(() => this.searchEnterpriseOptions(keyword), 250); },
    /** 已有关键词重新聚焦时恢复候选列表，不在空输入状态请求企业全集。 */
    onEnterpriseFocus() { if (this.enterpriseKeyword.trim() && this.enterpriseOptions.length) this.enterpriseOpen = true; },
    /** 调用真实后端按名称或编码获取最多 20 条企业候选，并丢弃已经过期的关键词响应。 */
    async searchEnterpriseOptions(keyword) { this.enterpriseSearching = true; this.enterpriseOpen = true; try { const items = await reminderApi.enterpriseOptions(keyword); if (keyword === this.enterpriseKeyword.trim()) this.enterpriseOptions = items; } catch (error) { this.enterpriseOptions = []; this.errorMessage(error); } finally { if (keyword === this.enterpriseKeyword.trim()) this.enterpriseSearching = false; } },
    /** 只有显式选中候选项后才保存企业 ID，防止任意输入文本作为筛选条件提交。 */
    selectEnterprise(item) { this.query.enterpriseId = item.id; this.enterpriseKeyword = `${item.name}（${item.code}）`; this.enterpriseOptions = [item]; this.enterpriseOpen = false; },
    /** 同时清理展示文本、选中 ID 和候选项，恢复不限制企业的查询状态。 */
    clearEnterprise() { clearTimeout(this.enterpriseSearchTimer); this.enterpriseKeyword = ''; this.query.enterpriseId = ''; this.enterpriseOptions = []; this.enterpriseOpen = false; },
    /** 新筛选从第一页开始，保证用户不会停留在超出结果范围的旧页码。 */
    search() { this.query.pageNo = 1; this.load(); },
    /** 清除其他业务筛选并恢复第一页；处理状态回到页面默认的待处理队列。 */
    reset() { Object.assign(this.query, { severity: '', categoryCode: '', typeCodes: '', enterpriseId: '', processStatus: '0', pageNo: 1 }); this.clearEnterprise(); this.load(); },
    /** 应用分页组件返回的新页码并重新查询；每页数量沿用页面当前设置。 */
    changePage(pageNo) { this.query.pageNo = pageNo; this.load(); },
    /** 打开高风险确认弹窗时保存提醒版本，最终提交依赖该版本进行并发校验。 */
    openConfirm(item) { this.selected = item; this.remark = ''; this.confirmVisible = true; },
    /** 打开恢复确认弹窗并保存页面所见版本，避免无确认直接改变待办队列。 */
    openRestore(item) { this.selected = item; this.restoreVisible = true; },
    /** 提交处理动作；若提醒在弹窗期间升级，后端拒绝旧版本并保留待处理状态。 */
    async markProcessed() { if (!this.selected || this.submitting) return; this.submitting = true; try { await reminderApi.markProcessed(this.selected.id, { revision: this.selected.revision, remark: this.remark }); this.confirmVisible = false; this.notify('提醒已标记为已处理'); await this.load(); } catch (error) { this.errorMessage(error); } finally { this.submitting = false; } },
    /** 按页面版本恢复提醒；并发状态已变化时由后端拒绝并提示刷新。 */
    async restoreUnprocessed() { if (!this.selected || this.submitting) return; this.submitting = true; try { await reminderApi.restoreUnprocessed(this.selected.id, { revision: this.selected.revision }); this.restoreVisible = false; this.notify('提醒已恢复为待处理'); await this.load(); } catch (error) { this.errorMessage(error); } finally { this.submitting = false; } },
    severityMeta(value) { return value === 'CRITICAL' ? { text: '紧急', class: 'danger' } : value === 'WARNING' ? { text: '重要', class: 'warning' } : { text: '提醒', class: '' }; },
    dateTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '—'; }
  }
};
</script>

<style scoped>
.reminder-overview{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1px;margin-bottom:16px;overflow:hidden;background:var(--border)}.reminder-overview>div{display:flex;align-items:center;gap:13px;padding:16px 18px;background:var(--surface)}.overview-icon{display:grid;place-items:center;flex:0 0 42px;height:42px;border-radius:10px;color:var(--primary);background:#eaf2ff;font-size:19px}.overview-icon.critical{color:var(--danger);background:#fff1f2}.reminder-overview p{display:flex;flex-direction:column;margin:0}.reminder-overview small,.reminder-table small{display:block;color:var(--muted);font-size:12px}.reminder-filter{display:flex;align-items:flex-end;gap:10px;min-width:0}.filter-fields{display:flex;flex:1 1 auto;align-items:flex-end;flex-wrap:wrap;gap:10px;min-width:0}.filter-fields>.field{flex:0 0 142px;width:142px;min-width:0}.filter-fields>.enterprise-field{flex-basis:240px;width:240px}.filter-fields .layui-input,.filter-fields .layui-select{display:block;width:100%;min-width:0}.reminder-filter>.action-row{flex:0 0 auto;margin-left:auto}.enterprise-combobox{position:relative;width:100%}.enterprise-combobox>.layui-input{padding-right:36px}.enterprise-combobox>i,.enterprise-combobox>button{position:absolute;z-index:2;top:0;right:0;display:grid;place-items:center;width:36px;height:40px;border:0;background:transparent;color:var(--muted)}.enterprise-combobox>ul{position:absolute;z-index:40;top:calc(100% + 5px);right:0;left:0;max-height:260px;margin:0;padding:5px;overflow:auto;border:1px solid var(--border);border-radius:7px;background:#fff;box-shadow:0 12px 28px rgba(15,23,42,.14);list-style:none}.enterprise-combobox li{display:flex;flex-direction:column;min-height:44px;padding:7px 9px;border-radius:5px;cursor:pointer}.enterprise-combobox li:hover,.enterprise-combobox li:focus{outline:none;background:#eff6ff}.enterprise-combobox li small{color:var(--muted)}.enterprise-combobox .option-state{justify-content:center;color:var(--muted);cursor:default}.reminder-table{min-width:1410px}.reminder-content{max-width:340px}.reminder-content small{display:-webkit-box;margin-top:4px;overflow:hidden;-webkit-box-orient:vertical;-webkit-line-clamp:2}.enterprise-info{min-width:190px}.enterprise-info strong{display:block}.enterprise-info small{margin-top:3px}.enterprise-contact{min-width:145px}.enterprise-contact span{display:block;line-height:1.65}.row-actions{display:flex;align-items:center;gap:6px;white-space:nowrap}.reminder-action{min-height:26px;height:26px;padding:0 9px;border-radius:4px;font-size:12px;line-height:26px}.reminder-restore{border-color:#f59e0b;background:#f59e0b;color:#fff}.reminder-restore:hover,.reminder-restore:focus{border-color:#d97706;background:#d97706;color:#fff}.processed-note{color:var(--muted);font-size:12px}.warning-box,.restore-box{display:flex;gap:12px;padding:14px;border-radius:8px}.warning-box{border:1px solid #fecaca;color:#991b1b;background:#fff7f7}.restore-box{border:1px solid #bfdbfe;color:#1e40af;background:#eff6ff}.warning-box>i,.restore-box>i{font-size:22px}.warning-box p,.restore-box p{margin:5px 0 0}.warning-box p{color:#7f1d1d}.restore-box p{color:#1e3a8a}.confirm-target{display:grid;grid-template-columns:52px 1fr;gap:7px 10px;padding:14px 0}.confirm-target span{color:var(--muted)}.layui-textarea{min-height:92px;border-radius:6px;border-color:#cbd5e1;resize:vertical}@media(max-width:1200px){.reminder-filter{align-items:stretch;flex-direction:column}.reminder-filter>.action-row{align-self:flex-end;margin-top:0}}@media(max-width:700px){.reminder-overview{grid-template-columns:1fr}.filter-fields{flex-direction:column;align-items:stretch}.filter-fields>.field,.filter-fields>.enterprise-field{flex-basis:auto;width:100%}.reminder-filter>.action-row{align-self:stretch}.reminder-filter>.action-row .layui-btn{flex:1}}
.reminder-header{min-height:auto;align-items:flex-start;flex-wrap:wrap;padding:12px 18px 14px}.reminder-heading{flex:1 0 auto;margin-right:auto;padding:0}.reminder-filter{flex:0 0 970px;width:970px;max-width:100%;justify-content:flex-end;margin-left:auto}.reminder-filter .filter-fields{flex:1 1 auto;justify-content:flex-end}.reminder-filter>.action-row{margin-left:0}
@media(max-width:1200px){.reminder-filter{align-items:flex-end;flex-direction:row}.reminder-filter>.action-row{align-self:flex-end}}
@media(max-width:700px){.reminder-filter{align-items:stretch;flex-direction:column}.reminder-filter .filter-fields{align-items:stretch}.reminder-filter>.action-row{align-self:stretch}}

/* 与 SaaS 门户企业成员卡片保持同一结构：标题在左，紧凑筛选工具栏在右，空间不足时自然整行换行。 */
.reminders-panel{padding:22px;margin-bottom:18px}.table-toolbar{display:flex;align-items:end;justify-content:space-between;gap:14px;flex-wrap:wrap}.table-toolbar h2{margin:0 0 16px;font-size:18px}.filters{display:flex;gap:10px;flex-wrap:wrap;align-items:center;margin-left:auto}.filters .layui-input{width:220px;height:38px}.filters .layui-select{width:130px;min-width:130px;height:38px;border:1px solid var(--border);border-radius:4px}.filters .layui-btn{height:38px}.filters .enterprise-combobox{width:220px}.filters .enterprise-combobox>i,.filters .enterprise-combobox>button{height:38px}.reminder-cascader{flex:0 0 380px;width:380px;height:38px}.reminder-cascader :deep(.el-input){height:38px!important;line-height:38px}.reminder-cascader :deep(.el-input__wrapper){min-height:38px!important;height:38px!important;max-height:38px!important;padding:0 11px;overflow:hidden;border-radius:4px;background:#fff;box-shadow:0 0 0 1px var(--border) inset;transition:box-shadow .2s}.reminder-cascader :deep(.el-cascader__tags){align-items:center;flex-wrap:nowrap;width:calc(100% - 34px);max-width:calc(100% - 34px);height:32px!important;max-height:32px!important;padding:4px 10px;overflow:hidden}.reminder-selection-summary{display:block;width:100%;overflow:hidden;color:var(--text);font-size:14px;line-height:24px;text-overflow:ellipsis;white-space:nowrap}.reminder-cascader :deep(.el-input__wrapper:hover){box-shadow:0 0 0 1px #94a3b8 inset}.reminder-cascader :deep(.el-input__wrapper.is-focus){box-shadow:0 0 0 1px var(--primary) inset}.reminder-cascader :deep(.el-input__inner){height:36px!important;line-height:36px!important;color:var(--text);font-size:14px}.reminder-cascader :deep(.el-input__inner::placeholder){color:var(--muted)}:global(.monitor-reminder-cascader-popper.el-popper){border-color:var(--border);border-radius:7px;box-shadow:0 12px 28px rgba(15,23,42,.14)}:global(.monitor-reminder-cascader-popper .el-cascader-node){min-height:38px;color:var(--text)}:global(.monitor-reminder-cascader-popper .el-cascader-node:hover),:global(.monitor-reminder-cascader-popper .el-cascader-node.in-active-path),:global(.monitor-reminder-cascader-popper .el-cascader-node.is-active){color:var(--primary);background:#eff6ff}:global(.monitor-reminder-cascader-popper .el-cascader-node.is-active){font-weight:700}.reminders-panel>.loading-state,.reminders-panel>.empty-state{margin:0 -22px -22px}.reminders-panel>.table-wrap{margin:0}.reminders-panel>.pagination{margin:0 -22px -22px}
@media(max-width:720px){.filters{width:100%;margin-left:0}.filters .layui-input,.filters .layui-select,.filters .reminder-cascader,.filters .enterprise-combobox{width:100%}.filters .layui-btn{flex:1}.table-toolbar h2{margin-bottom:0}}
</style>
