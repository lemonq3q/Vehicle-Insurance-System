<template>
  <div class="page-heading">
    <div><p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p><h1>{{ title }}</h1><p>{{ description }}</p></div>
    <div class="page-actions"><slot /></div>
  </div>
</template>
<script>export default { name: 'PageHeader', props: { eyebrow: String, title: { type: String, required: true }, description: String } };</script>
