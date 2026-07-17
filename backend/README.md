# Insurance backend

该目录是从原 `InsuranceSystem` 单模块工程拆分出的 Maven 多模块后端，原目录保持不变。

## 模块

- `common`：公共配置、领域模型、权限过滤、异常处理、通用工具及纯架构级系统组件。
- `insruance`：现有车辆保险业务、Mapper、Service、Controller、定时归档任务和原运行配置。模块名按需求保留为 `insruance`。
- `saas`：SaaS 门户业务应用，包含登录、企业、成员、订阅、财务、用户中心及维护任务。默认端口为 `8081`。

`DataArchive` 及 `DailyTaskScheduler` 需要现有保险业务 Mapper，因此保留在 `insruance`；`ArchiveContext` 和 `MaintenanceManager` 位于 `common`，以避免模块循环依赖。

## 构建与运行

```powershell
mvn clean package -DskipTests
java -jar .\insruance\target\insruance-0.0.1-SNAPSHOT.jar
java -jar .\saas\target\saas-0.0.1-SNAPSHOT.jar
```

本地启动 `saas` 前请通过环境变量提供数据库连接信息，不要把密码提交到配置文件：

```powershell
$env:INSURANCE_DB_URL='jdbc:mysql://localhost:3306/insurance_saas?characterEncoding=utf-8&serverTimezone=Asia/Shanghai'
$env:INSURANCE_DB_USERNAME='root'
$env:INSURANCE_DB_PASSWORD='<本机数据库密码>'
java -jar .\saas\target\saas-0.0.1-SNAPSHOT.jar
```

Redis 默认连接 `localhost:6379`，也可通过 `INSURANCE_REDIS_HOST`、`INSURANCE_REDIS_PORT` 和 `INSURANCE_REDIS_PASSWORD` 覆盖。
