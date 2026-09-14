<template><nav class="subnav" aria-label="企业详情导航"><router-link :to="`/enterprises/${id}/overview`">企业概览</router-link><router-link :to="`/enterprises/${id}/members`">企业成员</router-link><router-link :to="`/enterprises/${id}/finance`">订单与流水</router-link></nav></template>
<script>
export default {
  name: 'EnterpriseNav',
  computed: {
    /**
     * 从当前路由提取企业 ID，为概览、成员和财务三个企业子页面生成一致导航地址；无参数时使用开发占位 ID。
     */
    id() { return this.$route.params.id || 1; }
  }
};
</script>
