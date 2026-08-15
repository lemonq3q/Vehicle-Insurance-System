<template>
  <main class="information-page">
    <header class="information-nav">
      <router-link class="brand" to="/" aria-label="iDatag 官网首页">
        <img class="brand-logo" src="@/assets/brand/idatag-logo.png" alt="" />
        <span class="brand-name">iDatag</span>
        <span class="brand-divider"></span>
        <span class="brand-product">车险业务云平台</span>
      </router-link>
      <nav aria-label="官网导航">
        <button class="language-switch" type="button" @click="toggleLanguage">{{ language === 'zh' ? 'English' : '中文' }}</button>
        <router-link to="/login">登录</router-link>
        <router-link class="nav-cta" to="/login">立即使用</router-link>
      </nav>
    </header>

    <article class="document-content" :lang="language" v-html="documentHtml"></article>

    <footer class="information-footer">
      <nav aria-label="信息页面导航">
        <router-link to="/privacy">用户隐私声明</router-link>
        <router-link to="/terms">服务使用协议</router-link>
        <router-link to="/about">关于我们</router-link>
      </nav>
      <p>香港科学园（HKSTP）Ideation 计划入选企业。</p>
      <small>© 2026 iDatag. 保留所有权利。</small>
    </footer>
  </main>
</template>

<script>
import documents from '@/content/informationDocuments';

export default {
  name: 'InformationPage',
  props: {
    page: { type: String, required: true }
  },
  /**
   * 协议阅读页默认展示中文版本，语言切换只改变当前页面本地状态，不修改原始静态文档内容。
   */
  data() {
    return { language: 'zh' };
  },
  computed: {
    /**
     * 根据路由指定的文档类型和当前语言选择预生成的静态 HTML；内容来自对应 Markdown 的完整转换结果。
     */
    documentHtml() {
      return documents[this.page][this.language].html;
    }
  },
  methods: {
    /**
     * 在同一协议的中英文版本间切换，保持当前路由及页面位置结构不变。
     */
    toggleLanguage() {
      this.language = this.language === 'zh' ? 'en' : 'zh';
    }
  }
};
</script>

<style scoped>
.information-page { min-height: 100dvh; color: #10251f; background: #f5f7f4; }
.information-nav { position: fixed; inset: 0 0 auto; z-index: 30; display: flex; align-items: center; justify-content: space-between; height: 76px; padding: 0 clamp(24px, 5vw, 76px); border-bottom: 1px solid #dce5df; background: rgba(255,255,255,.96); box-shadow: 0 8px 28px rgba(16,37,31,.08); }
.brand, .information-nav nav { display: flex; align-items: center; }.brand { gap: 11px; color: #10251f; font-weight: 700; }.brand-logo { width: 38px; height: 38px; object-fit: contain; }.brand-name { font-size: 19px; }.brand-divider { width: 1px; height: 18px; margin: 0 2px; background: currentColor; opacity: .3; }.brand-product { color: #63766f; font-size: 13px; font-weight: 500; }.information-nav nav { gap: 28px; font-size: 14px; font-weight: 600; }.information-nav nav a { color: inherit; text-decoration: none; }.information-nav nav a:hover, .information-nav nav a:focus-visible { color: #0f8f68; }.language-switch { padding: 0; border: 0; color: inherit; background: transparent; font: inherit; cursor: pointer; }.language-switch:hover, .language-switch:focus-visible { color: #0f8f68; }.nav-cta { min-height: 38px; padding: 0 19px; border: 1px solid #f0b44d; border-radius: 2px; background: #f0b44d; color: #17362c !important; line-height: 38px; }
.document-content { width: min(900px, calc(100% - 48px)); margin: 0 auto; padding: calc(76px + clamp(56px, 8vw, 100px)) 0 clamp(56px, 8vw, 100px); }.document-content :deep(h1), .document-content :deep(h2), .document-content :deep(h3), .document-content :deep(h4), .document-content :deep(h5), .document-content :deep(h6) { color: #10251f; }.document-content :deep(h1) { margin: 0 0 32px; font-size: clamp(36px, 5vw, 56px); line-height: 1.2; }.document-content :deep(h2) { margin: 48px 0 18px; padding-top: 28px; border-top: 1px solid #d6e2dc; font-size: clamp(24px, 3vw, 32px); line-height: 1.35; }.document-content :deep(h3) { margin: 30px 0 14px; color: #0f8f68; font-size: 20px; line-height: 1.45; }.document-content :deep(p) { margin: 0 0 18px; color: #405851; font-size: 16px; line-height: 1.9; white-space: pre-wrap; }
.information-footer { display: grid; grid-template-columns: 1fr auto; column-gap: 28px; row-gap: 24px; padding: 32px clamp(24px, 7vw, 110px); color: #c2d3cc; border-top: 2px solid #0f8f68; background: #081c17; }.information-footer nav { display: flex; align-items: center; gap: 24px; font-size: 13px; }.information-footer nav a { color: inherit; text-decoration: none; text-underline-offset: 5px; transition: color 160ms ease, text-decoration-color 160ms ease; }.information-footer nav a:hover, .information-footer nav a:focus-visible, .information-footer nav a.router-link-active { color: #58d2a8; text-decoration: underline; }.information-footer > p { justify-self: end; margin: 0; white-space: nowrap; }.information-footer small { grid-column: 2; justify-self: end; color: #78968a; }
@media (max-width: 800px) { .information-nav { height: 68px; padding: 0 20px; }.brand-product, .brand-divider { display: none; }.information-nav nav { gap: 16px; }.document-content { width: min(100% - 40px, 900px); padding-top: 124px; }.document-content :deep(h2) { margin-top: 36px; }.information-footer { grid-template-columns: 1fr; }.information-footer nav { flex-wrap: wrap; }.information-footer > p { justify-self: start; white-space: normal; }.information-footer small { grid-column: 1; justify-self: start; } }
</style>
