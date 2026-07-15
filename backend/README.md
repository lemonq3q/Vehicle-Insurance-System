# Insurance backend

该目录是从原 `InsuranceSystem` 单模块工程拆分出的 Maven 多模块后端，原目录保持不变。

## 模块

- `common`：公共配置、领域模型、权限过滤、异常处理、通用工具及纯架构级系统组件。
- `insruance`：现有车辆保险业务、Mapper、Service、Controller、定时归档任务和原运行配置。模块名按需求保留为 `insruance`。
- `saas`：新的 SaaS 应用入口，当前仅依赖 `common`，暂不包含业务逻辑。默认端口为 `8081`。

`DataArchive` 及 `DailyTaskScheduler` 需要现有保险业务 Mapper，因此保留在 `insruance`；`ArchiveContext` 和 `MaintenanceManager` 位于 `common`，以避免模块循环依赖。

## 构建与运行

```powershell
mvn clean package -DskipTests
java -jar .\insruance\target\insruance-0.0.1-SNAPSHOT.jar
java -jar .\saas\target\saas-0.0.1-SNAPSHOT.jar
```
