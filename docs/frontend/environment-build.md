# 正式与测试前端打包

三套前端均使用 Vue CLI，分别在对应项目目录执行命令。

| 命令 | 配置文件 | 接口目标 | 输出目录 |
| --- | --- | --- | --- |
| `npm run build` | `.env.production` | 保留原同域相对接口路径 | `dist` |
| `npm run build:dev` | `.env.dev` | `https://lmy.idatag.com` 的对应接口路径 | `dist-dev` |
| `npm run serve` | `.env.development` | 原本机开发配置 | 本机开发服务 |

测试包仍使用生产优化的编译方式（NODE_ENV=production）；dev 表示部署目标，不是启动本机调试服务。三个测试接口前缀分别为 `/insuranceback`、`/saasback`、`/api/monitor`，公共路由与资源路径仍为 `/insurance/`、`/saas/`、`/monitor/`。

在仓库根目录可依次执行：

```powershell
npm --prefix frontend run build:dev
npm --prefix systemportal run build:dev
npm --prefix monitor-frontend run build:dev
```

上传测试产物至香港 Nginx 入口服务器，与 `docs/nginx/lmy.idatag.com` 对应：

| 本机产物内容 | 入口服务器目录 |
| --- | --- |
| `frontend/dist-dev/` | `/var/www/idatag_dev/insurance/dist/` |
| `systemportal/dist-dev/` | `/var/www/idatag_dev/saas/dist/` |
| `monitor-frontend/dist-dev/` | `/var/www/idatag_dev/monitor/dist/` |

上传的是 dist-dev 内的内容，目标 dist 目录下应直接存在 index.html。之后访问 `https://lmy.idatag.com`，浏览器页面和接口均使用测试域名。

打包配置决定接口目标，不会自动改变浏览器地址或DNS；仍须从测试域名打开页面。登录跳转、微信和支付回调由后端及第三方配置决定，沿用独立测试 `.env` 中的 lmy 地址，不能只修改前端包。

`.env.dev` 只含公开地址及开关，可以随代码管理。机器已有的环境变量和 `.env.dev.local` 可能覆盖文件值，构建前应确认没有残留正式环境的 VUE_APP_API_BASE_URL；环境变量在打包时固定，修改后需重新构建。
