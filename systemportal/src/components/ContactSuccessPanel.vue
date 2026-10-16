<template>
  <div class="contact-success-panel">
    <div class="success-content">
      <section class="success-summary" aria-labelledby="contact-success-title">
        <div class="success-brand"><span>idatag</span><small>SaaS</small></div>
        <div class="success-heading"><span class="success-check" aria-hidden="true">✓</span><h2 id="contact-success-title">提交成功</h2></div>
        <p class="success-description" role="status">您的合作需求已提交，我们会尽快与您联系。</p>
        <div class="success-artwork">
          <img src="@/assets/brand/contact-success-illustration.png" alt="" />
          <p>让车险更简单<br />让服务更高效</p>
        </div>
      </section>
      <section class="service-section" aria-labelledby="wechat-service-title">
        <div class="service-banner">
          <span class="service-wechat" aria-hidden="true"><i class="layui-icon layui-icon-login-wechat"></i></span>
          <div><span class="service-badge">推荐关注</span><h3 id="wechat-service-title">idatag SaaS 微信服务号</h3><p>微信扫一扫，关注服务号，后续服务更方便</p></div>
        </div>
        <div class="service-details">
          <figure class="service-qr">
            <div class="qr-card"><img src="@/assets/brand/idatag-wechat-service-qr.svg" alt="idatag SaaS 微信服务号二维码，请使用微信扫一扫关注" width="1176" height="1176" /><figcaption>idatag SaaS 微信服务号</figcaption></div>
            <p>扫码关注 · 获取持续服务</p>
          </figure>
          <ul class="service-features">
            <li><span aria-hidden="true"><i class="layui-icon layui-icon-app"></i></span><div><h4>了解产品</h4><p>了解 idatag SaaS<br />功能及使用方法</p></div></li>
            <li><span aria-hidden="true"><i class="layui-icon layui-icon-dialogue"></i></span><div><h4>在线咨询</h4><p>咨询平台使用及<br />车险相关问题</p></div></li>
            <li><span aria-hidden="true"><i class="layui-icon layui-icon-file"></i></span><div><h4>查询保单</h4><p>后续通过微信服务号<br />获取保单查询指引</p></div></li>
          </ul>
        </div>
      </section>
    </div>
    <footer class="success-footer">
      <div class="submission-reference"><i class="layui-icon layui-icon-file" aria-hidden="true"></i><div><p>提交编号：<strong>{{ submissionNumber }}</strong></p><small>如需咨询合作进度，可提供此提交编号。</small></div></div>
      <button ref="completeButton" class="success-complete" type="button" @click="$emit('close')">完成</button>
    </footer>
  </div>
</template>

<script>
/**
 * 联系表单成功后的确认面板，以后端游客提交编号证明本次申请已被接收。
 * 二维码使用原图解析出的服务号地址生成的清晰矢量资产；组件不再发送申请或自动打开微信。
 * 完成按钮通知父页面关闭弹窗，手机号等联系表单敏感信息不会在本面板展示。
 */
export default {
  name: 'ContactSuccessPanel',
  props: {
    submissionNumber: { type: String, required: true }
  },
  emits: ['close'],
  /**
   * 提交成功切换内容后将焦点送到完成按钮，方便键盘用户关闭结果面板。
   * 保持父级 Escape 和关闭按钮的既有行为，不新增接口或持久化状态。
   */
  mounted() {
    this.$refs.completeButton.focus({ preventScroll: true });
  }
};
</script>

<style scoped>
.contact-success-panel { color: #074c55; background: linear-gradient(135deg, #fff 35%, #f3fbfa); }
.success-content { display: grid; grid-template-columns: .86fr 1.14fr; }
.success-summary { padding: 34px 38px 24px; border-right: 1px solid #e5eeef; }
.success-brand { display: inline-flex; flex-direction: column; align-items: center; color: #08789d; line-height: 1; }
.success-brand>span { position: relative; font-size: 40px; font-weight: 800; letter-spacing: -1.5px; }
.success-brand>span::before { position: absolute; top: 0; left: 2px; width: 6px; height: 6px; border-radius: 50%; background: #29ba7c; content: ''; }
.success-brand small { margin-top: 4px; font-size: 21px; font-weight: 600; }
.success-heading { display: flex; align-items: center; gap: 20px; margin-top: 34px; }
.success-check { display: grid; place-items: center; width: 66px; height: 66px; flex-shrink: 0; border-radius: 50%; background: linear-gradient(135deg, #20b584, #3ccc72); color: #fff; font-size: 46px; font-weight: 700; }
.success-heading h2 { margin: 0; color: #074c55; font-size: clamp(30px, 3vw, 42px); font-weight: 800; }
.success-description { margin: 20px 0 0; padding-bottom: 28px; border-bottom: 1px solid #e2ecee; color: #607d90; font-size: 17px; line-height: 1.85; }
.success-artwork { display: flex; align-items: center; min-height: 230px; margin: 18px -18px 0; }
.success-artwork img { width: 66%; min-width: 0; height: auto; }
.success-artwork p { position: relative; flex-shrink: 0; margin-left: -10px; color: #0ca477; font-size: 19px; font-weight: 600; line-height: 1.7; transform: rotate(-8deg); }
.success-artwork p::after { display: block; width: 90%; height: 3px; margin-top: 8px; background: #0aa275; border-radius: 100%; content: ''; }
.service-section { min-width: 0; padding: 42px 34px 28px; }
.service-banner { display: flex; align-items: center; gap: 20px; padding: 20px 22px; border-radius: 16px; background: #e9f7f3; }
.service-wechat { display: grid; place-items: center; width: 72px; height: 72px; flex-shrink: 0; border-radius: 50%; background: linear-gradient(135deg, #10a873, #008f71); color: #fff; }
.service-wechat i { font-size: 45px; }
.service-badge { display: inline-block; padding: 4px 13px; border-radius: 12px; color: #fff; background: #099f73; font-size: 14px; font-weight: 700; }
.service-banner h3 { margin: 8px 0 7px; font-size: clamp(21px, 2.2vw, 28px); font-weight: 800; line-height: 1.35; }
.service-banner p { margin: 0; font-size: 15px; line-height: 1.65; }
.service-details { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; margin-top: 22px; }
.service-qr { min-width: 0; margin: 0; }
.qr-card { overflow: hidden; border: 1px solid #e9f1ef; border-radius: 14px; background: #fff; box-shadow: 0 4px 20px rgba(25, 96, 86, .04); }
.qr-card img { display: block; width: 100%; height: auto; aspect-ratio: 1; }
.qr-card figcaption { padding: 12px 4px; background: #eff9f5; color: #00785d; text-align: center; font-size: 14px; font-weight: 700; }
.service-qr>p { margin: 16px 0 0; color: #607d90; font-size: 14px; text-align: center; }
.service-features { margin: 0; padding: 0 18px; list-style: none; border-radius: 14px; background: #f1f9f8; }
.service-features li { display: flex; gap: 14px; align-items: flex-start; padding: 17px 0; border-bottom: 1px solid #dfeceb; }
.service-features li:last-child { border: 0; }
.service-features li>span { display: grid; place-items: center; width: 47px; height: 47px; flex-shrink: 0; border-radius: 50%; background: #069e76; color: #fff; }
.service-features i { font-size: 27px; }
.service-features h4 { margin: 1px 0 7px; font-size: 17px; font-weight: 800; }
.service-features p { margin: 0; color: #607d90; font-size: 13px; line-height: 1.8; }
.success-footer { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 23px 38px; border-top: 1px solid #e5eeef; background: rgba(255,255,255,.85); }
.submission-reference { display: flex; align-items: flex-start; gap: 12px; min-width: 0; color: #607d90; }
.submission-reference>i { padding: 7px; border-radius: 50%; background: #ecf4f8; color: #83aac2; font-size: 20px; }
.submission-reference p { margin: 0 0 6px; font-size: 14px; line-height: 1.8; }
.submission-reference strong { color: #066584; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.submission-reference small { font-size: 12px; }
.success-complete { min-width: 180px; min-height: 50px; padding: 12px 30px; border: 0; border-radius: 9px; background: linear-gradient(120deg, #08a077, #14a369); color: #fff; font-size: 18px; font-weight: 700; cursor: pointer; }
.success-complete:hover { filter: brightness(.95); }
.success-complete:focus-visible { outline: 3px solid #78d4b2; outline-offset: 3px; }
@media (max-width: 1000px) {
  .success-summary { padding: 30px 26px; }
  .service-section { padding: 38px 24px 28px; }
  .service-banner { gap: 12px; padding: 16px; }
  .service-wechat { width: 55px; height: 55px; }.service-wechat i { font-size: 36px; }
  .service-details { gap: 14px; }.service-features { padding: 0 12px; }.service-features li { gap: 9px; }
  .service-features li>span { width: 38px; height: 38px; }.success-artwork p { font-size: 16px; }
}
@media (max-width: 760px) {
  .success-content { grid-template-columns: 1fr; }.success-summary { border-right: 0; border-bottom: 1px solid #e5eeef; padding: 26px 24px 22px; }
  .success-brand>span { font-size: 32px; }.success-brand small { font-size: 17px; }
  .success-heading { margin-top: 23px; }.success-heading h2 { font-size: 32px; }.success-check { width: 54px; height: 54px; font-size: 36px; }
  .success-description { margin-top: 14px; padding: 0; border: 0; font-size: 15px; }.success-artwork { display: none; }
  .service-section { padding: 24px; }.service-banner h3 { font-size: 20px; }.service-banner p { font-size: 13px; }
  .service-details { grid-template-columns: 1fr; }.service-qr { width: min(100%, 270px); margin: 0 auto; }
  .service-features { margin-top: 8px; }.service-features li { align-items: center; }.service-features p br { display: none; }
  .success-footer { flex-direction: column; align-items: stretch; gap: 18px; padding: 22px 24px; }.success-complete { width: 100%; }
}
</style>
