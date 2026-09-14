const { defineConfig } = require('@vue/cli-service');

/**
 * 监控前端开发服务固定使用 8889，并将 /api/monitor 转发到独立监控后端的 /monitor。
 * 生产环境由网关提供同样的路径映射，也可通过 VUE_APP_API_BASE_URL 覆盖完整接口前缀。
 */
module.exports = defineConfig({
  publicPath: process.env.VUE_APP_PUBLIC_PATH || '/',
  transpileDependencies: true,
  devServer: {
    port: 8889,
    proxy: {
      '/api/monitor': {
        target: process.env.MONITOR_DEV_BACKEND_URL || 'http://127.0.0.1:8083',
        changeOrigin: true,
        pathRewrite: { '^/api/monitor': '/monitor' }
      }
    }
  }
});
