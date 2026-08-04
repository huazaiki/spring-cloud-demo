# CI 流水线与 Nacos 多环境配置方案

> 决策 ticket：「设计 CI 流水线与 Nacos 多环境配置方案」（[#12](https://github.com/huazaiki/spring-cloud-demo/issues/12)）的产出。
> 依据：Q8 决策、测试策略（#14 / Spec Testing Decisions）。

## 1. CI 流水线（GitHub Actions）

文件：`.github/workflows/ci.yml`（已落地，见 roadmap「质量基建」）

```yaml
name: ci
on:
  push:
    branches: [ main ]
  pull_request:

jobs:
  build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - run: mvn -B verify   # 单测+集成测试（Testcontainers）+契约+JaCoCo 覆盖率门禁
      - run: mvn -B flyway:validate   # schema 脚本完整性
  docker:
    if: github.ref == 'refs/heads/main'
    needs: build-test
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [sc-gateway-service, sc-auth-service, sc-supplier-service, sc-purchase-service, sc-inventory-service, sc-payment-service]
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with: { registry: ghcr.io, username: ${{ github.actor }}, password: ${{ secrets.GITHUB_TOKEN }} }
      - uses: docker/build-push-action@v6
        with:
          context: .
          file: docker/${{ matrix.service }}/Dockerfile
          push: true
          tags: ghcr.io/${{ github.repository }}/${{ matrix.service }}:${{ github.sha }}
```

要点：
- **门禁**：`mvn verify` 内含 JaCoCo 覆盖率检查（低于 60% fail）；`flyway:validate` 校验迁移脚本。
- **镜像**：仅 main 构建推送；tag 用 commit SHA；Dockerfile 多阶段（maven 构建 → temurin:17-jre 运行），位置 `docker/<service>/Dockerfile`。
- **集成测试**：跑在支持 Docker 的 runner 上（Testcontainers 起 MySQL/Kafka）。

## 2. Nacos 多环境

### namespace 规划

| namespace | 用途 | 说明 |
|---|---|---|
| dev | 本地/开发 | 默认；开发者本地 `.env` 指向本地中间件 |
| prod | 生产 | 与 dev 完全隔离；密钥注入 |

### 配置分层（同一 namespace 内，优先级从低到高）

1. `common.yml`：全服务共享（MyBatis-Plus、通用项）——不含密钥。
2. `sc-{service}.yml`：服务专属（数据源 URL 占位、Feign、Kafka、Seata）。
3. `sc-{service}-{env}.yml`：环境专属（数据源地址、jwt.secret 引用、日志级别）。

每个服务 `spring.config.import` 按环境加载三层；`refreshEnabled=true` 支持热更新。

### 密钥管理

- **移除明文**：`jwt.secret`、`spring.datasource.password` 不再写死在 yml。
- **注入方式**：CI secrets（GitHub Actions `secrets.*`）→ 启动参数/环境变量 → 服务以 `${JWT_SECRET}` 占位引用；本地 `.env` 提供 dev 值（已 gitignore）。
- 生产二期接 KMS/Vault；Nacos 开启鉴权 + 独立 namespace。

## 3. 容器化与部署形态

- 每个服务独立 Dockerfile（多阶段构建），`docker-compose.yml` 增加 6 个服务定义（build 或镜像引用），一期以 compose 一键拉起为部署形态。
- 服务间用服务名直连（compose 网络），与 `lb://` 注册发现并存（本地 compose 可配 Nacos 或简单静态）。
- 二期再评估 K8s/Helm/灰度（roadmap Out of scope）。

## 4. 与本地开发的关系

- 开发者：`docker compose up -d`（中间件）→ `mvn -pl <svc> spring-boot:run`（dev namespace）。
- 新增配置项必须同时更新 `nacos-config/` 源文件与导入脚本，保持"配置源文件=唯一真相"。