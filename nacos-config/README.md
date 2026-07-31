# Nacos 配置中心

本项目将业务配置统一托管到 Nacos，本地 `application.yml` 只保留启动引导所需的极简配置。

## 版本兼容性

| 组件 | 版本 |
|---|---|
| Spring Cloud Alibaba | `2023.0.3.2` |
| nacos-client（由 BOM 锁定） | `2.4.2` |
| Nacos Server（docker-compose） | `v3.1.0` |

nacos-client `2.4.x` 与 Nacos Server `3.x` 兼容，无需额外调整依赖。

## 设计：共享 + 各服务配置

采用 `spring.config.import` 机制（Spring Boot 3 原生方式，无需 bootstrap.yml / spring-cloud-starter-bootstrap）。
每个服务导入两份配置，**后者覆盖前者**：

1. `common.yml` —— 全服务共享（mybatis-plus、数据源账号密码与驱动）。
2. `<服务名>.yml` —— 服务专属（数据源 URL、jwt、kafka、gateway 路由等）。

本地 `application.yml` 仅保留：

- `server.port`、`spring.application.name`
- `spring.cloud.nacos.server-addr`（discovery 与 config 共享）
- `spring.cloud.nacos.config.group` / `file-extension`
- `spring.config.import`（导入列表）

### 目录结构

```
nacos-config/
├── common.yml            # 共享配置
├── auth-service.yml      # dataId = auth-service.yml
├── supplier-service.yml
├── purchase-service.yml
├── inventory-service.yml
├── payment-service.yml
├── gateway-service.yml
├── import-to-nacos.sh    # 一键导入脚本
└── README.md
```

> Nacos 中 dataId 即文件名，group 统一为 `DEFAULT_GROUP`，namespace 为默认 `public`。

## 导入配置到 Nacos

### 方式一：脚本导入（推荐）

先启动 Nacos（`docker compose up -d nacos`），然后在仓库根目录执行：

```bash
bash nacos-config/import-to-nacos.sh
# 或指定地址：
bash nacos-config/import-to-nacos.sh 192.168.1.10:8848
# 若开启了鉴权：
NACOS_USER=nacos NACOS_PASSWORD=nacos bash nacos-config/import-to-nacos.sh
```

脚本通过 `POST /nacos/v1/cs/configs` 发布，逐个打印结果。

### 方式二：控制台手动导入

浏览器打开 http://localhost:9090 （docker-compose 将主机 9090 映射到 Nacos 控制台 8080），
在「配置管理 → 配置列表」中按上表 dataId/group 逐个新建，格式选 YAML，内容粘贴对应文件。

## 工作机制说明

- `spring.config.import: optional:nacos:<dataId>?group=...&refreshEnabled=true`
  - `optional:` 前缀表示配置不存在时不阻断启动（便于 Nacos 未就绪时本地调试）。
  - `refreshEnabled=true` 开启该配置的动态刷新（修改后推送至服务，`@RefreshScope` Bean 生效）。
- `spring.cloud.nacos.server-addr` 为 discovery 与 config 共享简写；
  `config.group` / `file-extension` 仅作用于配置中心。
- 导入顺序决定优先级：`common.yml` 在前（低优先级），`<服务名>.yml` 在后（高优先级，覆盖共享项）。

## 注意事项

- `jwt.secret`、数据库密码等敏感信息目前明文存于 Nacos。docker-compose 中
  `NACOS_AUTH_ENABLE=false`，生产环境应开启鉴权、使用独立 namespace，或接入外部密钥管理。
- `JwtAuthFilter` 通过构造器 `@Value` 注入 `jwt.secret`，启动时从 Nacos 加载，但运行时
  修改不会热更新（需重启生效）；如需热更新需改造为 `@RefreshScope` 并重建密钥。
- Gateway 路由从 Nacos 加载为静态路由；动态路由刷新需额外引入响应式路由刷新机制。
- 修改 Nacos 中的配置后，引用该配置且标注 `@RefreshScope` 的 Bean 会自动刷新。
  纯配置属性（如 `server.port`）不会热更新，需重启服务。
