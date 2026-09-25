const net = require('net');

/**
 * 在 Vue CLI 启动前验证约定的开发端口是否可用。端口被占用时直接终止启动，避免 CLI 自动寻找
 * 后续端口后造成跨系统回调、代理地址和开发书签指向错误的前端实例。
 *
 * @param {string} process.argv[2] 各前端在项目中固定使用的 TCP 端口。
 * @param {string} process.argv[3] 用于错误提示的应用名称。
 */
const port = Number(process.argv[2]);
const application = process.argv[3] || 'frontend';

if (!Number.isInteger(port) || port < 1 || port > 65535) {
  console.error(`[${application}] 开发端口配置无效：${process.argv[2] || '未配置'}`);
  process.exit(1);
}

const server = net.createServer();
server.unref();

server.once('error', error => {
  if (error.code === 'EADDRINUSE') {
    console.error(`[${application}] 固定开发端口 ${port} 已被占用，已停止启动。请先释放该端口。`);
  } else {
    console.error(`[${application}] 无法检查固定开发端口 ${port}：${error.message}`);
  }
  process.exit(1);
});

server.listen({ port, host: '127.0.0.1', exclusive: true }, () => {
  server.close(error => {
    if (error) {
      console.error(`[${application}] 释放端口检查套接字失败：${error.message}`);
      process.exit(1);
    }
  });
});
