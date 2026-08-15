<template>
  <div class="page-heading">
    <div><p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p><h1>{{ title }}</h1><p>{{ description }}</p></div>
    <div class="page-actions"><slot /></div>
  </div>
</template>
<script>
/**
 * 统一监控页面标题、英文眉题、说明文案和右侧操作插槽，保证各业务页面头部结构一致。
 */
export default { name: 'PageHeader', props: { eyebrow: String, title: { type: String, required: true }, description: String } };
</script>
