// vue.config.js - 统一使用 CommonJS 语法（Vue CLI 推荐）
const path = require('path'); 
const { defineConfig } = require('@vue/cli-service');

module.exports = defineConfig({
  publicPath: '/',
  // 转译依赖
  transpileDependencies: true,
  
  // 开发服务器配置（Vue CLI 用 devServer）
  devServer: {
    port: 8888, // 自定义端口号
  },
  
  // Webpack 配置
  configureWebpack: {
    resolve: {
      alias: {
        // 配置 @ 指向 src 目录
        "@": path.resolve(__dirname, "src"),
      },
    }
  }
});