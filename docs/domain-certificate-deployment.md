# 域名证书部署与自动续期（acme.sh）

适用于本项目的香港 Nginx 入口服务器。www.idatag.com 与 lmy.idatag.com 均解析至香港入口；大陆测试服务器 47.100.210.159 只运行后端，不在该机签发本项目入口证书。

## 项目路径

| 域名 | HTTP 验证目录 | Nginx 证书目录 |
| --- | --- | --- |
| www.idatag.com | /var/www/www.idatag.com | /etc/nginx/ssl/www.idatag.com |
| lmy.idatag.com | /var/www/lmy.idatag.com | /etc/nginx/ssl/lmy.idatag.com |

每个证书目录中，fullchain.pem 对应 ssl_certificate，privkey.pem 对应 ssl_certificate_key。不要让 Nginx 直接引用 acme.sh 内部工作目录。使用 --install-cert 登记部署路径与 reload 钩子，让续期后的新证书被 Nginx 实际加载。

以下命令按在香港服务器以 root 登录、acme.sh 安装于 /root/.acme.sh 编写。如果原先由其他账号安装，应沿用那个账号及其 acme.sh 路径，并保证证书安装和 Nginx reload 权限，不要额外安装第二套或覆盖原续期任务。

## 一、确认现有证书管理方式

在香港服务器执行：

```bash
/root/.acme.sh/acme.sh --version
/root/.acme.sh/acme.sh --list
crontab -l
```

本项目无法从本地确认服务器上现有的证书签发账号、CA、验证方式和 cron。上述检查不会改变已有证书。正式 www 证书如果已经正常续期，保持原设置；本流程给 lmy 单独签发证书，不重签或替换 www。

若现有 www 证书含有 *.idatag.com，可能已覆盖 lmy，可先查看：

```bash
openssl x509 -in /etc/nginx/ssl/www.idatag.com/fullchain.pem -noout -ext subjectAltName
```

www.idatag.com 单域名证书本身不覆盖 lmy.idatag.com。本说明仍以给 lmy 单独签发为准。不要对同一张通配符证书反复用 --install-cert 写不同目标目录，以免覆盖其原安装记录。

## 二、首次给 lmy.idatag.com 签发证书

### 1. 配置 DNS 与网络

在域名解析控制台添加 A 记录：主机记录 lmy，记录值为香港 Nginx 公网 IP，不是 47.100.210.159。如存在 AAAA 记录，也必须指向能正确提供验证文件的入口；否则先纠正记录。入口的80、443端口需要可访问。

检查解析：

```bash
dig +short A lmy.idatag.com
dig +short AAAA lmy.idatag.com
```

### 2. 先启用只监听80的临时站点

尚未有证书时，不要直接启用仓库里引用证书的完整 HTTPS 配置，否则 nginx -t 会因文件不存在而失败。

创建验证目录：

```bash
mkdir -p /var/www/lmy.idatag.com/.well-known/acme-challenge
```

编辑 /etc/nginx/sites-available/lmy.idatag.com，首次签发前暂时仅放下面内容：

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name lmy.idatag.com;

    location ^~ /.well-known/acme-challenge/ {
        root /var/www/lmy.idatag.com;
        default_type text/plain;
        try_files $uri =404;
    }

    location / {
        return 200 "lmy certificate setup\n";
        default_type text/plain;
    }
}
```

使用 sites-enabled 的安装方式，首次创建链接；已存在则跳过，不要重复创建：

```bash
ln -s /etc/nginx/sites-available/lmy.idatag.com /etc/nginx/sites-enabled/lmy.idatag.com
nginx -t && systemctl reload nginx
```

如果服务器改用 conf.d/*.conf，只在该目录配置一次，不重复加载。保持 www 正式站点原配置不变。

### 3. 验证 HTTP 文件可从外部访问

```bash
printf 'acme-check\n' > /var/www/lmy.idatag.com/.well-known/acme-challenge/check.txt
```

在本地电脑或另一台机器执行：

```bash
curl http://lmy.idatag.com/.well-known/acme-challenge/check.txt
```

必须返回 acme-check。返回其他页面、404或超时，先解决 DNS、Nginx、80端口或访问控制问题，再签发。

### 4. 使用已有 acme.sh 签发独立 ECC 证书

```bash
/root/.acme.sh/acme.sh --issue \
  --server letsencrypt \
  -d lmy.idatag.com \
  -w /var/www/lmy.idatag.com \
  --keylength ec-256
```

本例为 lmy 明确选择 Let's Encrypt，不改变既有 www 证书的 CA 或全局默认 CA。若已有 CAA 记录限制签发机构，需要允许选用的 CA。已经签发过 lmy 时先检查 --list，沿用其现有记录，不反复强制申请。

### 5. 安装证书，并登记自动 reload

```bash
install -d -m 700 /etc/nginx/ssl/lmy.idatag.com

/root/.acme.sh/acme.sh --install-cert -d lmy.idatag.com --ecc \
  --key-file /etc/nginx/ssl/lmy.idatag.com/privkey.pem \
  --fullchain-file /etc/nginx/ssl/lmy.idatag.com/fullchain.pem \
  --reloadcmd "nginx -t && systemctl reload nginx"

chmod 600 /etc/nginx/ssl/lmy.idatag.com/privkey.pem
```

--ecc 与本次 ECC 签发对应。对于已有 RSA 证书，应使用其正确记录而非机械添加 --ecc。--install-cert 安装时会调用 reload 钩子；此时临时 HTTP 站点仍可正常 reload。

### 6. 替换为项目完整配置

通过 Xftp 将本地 docs/nginx/lmy.idatag.com 上传并覆盖香港服务器的 /etc/nginx/sites-available/lmy.idatag.com。它已经包含正确证书路径，并保留 HTTP 验证 location。

```bash
nginx -t && systemctl reload nginx
```

浏览器访问 https://lmy.idatag.com。证书签发/启用与测试前端、后端部署是独立步骤；页面404或接口502不一定是证书问题，仍需部署前后端。

## 三、自动续期怎样生效

完整链路：acme.sh 定时检查 → 到期前自动续期 → --install-cert 登记的文件位置更新 → 执行 reloadcmd → Nginx 加载新证书。

- 保留原安装账号的 cron；已有任务不重复添加。
- 保留80端口验证 location、验证目录，以及正确 DNS，后续 webroot 续期仍会使用它们。
- 不要只手工复制 acme.sh 内部文件，否则可能出现证书已续期而 Nginx 仍使用旧文件。
- reload 钩子必须有权限执行；原配置由非 root 用户管理时应正确配置授权。
- 已经使用 DNS API 自动验证的正式证书可以继续原方式；不要改成手工 DNS 验证来期望无人值守续期。

查看任务及证书：

```bash
crontab -l
/root/.acme.sh/acme.sh --list
openssl x509 -in /etc/nginx/ssl/lmy.idatag.com/fullchain.pem -noout -subject -issuer -dates
```

检查公网实际提供的证书（不仅检查磁盘文件）：

```bash
openssl s_client -connect lmy.idatag.com:443 -servername lmy.idatag.com </dev/null 2>/dev/null | openssl x509 -noout -subject -issuer -dates
```

需要人工运行一次正常续期检查时，可以执行下列命令；它会检查该账号管理的证书，不要反复使用 --force：

```bash
/root/.acme.sh/acme.sh --cron --home /root/.acme.sh
```

## 四、常见问题

| 现象 | 检查方向 |
| --- | --- |
| nginx -t 提示证书文件不存在 | 先用临时80配置签发并安装证书，再启用完整配置 |
| HTTP验证返回404 | lmy是否命中正确server、root是否为/var/www/lmy.idatag.com、验证文件是否存在 |
| 验证超时 | DNS是否指向香港入口、80端口是否开放、AAAA是否错误 |
| 签发成功但浏览器仍显示旧证书 | install-cert目标路径、reloadcmd是否成功、是否访问另一台入口/CDN |
| 续期失败 | 原安装账号cron、验证目录、DNS与端口、CA/账户错误信息 |
| 续期成功但HTTPS过期 | Nginx是否引用已安装路径，reload钩子是否有权限且nginx -t通过 |

## 官方参考与本次验证范围

- [acme.sh 官方项目](https://github.com/acmesh-official/acme.sh)
- [官方中文说明](https://github.com/acmesh-official/acme.sh/wiki/说明)

本文按官方签发、安装及续期机制与项目现有 Nginx 路径编写。本次只生成说明，未连接服务器、未申请证书、未修改已有续期配置；服务器实际安装账号与现有CA需按第一节核对。

