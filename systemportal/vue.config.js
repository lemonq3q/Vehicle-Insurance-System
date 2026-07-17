const { defineConfig } = require('@vue/cli-service');

module.exports = defineConfig({
  transpileDependencies: true,
  devServer: {
    historyApiFallback: true,
    proxy: {
      '/portal': {
        target: process.env.PORTAL_API_TARGET || 'http://127.0.0.1:8081',
        changeOrigin: true
      }
    }
  }
});
