const { defineConfig } = require('@vue/cli-service');

module.exports = defineConfig({
  publicPath: process.env.VUE_APP_PUBLIC_PATH || '/',
  transpileDependencies: true,
  devServer: {
    historyApiFallback: true,
    // 监控系统固定使用 8889；门户使用独立端口，避免 SSO 回调被监控前端接收。
    port: 8887
  }
});
