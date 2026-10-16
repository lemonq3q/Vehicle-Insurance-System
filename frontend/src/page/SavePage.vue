<template>
  <LoginPage />
</template>

<script>
import LoginPage from '@/page/LoginPage.vue';

/**
 * 兼容旧备用登录页的引用，复用当前邮箱登录与密码找回页面。
 * 共享账号注册统一由 SaaS 门户提供，不保留另一套车险注册入口。
 */
export default {
  name: 'SavePage',
  components: { LoginPage }
};
</script>
