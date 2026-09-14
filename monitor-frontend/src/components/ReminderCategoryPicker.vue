<template>
  <div ref="root" class="reminder-category-picker" @keydown.esc="close">
    <div class="category-trigger" role="button" tabindex="0" :aria-expanded="open" aria-haspopup="listbox" @click="toggle" @keydown.enter.prevent="toggle" @keydown.space.prevent="toggle">
      <span :class="['selection-text', { placeholder: !selectionText }]">{{selectionText || placeholder}}</span>
      <button v-if="selectedPaths.length" class="clear-selection" type="button" aria-label="清空提醒类别" @click.stop="clear">×</button>
      <i v-else class="layui-icon layui-icon-down" aria-hidden="true"></i>
    </div>

    <div v-if="open" class="category-dropdown">
      <div class="category-search"><i class="layui-icon layui-icon-search" aria-hidden="true"></i><input v-model.trim="keyword" type="text" placeholder="搜索类别或类型" aria-label="搜索提醒类别或类型"></div>
      <div v-if="filteredCategories.length" class="category-panels">
        <ul class="category-list" aria-label="提醒大类">
          <li v-for="category in filteredCategories" :key="category.code">
            <button type="button" :class="{ active: category.code === activeCode }" @mouseenter="activeCode=category.code" @focus="activeCode=category.code" @click="toggleCategory(category)">
              <span :class="['check-mark', categoryCheckState(category)]" aria-hidden="true"></span><span>{{category.name}}</span><i class="layui-icon layui-icon-right"></i>
            </button>
          </li>
        </ul>
        <ul v-if="activeCategory" class="type-list" aria-label="提醒小类">
          <li><button class="select-category" type="button" @click="toggleCategory(activeCategory)"><span :class="['check-mark', categoryCheckState(activeCategory)]" aria-hidden="true"></span><strong>选择整个大类</strong></button></li>
          <li v-for="type in visibleTypes" :key="type.code"><button type="button" @click="toggleType(activeCategory, type)"><span :class="['check-mark', { checked: isTypeSelected(activeCategory.code, type.code) }]" aria-hidden="true"></span><span>{{type.name}}</span></button></li>
        </ul>
      </div>
      <div v-else class="category-empty">没有匹配的提醒类别</div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ReminderCategoryPicker',
  props: {
    /** 后端筛选字典形成的二级类别树，组件不修改传入对象。 */
    categories: { type: Array, default: () => [] },
    /** 已选叶子路径；每项固定为 [categoryCode, typeCode]。 */
    modelValue: { type: Array, default: () => [] },
    placeholder: { type: String, default: '提醒类别 / 类型' }
  },
  emits: ['update:modelValue'],
  data: () => ({ open: false, keyword: '', activeCode: '' }),
  computed: {
    /** 复制有效路径，防止界面事件直接修改父组件计算属性返回的数据。 */
    selectedPaths() { return this.modelValue.filter(path => Array.isArray(path) && path.length >= 2); },
    /** 搜索同时匹配大类与小类；匹配小类时保留其所属大类，维持二级导航上下文。 */
    filteredCategories() {
      const keyword = this.keyword.toLowerCase();
      if (!keyword) return this.categories;
      return this.categories.filter(category => category.name.toLowerCase().includes(keyword) || category.types.some(type => type.name.toLowerCase().includes(keyword)));
    },
    activeCategory() { return this.filteredCategories.find(category => category.code === this.activeCode) || this.filteredCategories[0] || null; },
    visibleTypes() {
      if (!this.activeCategory) return [];
      const keyword = this.keyword.toLowerCase();
      if (!keyword || this.activeCategory.name.toLowerCase().includes(keyword)) return this.activeCategory.types;
      return this.activeCategory.types.filter(type => type.name.toLowerCase().includes(keyword));
    },
    /** 完整大类只显示大类名；部分多选限制为首项加数量摘要，始终保持单行固定高度。 */
    selectionText() {
      if (!this.selectedPaths.length) return '';
      const category = this.categories.find(item => item.code === this.selectedPaths[0][0]);
      if (!category) return '';
      const selectedCodes = new Set(this.selectedPaths.filter(path => path[0] === category.code).map(path => path[1]));
      if (category.types.length && category.types.every(type => selectedCodes.has(type.code))) return category.name;
      const labels = category.types.filter(type => selectedCodes.has(type.code)).map(type => type.name);
      return labels.length > 1 ? `${labels[0]} 等 ${labels.length} 项` : labels[0] || '';
    }
  },
  watch: {
    /** 字典异步加载后默认展开首个大类，避免首次打开右栏为空。 */
    categories: { immediate: true, handler(value) { if (!value.some(item => item.code === this.activeCode)) this.activeCode = value[0]?.code || ''; } }
  },
  mounted() { document.addEventListener('mousedown', this.onOutsideClick); },
  beforeUnmount() { document.removeEventListener('mousedown', this.onOutsideClick); },
  methods: {
    /** 打开或关闭浮层；每次打开保留当前大类，方便连续调整小类。 */
    toggle() { this.open = !this.open; },
    close() { this.open = false; },
    clear() { this.keyword = ''; this.$emit('update:modelValue', []); },
    onOutsideClick(event) { if (this.open && !this.$refs.root?.contains(event.target)) this.close(); },
    isTypeSelected(categoryCode, typeCode) { return this.selectedPaths.some(path => path[0] === categoryCode && path[1] === typeCode); },
    /** 返回父级全选、部分选中或未选中状态，为大类一键选择提供明确视觉反馈。 */
    categoryCheckState(category) {
      const count = category.types.filter(type => this.isTypeSelected(category.code, type.code)).length;
      return count === category.types.length && count > 0 ? 'checked' : count > 0 ? 'partial' : '';
    },
    /** 选择大类时一次勾选全部叶子，再次点击则清空；不同大类之间保持单大类筛选语义。 */
    toggleCategory(category) {
      this.activeCode = category.code;
      const allSelected = category.types.length > 0 && category.types.every(type => this.isTypeSelected(category.code, type.code));
      this.$emit('update:modelValue', allSelected ? [] : category.types.map(type => [category.code, type.code]));
    },
    /** 小类支持同一大类内多选；切换到其他大类时自动清除旧大类选择，避免提交歧义。 */
    toggleType(category, type) {
      this.activeCode = category.code;
      const sameCategory = this.selectedPaths.filter(path => path[0] === category.code);
      const selected = sameCategory.some(path => path[1] === type.code);
      const next = selected ? sameCategory.filter(path => path[1] !== type.code) : [...sameCategory, [category.code, type.code]];
      this.$emit('update:modelValue', next);
    }
  }
};
</script>

<style scoped>
.reminder-category-picker{position:relative;width:100%;height:38px}.category-trigger{display:flex;align-items:center;width:100%;height:38px;padding:0 11px;border:1px solid var(--border);border-radius:4px;background:#fff;color:var(--text);cursor:pointer}.category-trigger:hover,.category-trigger:focus{border-color:var(--primary);outline:none}.selection-text{min-width:0;overflow:hidden;text-align:left;text-overflow:ellipsis;white-space:nowrap}.selection-text.placeholder{color:var(--muted)}.category-trigger>i,.clear-selection{flex:0 0 auto;margin-left:auto;color:var(--muted)}.clear-selection{width:24px;height:24px;padding:0;border:0;background:transparent;font-size:19px;line-height:24px;cursor:pointer}.clear-selection:hover{color:var(--danger)}.category-dropdown{position:absolute;z-index:80;top:43px;left:0;width:540px;overflow:hidden;border:1px solid var(--border);border-radius:7px;background:#fff;box-shadow:0 12px 28px rgba(15,23,42,.14)}.category-search{position:relative;padding:10px;border-bottom:1px solid var(--border)}.category-search i{position:absolute;top:20px;left:21px;color:var(--muted)}.category-search input{box-sizing:border-box;width:100%;height:34px;padding:0 10px 0 32px;border:1px solid var(--border);border-radius:4px;outline:none}.category-search input:focus{border-color:var(--primary)}.category-panels{display:grid;grid-template-columns:210px 1fr;height:290px}.category-list,.type-list{margin:0;padding:6px;overflow-y:auto;list-style:none}.category-list{border-right:1px solid var(--border);background:#f8fafc}.category-list button,.type-list button{display:flex;align-items:center;gap:9px;width:100%;min-height:38px;padding:7px 9px;border:0;border-radius:4px;background:transparent;color:var(--text);text-align:left;cursor:pointer}.category-list button:hover,.category-list button.active,.type-list button:hover{background:#eff6ff;color:var(--primary)}.category-list button>i{margin-left:auto}.type-list .select-category{margin-bottom:5px;border-bottom:1px solid var(--border);border-radius:4px 4px 0 0}.check-mark{position:relative;box-sizing:border-box;flex:0 0 16px;width:16px;height:16px;border:1px solid #94a3b8;border-radius:3px;background:#fff}.check-mark.checked,.check-mark.partial{border-color:var(--primary);background:var(--primary)}.check-mark.checked::after{position:absolute;top:1px;left:4px;width:5px;height:9px;border:solid #fff;border-width:0 2px 2px 0;content:'';transform:rotate(45deg)}.check-mark.partial::after{position:absolute;top:6px;left:3px;width:8px;height:2px;background:#fff;content:''}.category-empty{padding:32px;text-align:center;color:var(--muted)}
@media(max-width:720px){.category-dropdown{right:0;width:min(540px,calc(100vw - 44px))}.category-panels{grid-template-columns:42% 58%}}
</style>
