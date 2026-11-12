# 测试环境服务

在 `47.100.210.159` 部署，域名入口为 `lmy.idatag.com`。四个服务保留正式环境端口映射，数据库切换到 `insurance_saas_dev`；正式 `idatag.target` 和模板不修改。

| 服务 | 端口 |
| --- | --- |
| insruance | 8080 |
| saas | 8081 |
| coordinator | 8082 |
| monitor | 8083 |

测试实例使用相同端口，适用于独立测试服务器，不能在同一服务器与已占用这些端口的正式实例并行运行。

部署文件：

- `idatag_dev.target`、`idatag_dev@.service` 放到 `/etc/systemd/system/`。
- 将四个 JAR（名称与正式版相同）放入 `/usr/idatag_dev/`。
- 将 `idatag_dev.env.example` 复制为 `/usr/idatag_dev/.env`，填写测试数据库账号密码；两套数据库用户名/密码变量保持一致，密钥更换为测试环境专用值，Redis 选择未用于正式环境的库。
- 测试续保提醒时，在测试环境 `.env` 设置 `RENEWAL_CYCLE_MONTHS=1`，重新启动 `insruance` 和 `saas` 两个服务。配置默认值为 `12`；恢复正常周期时删除该变量或设为 `12` 后再次重启。缩短周期会改变已保存的跟进轮次含义，请只在独立测试库使用，不要用正式业务数据测试。
- 先准备好 `insurance_saas_dev` 及其所需表结构/迁移。启动配置不会创建、复制或修改数据库。
- 该服务模板显式传入通用与 Druid 数据源 URL，覆盖 JAR 中正式库默认值；使用 prod 配置档运行，但内部调用与前端跳转均由独立环境文件指向测试服务。
- `docs/nginx/lmy.idatag.com` 部署在入口 Nginx 服务器，和正式域名配置一起启用；测试前端部署在该入口服务器的 `/var/www/idatag_dev/{saas,insurance,monitor}/dist/`。`47.100.210.159` 只运行测试后端，无需安装 Nginx 或监听443端口；前端通过 `npm run build:dev` 构建，明确使用 lmy 接口域名。详见 `docs/nginx/README.md` 和 `docs/frontend/environment-build.md`。

上传并填写配置后，在测试服务器执行：

```bash
sudo chmod 600 /usr/idatag_dev/.env
sudo systemd-analyze verify /etc/systemd/system/idatag_dev.target /etc/systemd/system/idatag_dev@.service
sudo systemctl daemon-reload
sudo systemctl start idatag_dev.target
sudo systemctl status idatag_dev@insruance.service idatag_dev@saas.service idatag_dev@monitor.service idatag_dev@coordinator.service
```

常用操作：

```bash
sudo systemctl stop idatag_dev.target
sudo systemctl restart idatag_dev.target
sudo systemctl enable idatag_dev.target
sudo tail -n 100 /usr/idatag_dev/insruance.log
```

`enable` 为可选开机启动设置。Target 本身显示 active 不代表每个 Java 应用已就绪，应检查四个实例状态及各自日志。

本次仅新增配置和说明，没有连接服务器、启动服务或执行数据库变更。

