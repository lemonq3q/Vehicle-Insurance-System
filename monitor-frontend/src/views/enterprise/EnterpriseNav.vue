<template><nav class="subnav" aria-label="企业详情导航"><router-link :to="`/enterprises/${id}/overview`">企业概览</router-link><router-link :to="`/enterprises/${id}/members`">企业员工</router-link><router-link :to="`/enterprises/${id}/finance`">订单与流水</router-link></nav></template>
<script>export default{name:'EnterpriseNav',computed:{id(){return this.$route.params.id||1;}}};</script>
