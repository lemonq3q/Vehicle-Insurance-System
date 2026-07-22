<template>
  <router-view />
  <NotificationCenter />
</template>

<script>
import NotificationCenter from '@/components/NotificationCenter.vue';

export default {
  name: 'App',
  components: { NotificationCenter }
};
</script>
