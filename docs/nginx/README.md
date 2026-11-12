# 正式与测试域名共用入口 Nginx

两份域名配置都部署在有 Nginx 的入口服务器。`47.100.210.159` 只部署测试 Java 后端及测试数据库，不需要 Nginx。

| 域名 | DNS 指向 | 前端目录（入口服务器） | 接口目标 |
| --- | --- | --- | --- |
| www.idatag.com | 入口 Nginx 服务器 IP | 原 `/var/www/{saas,insurance,monitor}/dist/` | 原正式配置 |
| lmy.idatag.com | 同一入口 Nginx 服务器 IP | `/var/www/idatag_dev/{saas,insurance,monitor}/dist/` | 47.100.210.159 的8081/8080/8083 |

两份配置分别使用 `server_name` 区分域名，可共用80/443端口。文件名后缀 `.com` 只是配置文件名，不是需要运行的程序；前提是 nginx.conf 已包含 `/etc/nginx/sites-enabled/*`。

## 入口服务器操作

1. 保留现有正式站点，把仓库 `lmy.idatag.com` 上传到 `/etc/nginx/sites-available/lmy.idatag.com`。
2. 为 lmy 域名配置 DNS，并在入口服务器准备其 HTTPS 证书，路径与配置中的 `ssl_certificate`、`ssl_certificate_key` 一致。
3. 分别在 systemportal、frontend、monitor-frontend 执行 `npm run build:dev`，把生成的 `dist-dev` 内容上传至三个测试前端 `dist` 目录，确保各目录下直接存在 `index.html`。测试构建明确使用 lmy 接口地址，正式构建保持原相对路径；详见 `docs/frontend/environment-build.md`。
4. 确认正式和测试两份配置都在 sites-enabled 中启用；已经存在的链接无需重复创建：

```bash
sudo ln -s /etc/nginx/sites-available/www.idatag.com /etc/nginx/sites-enabled/www.idatag.com
sudo ln -s /etc/nginx/sites-available/lmy.idatag.com /etc/nginx/sites-enabled/lmy.idatag.com
sudo nginx -t && sudo systemctl reload nginx
```

若使用的 Nginx 安装方式只加载 `conf.d/*.conf`，可分别放为 `www.idatag.com.conf`、`lmy.idatag.com.conf`；只选一种加载方式，避免重复加载相同 server 配置。

入口服务器需要能访问 `47.100.210.159:8080`、`:8081`、`:8083`。目标服务器服务启动及数据库配置见 `docs/service/README-dev.md`，使用 `idatag_dev.target` 和 `insurance_saas_dev`。

当前仓库只保存配置，尚未在服务器启用或验证连通性；本机无 Nginx，部署时须运行上述语法检查。
