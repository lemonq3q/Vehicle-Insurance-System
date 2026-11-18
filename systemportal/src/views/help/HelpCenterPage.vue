<template>
  <section class="help-center">
    <div class="section-title">
      <div>
        <h1>帮助中心</h1>
        <p>通过 iDatag SaaS 官方微信服务号获取操作指南、视频教程与在线咨询。</p>
      </div>
    </div>

    <div class="help-grid">
      <section class="help-hero" aria-labelledby="help-services-title">
        <h2 id="help-services-title">使用平台遇到问题？<br>微信服务号为您提供帮助</h2>
        <div class="help-features">
          <div class="help-feature">
            <h3>📚 使用指南</h3>
            <p>查阅图文操作手册，按步骤学习平台功能。</p>
          </div>
          <div class="help-feature">
            <h3>🎬 视频教程</h3>
            <p>观看系统操作演示，了解常用功能。</p>
          </div>
          <div class="help-feature">
            <h3>💬 在线咨询</h3>
            <p>通过服务号发送消息，反馈使用问题。</p>
          </div>
          <div class="help-feature">
            <h3>🏢 了解平台</h3>
            <p>查看平台介绍、功能信息及相关动态。</p>
          </div>
        </div>
      </section>

      <section class="portal-card help-contact" aria-labelledby="help-contact-title">
        <h2 id="help-contact-title">关注官方微信服务号</h2>
        <p>打开微信「扫一扫」，扫描下方二维码。</p>
        <img class="help-qr" src="@/assets/official-wechat-qr.png"
          alt="iDatag SaaS 官方微信服务号二维码">
        <p class="help-account">iDatag SaaS</p>
        <p class="help-instructions">手机端可保存二维码后在微信中识别，或直接搜索公众号名称。</p>
        <button type="button" class="help-copy" @click="copyAccountName">复制公众号名称</button>
        <p class="help-copy-status" role="status">{{ copyStatus }}</p>
      </section>
    </div>

  </section>
</template>

<script>
/**
 * 门户帮助中心集中展示微信服务号可提供的支持内容，并提供扫码与公众号搜索入口。
 * 页面使用随门户发布的二维码资源，无需请求后台。
 */
export default {
  name: 'HelpCenterPage',
  data() {
    return {
      copyStatus: ''
    };
  },
  methods: {
    /**
     * 将服务号名称写入系统剪贴板，供用户在微信搜索。
     * 剪贴板不可用或写入失败时，在按钮下方提示手动搜索。
     */
    async copyAccountName() {
      this.copyStatus = '';
      try {
        await navigator.clipboard.writeText('iDatag SaaS');
        this.copyStatus = '公众号名称已复制';
      } catch {
        this.copyStatus = '请在微信中手动搜索「iDatag SaaS」';
      }
    }
  }
};
</script>

<style scoped>
.help-center {
  max-width: 1320px;
  container-type: inline-size;
}

.help-grid {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(290px, 2fr);
  gap: 16px;
}

.help-hero {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: clamp(20px, 3vw, 36px);
  border-radius: 20px;
  background: linear-gradient(120deg, #101b34, #0b3659 75%, #006d60);
  color: #fff;
}

.help-hero h2 {
  font-size: clamp(24px, 3vw, 34px);
  font-weight: 700;
  line-height: 1.4;
}

.help-features {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 20px;
}

.help-feature {
  padding: 16px;
  border: 1px solid #ffffff2b;
  border-radius: 12px;
  background: #ffffff0c;
}

.help-feature h3 {
  margin-bottom: 6px;
  font-size: 17px;
  font-weight: 700;
}

.help-feature p {
  color: #c4d6e7;
  line-height: 1.6;
}

.help-contact {
  padding: 16px;
  border-radius: 20px;
  text-align: center;
}

.help-contact h2 {
  margin-bottom: 8px;
  font-size: 20px;
  font-weight: 700;
}

.help-contact p {
  color: var(--portal-muted);
  line-height: 1.7;
}

.help-qr {
  display: block;
  width: clamp(160px, 28dvh, 220px);
  max-width: 100%;
  height: auto;
  margin: 10px auto 6px;
  padding: 8px;
  border: 1px solid #dfe8ee;
  border-radius: 16px;
  background: #fff;
}

.help-contact .help-account {
  color: #117c48;
  font-size: 18px;
  font-weight: 700;
}

.help-contact .help-instructions {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #eef8f1;
  color: #42634e;
  text-align: left;
}

.help-copy {
  margin-top: 10px;
  padding: 8px 16px;
  border: 1px solid #117c48;
  border-radius: 8px;
  background: #fff;
  color: #117c48;
  cursor: pointer;
}

.help-copy-status {
  min-height: 1.7em;
  margin-top: 6px;
}

@container (max-width: 640px) {
  .help-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@container (max-width: 360px) {
  .help-features {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
