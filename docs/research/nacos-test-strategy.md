# Nacos 测试替代策略调研 —— 网关公共 API 黑盒 E2E 主接缝

> 决策 ticket：[#11 调研并验证集成测试中 Nacos 的替代方案](https://github.com/huazaiki/spring-cloud-demo/issues/11)（Part of [#2 决策地图](https://github.com/huazaiki/spring-cloud-demo/issues/2)）
> 关联 Spec：[#1 一期升级：企业级供应链采购管理系统](https://github.com/huazaiki/spring-cloud-demo/issues/1) —— Testing Decisions 主接缝
> 状态：调研完成（只读调研，未改业务代码）｜日期：2026-08-04

## TL;DR（推荐结论）

**主接缝采用「方案 A 测试本地配置」为主、融合「方案 C 的静态指向」思路**：
测试 profile 用 Spring Boot 原生机制覆盖 `spring.config.import` 绕过 Nacos 配置中心，并关闭 Nacos 注册发现；
服务间调用**保留生产 `lb://` 语义**，通过 Spring Cloud Commons 官方支持的 `SimpleDiscoveryClient` 静态实例列表
（`spring.cloud.discovery.client.simple.instances.<service>[0].uri=...`）解析到测试实例——生产路由配置一行不改；
Seata TC 用 `apache/seata-server:2.1.0` 容器 + 客户端 `file` 注册中心直连，不依赖 Nacos。

「方案 B Nacos testcontainer」作为**贴近生产的专项集成测试**备选：Testcontainers 官方没有 Nacos 模块，需用
`GenericContainer` 包装官方 `nacos/nacos-server` 镜像（Spring Cloud Alibaba 2023.x 官方测试即用 `nacos/nacos-server:v2.4.2`），
成本高、稳定性差，不适合作为主接缝默认。

---

## 1. 背景与现状

### 1.1 仓库对 Nacos 的依赖点

| 依赖面 | 现状（以本仓库文件为准） |
|---|---|
| 配置中心 | 每个服务 `src/main/resources/application.yml` 尾部 `spring.config.import: optional:nacos:common.yml?group=DEFAULT_GROUP&refreshEnabled=true` + `optional:nacos:<服务名>.yml?...`；业务配置（数据源、JWT、Kafka、网关路由、Seata）全部托管在 `nacos-config/*.yml` |
| 注册发现 | `spring-cloud-starter-alibaba-nacos-discovery`；网关路由 `uri: lb://sc-auth-service` 等 5 条；Feign 客户端按服务名调用（`sc-purchase-service` 调 `sc-inventory-service` / `sc-supplier-service`） |
| Seata TC 发现 | `sc-purchase/inventory/payment` 三服务 `seata.registry.type: nacos`（application `seata-server`，group `SEATA_GROUP`）经 Nacos 发现 TC |
| 基础设施 | `docker-compose.yml`：`nacos/nacos-server:v3.1.0`（standalone，8848/9848/9090-8080）、MySQL 8.3、Kafka、Zipkin、`seataio/seata-server:2.1.0` |

### 1.2 版本矩阵（以根 `pom.xml` / `docker-compose.yml` 为准）

| 组件 | 版本 | 备注 |
|---|---|---|
| Spring Boot | 3.2.12 | |
| Spring Cloud | 2023.0.3 | spring-cloud-commons 4.1.4 |
| Spring Cloud Alibaba | 2023.0.3.2 | 内置 nacos-client **2.4.2**（见 [starter pom](https://repo1.maven.org/maven2/com/alibaba/cloud/spring-cloud-starter-alibaba-nacos-discovery/2023.0.3.2/spring-cloud-starter-alibaba-nacos-discovery-2023.0.3.2.pom)） |
| Nacos Server（本地） | 3.1.0（镜像 v3.1.0） | 3.x 服务端**兼容 2.x 客户端**（见 §7 汇总表） |
| Seata | 2.1.0（`seata-spring-boot-starter`） | TC 镜像 `seataio/seata-server:2.1.0`（官方另有 `apache/seata-server:2.1.0` 命名空间） |
| Testcontainers | 1.20.4（根 BOM 已托管） | 目前仅 `spring-boot-starter-test`，**尚无任何 Testcontainers 依赖** |

### 1.3 主接缝定义（Spec #1 Testing Decisions）

> Testcontainers 起 MySQL + Kafka（**Nacos 用测试本地配置替代**），6 服务按黑盒拉起，驱动完整闭环：
> 登录→请购→审批→转单→订单审批→收货→质检→入库→发票→付款→核销，断言状态流转与事件幂等消费。
> 跨服务一致性（Seata / Outbox / Kafka）用真实中间件验证，不用 mock 替代。

即 Spec 已定方向为「Nacos 用测试本地配置替代」，本调研验证其可行性并给出实施细节（含 Seata TC 处理）。

---

## 2. 方案 A：测试本地配置（禁用/绕过 spring.config.import，服务直连测试实例）

### 2.1 做法

1. 每个服务增加测试 profile 配置（如 `src/test/resources/application-e2e.yml`），覆盖：
   - `spring.config.import`：覆盖为本地配置（或空列表），彻底绕过 Nacos 配置中心。Spring Boot 的 profile 配置对
     `spring.config.import` 是**整体覆盖**语义（list 属性整体替换，非追加），这是 [Spring Boot 官方 config import 机制](https://docs.spring.io/spring-boot/docs/3.2.12/reference/html/features.html#features.external-config.files.importing)；
   - `spring.cloud.nacos.config.enabled: false` 与 `spring.cloud.nacos.discovery.enabled: false`：关闭 SCA 的 Nacos 配置/发现客户端。
   - 补全原 Nacos 中的业务配置：数据源 URL/账号、JWT、Kafka、Seata（file 直连）、网关路由等。
2. 服务间调用**保留 `lb://` 语义**，用 Spring Cloud Commons 官方 `SimpleDiscoveryClient` 静态实例列表解析：
   ```yaml
   spring:
     cloud:
       discovery:
         client:
           simple:
             instances:
               sc-auth-service[0]: { uri: "http://localhost:18081" }
               sc-supplier-service[0]: { uri: "http://localhost:18082" }
               # ... 其余服务同理
   ```
   官方文档（[Spring Cloud Commons: Common Abstractions](https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/common-abstractions.html)）：
   > If there is no Service-Registry-backed DiscoveryClient in the classpath, SimpleDiscoveryClient ... will be used.
   > `spring.cloud.discovery.client.simple.instances.service1[0].uri=http://s11:8080`
   网关（WebFlux）走 `SimpleReactiveDiscoveryClient`（同一属性源），Feign（阻塞）走 `SimpleDiscoveryClient`。

3. 兜底行为验证（源码级）：SCA 2023.x 的
   [`NacosConfigDataLocationResolver.isResolvable()`](https://github.com/alibaba/spring-cloud-alibaba/blob/2023.x/spring-cloud-alibaba-starters/spring-alibaba-nacos-config/src/main/java/com/alibaba/cloud/nacos/configdata/NacosConfigDataLocationResolver.java)
   绑定 `spring.cloud.nacos.config.enabled`，为 `false` 时不解析 `nacos:` 位置；且
   [`NacosConfigDataLoader.load()`](https://github.com/alibaba/spring-cloud-alibaba/blob/2023.x/spring-cloud-alibaba-starters/spring-alibaba-nacos-config/src/main/java/com/alibaba/cloud/nacos/configdata/NacosConfigDataLoader.java)
   在拉取失败且 `optional:` 时直接 `return null`（不抛 `ConfigDataResourceNotFoundException`）。
   **结论：即使测试 profile 不显式改 `spring.config.import`，Nacos 不可达时 `optional:nacos:` 也会安全跳过**；
   但显式覆盖 import 更清晰、避免误读与未来 `optional:` 移除风险。

### 2.2 优点

- **零新增基础设施**：不起 Nacos 容器，主接缝只起 MySQL + Kafka + Seata TC，启动快、稳定、省 CI 资源。
- **不改生产代码/配置**：生产 `application.yml`、`nacos-config/*.yml` 原封不动；全部替代只发生在测试 profile。
- **`lb://` 语义保留**：网关路由、Feign 调用方式与生产一致，走真实 Spring Cloud LoadBalancer 链路（只是服务实例来源从 Nacos 换成静态列表）。
- **黑盒测试本质匹配**：E2E 测的是业务闭环与一致性（Seata/Outbox/Kafka），Nacos 不是被测对象，替代它不损失测试有效性。

### 2.3 缺点 / 风险

- **配置双份维护**：测试 profile 要复制一份 Nacos 中的业务配置，存在与生产配置漂移的风险；建议测试配置以「覆盖项最少」为原则，并加注释引用 `nacos-config/` 源文件。
- **静态实例列表是"手写拓扑"**：新增服务/改端口需同步测试配置；官方定位 SimpleDiscoveryClient 为测试/演示用途（[spring-cloud-commons PR #684](https://github.com/spring-cloud/spring-cloud-commons/pull/684) 注明 `SimpleDiscoveryClient has been added for testing and demo purposes`）。
- **端口编排**：若 6 服务以测试进程内 `@SpringBootTest` 拉起且用随机端口，静态列表要动态注入（测试基类里根据 `LocalServerPort` 构建 `spring.cloud.discovery.client.simple.instances`）；推荐固定端口（`server.port` 固定或 `webEnvironment = DEFINED_PORT`）降低复杂度。
- **不会发现"真实 Nacos 集成问题"**：例如 `spring.config.import` 拼写、dataId/group 写错这类只在连真实 Nacos 时才暴露的问题，本方案测不到（交由 Nacos 专项集成测试覆盖，见方案 B）。

### 2.4 关键坑

- `spring.cloud.nacos.config.enabled=false` 只是关闭 SCA 客户端；若 `spring.config.import` 仍保留非 `optional:` 的 `nacos:` 条目会启动失败——测试 profile 应统一用 `optional:` 或直接覆盖 import。
- 网关的 JWT 密钥/路由在 `nacos-config/sc-gateway-service.yml`，测试 profile 必须提供（否则网关过滤器初始化失败）。
- `spring.cloud.discovery.client.simple.instances` 的 key 是**服务注册名**（`spring.application.name`），与网关 `lb://` 后面的名字、Feign 的 `@FeignClient(name=...)` 一致。

---

## 3. 方案 B：Nacos testcontainer

### 3.1 Testcontainers 官方支持情况

- **官方 testcontainers-java 没有 Nacos 模块**。官方 `modules/` 目录（[GitHub](https://github.com/testcontainers/testcontainers-java/tree/main/modules)）含 MySQL/Kafka/Redis/Consul/Vault/LocalStack 等 60+ 模块，**无 Nacos**。
- 社区有第三方模块（如 `testcontainers-nacos`），但非官方、维护与质量参差，**不建议引入**。
- 现实做法：用官方 [GenericContainer](https://java.testcontainers.org/features/creating_container/) 包装官方镜像 `nacos/nacos-server`。

### 3.2 官方参考实现

Spring Cloud Alibaba 官方 2023.x 分支的 `spring-cloud-alibaba-tests` 就是**用容器起 Nacos 做集成测试**：
- [`nacos-config-test` / `nacos-discovery-test` 的 `nacos-compose-test.yml`](https://github.com/alibaba/spring-cloud-alibaba/blob/2023.x/spring-cloud-alibaba-tests/nacos-tests/nacos-config-test/src/test/resources/docker/nacos-compose-test.yml)：
  ```yaml
  services:
    nacos:
      image: nacos/nacos-server:v2.4.2
      environment: [PREFER_HOST_MODE=hostname, MODE=standalone]
      ports: ["8848:8848", "9848:9848"]
      healthcheck:
        test: "curl --fail http://127.0.0.1:8848/nacos/v1/console/health/liveness || exit 1"
  ```
  这证明 **SCA 2023.x + Nacos server 2.4.2 容器组合是官方验证过的**。

### 3.3 与本仓库版本组合的可行性

- 本仓库生产 Nacos 用 **3.1.0**（`docker-compose.yml`），客户端是 SCA 2023.0.3.2 内置的 **nacos-client 2.4.2**。
- Nacos 官方[升级手册 · 客户端兼容性](https://nacos-group.github.io/docs/v3.1/manual/admin/upgrading/)明确：**Nacos 3.x 服务端兼容 2.x 客户端**（1.x 也兼容但将在 v3.2 后停止支持）。
- 因此测试既可用官方 SCA 同款 `nacos/nacos-server:v2.4.2`，也可用与生产一致的 `v3.1.0`；推荐后者以贴近生产（但 3.x 首次启动因命名空间迁移可能偏慢，见坑）。
- Nacos [官方 Docker 快速开始](https://nacos.io/zh-cn/docs/latest/quickstart/quick-start-docker/)：`MODE=standalone` 默认**内置 Derby 存储，无需 MySQL**；需暴露 8080（控制台）/8848/9848；默认未开启鉴权（官方注明仅限测试环境）。

### 3.4 优点

- **最贴近生产**：`spring.config.import`、服务注册、`lb://` 路由全部走真实 Nacos，能暴露配置/注册相关的真实集成问题。
- 与 docker-compose 本地环境一致，测试与本地开发行为同构。

### 3.5 缺点 / 风险

- **启动慢、资源占用高**：Nacos 是 JVM 应用（默认堆较大），容器就绪需数秒到数十秒，且需等待 8848+9848（gRPC）双端口就绪；CI 上会显著拉长 E2E 时间。
- **配置灌入复杂度**：每个测试类/每次干净环境都要把 `nacos-config/*.yml` 发布到 Nacos（HTTP OpenAPI 或 `ConfigService` 客户端写入），并处理"配置未就绪→服务启动拉不到"的时序；SCA 官方测试用 docker-compose 预置 + healthcheck 处理。
- **官方无 testcontainer 封装**：没有 `NacosContainer`，要自己写 `GenericContainer` + 就绪探测 + 端口映射逻辑（8848/9848 都要暴露，且 gRPC 端口由主端口偏移计算）。
- **Nacos 3.x 特有坑**：3.0 起 `server.port → nacos.server.main.port` 等配置项大改；升级场景有空/`public` 命名空间迁移逻辑；3.x 默认需 8080 控制台端口，测试容器端口映射需一并考虑（[部署手册概览](https://nacos-group.github.io/docs/v3.1/manual/admin/deployment/deployment-overview/)）。
- **第三方模块不可靠**：引入非官方 `testcontainers-nacos` 会带来额外依赖与潜在协议/版本不兼容风险。

### 3.6 定位

适合做**独立的"Nacos 集成测试"专项**（验证 `spring.config.import`、动态刷新、注册发现与真实 Nacos 的互操作），
或作为主接缝的「更贴近生产」变体；**不建议作为主接缝默认方案**（成本/稳定性/与 Spec「Nacos 用测试本地配置替代」方向相悖）。

---

## 4. 方案 C：网关静态路由指向测试实例

### 4.1 做法

网关测试 profile 直接把路由 `uri` 从 `lb://<service>` 改为固定地址 `http://<host>:<port>`：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: sc-auth-service
          uri: http://localhost:18081
          predicates: [Path=/api/v1/auth/**]
        # ... 其余路由同理
```

Spring Cloud Gateway 官方支持非 `lb://` 的静态 URI 路由（[configuration.adoc v4.1.4](https://github.com/spring-cloud/spring-cloud-gateway/blob/v4.1.4/docs/modules/ROOT/pages/spring-cloud-gateway/configuration.adoc) 示例 `uri: https://example.org`）；
注意 [how-it-works.adoc v4.1.4](https://github.com/spring-cloud/spring-cloud-gateway/blob/v4.1.4/docs/modules/ROOT/pages/spring-cloud-gateway/how-it-works.adoc) 的约束：**route uri 上的路径会被忽略**、无端口时默认 80/443。

### 4.2 优点

- **极简**：只改网关一个 profile，不涉及其他服务的发现配置；适合"只有网关这一个接缝"的场景。
- 直观易排错：路由目标一眼可见。

### 4.3 缺点 / 风险

- **生产路由配置被旁路**：`nacos-config/sc-gateway-service.yml` 的路由定义在测试中失效，测试覆盖不到"路由配置本身"（但 E2E 测的是业务闭环，可接受）。
- **端口硬编码**：若服务以随机端口启动（`@SpringBootTest(webEnvironment = RANDOM_PORT)`），静态 uri 无法预知端口；要么固定端口，要么测试代码动态改写路由（增加复杂度）。
- **只解决网关→下游**：服务间 Feign 调用（purchase→inventory/supplier）仍走 `lb://`，仍需 SimpleDiscoveryClient 静态列表（方案 A 第 2 步）——所以**纯方案 C 不完整**，实际是 C 与 A 组合。
- 与方案 A 相比：A 的静态实例列表方案**不改生产路由配置**，C 要在测试里维护一份路由副本（与 Nacos 中的路由配置漂移风险）。

### 4.4 定位

C 是 A 的「网关侧」变体：当团队倾向"测试配置里显式写死目标地址"时可用；
**推荐在方案 A 的静态实例列表基础上，把网关也并入同一机制**（保留 `lb://` + SimpleDiscoveryClient），避免维护两份路由清单。

---

## 5. Seata TC（seata-server）在集成测试中的处理

### 5.1 推荐：起 seata-server 容器（GenericContainer）

- 镜像：`apache/seata-server:2.1.0`（官方 [Deploy Seata Server By Docker (v2.1.0)](https://seata.apache.org/docs/v2.1/ops/deploy-by-docker/)；本仓库 docker-compose 用同版本的 `seataio/seata-server:2.1.0`）。
- 环境变量：`SEATA_PORT=8091`、`STORE_MODE=file`（默认 file 存储，无需数据库）；容器内以 file 注册/配置中心运行，不依赖 Nacos。
- 客户端（purchase/inventory/payment 三个参与服务）测试 profile 改为 file 直连：
  ```yaml
  seata:
    enabled: true
    tx-service-group: sc_tx_group
    registry:
      type: file
    config:
      type: file
    service:
      vgroup-mapping:
        sc_tx_group: default
      grouplist:
        default: <seata-host>:<mapped-port>   # Testcontainers 映射后的 host:port（默认 8091）
  ```
  Seata 官方[参数配置文档](https://seata.apache.org/docs/v2.1/user/configurations/)明确：`registry.type=file` 的初衷就是**不依赖第三方注册/配置中心、直连 TC 快速验证**；`service.default.grouplist`（spring-boot starter 中写作 `seata.service.grouplist.default`）是 TC 直连地址。

### 5.2 备选：禁用 Seata（不推荐为主）

- `seata.enabled: false` 可彻底关掉 TM/RM 代理，最简单；
- 但 Spec 要求「跨服务一致性（Seata AT 短链路）用真实中间件验证，不用 mock 替代」，主接缝覆盖"订单审批→预留库存"短链路时应保留 Seata，因此**仅建议**在「不涉及 Seata 链路的旁路场景」或 Seata 专项测试之外才禁用。

### 5.3 嵌入式 TC：无官方支持，不推荐

- Seata 是「独立 TC 进程 + TM/RM 客户端」架构，官方不提供面向测试的嵌入式 TC；
- 社区有通过 `Server` 类程序化启动 TC 的做法，但配置繁琐、非官方、易与版本行为脱节，不建议用于主接缝。

### 5.4 坑

- **`undo_log` 表**：AT 模式要求每个参与库有 `undo_log` 表；本仓库 `docker/mysql/init/03-seata-undo-log.sql` 已提供（测试库初始化需包含它）。
- **容器 IP 注册**：file 模式下客户端直连 TC 用映射地址即可；若保留 Nacos 注册 TC，则 TC 需以 `SEATA_IP` 指定可被测试网络访问的 IP，且 Nacos 必须可用（与方案 A 冲突，故推荐 file 直连）。
- **XID 传播**：`SeataFeignConfig` 的 `TX_XID` 请求头传播依赖 `@GlobalTransactional` 正常开启；`seata.enabled` 或 registry 配错会表现为"无 XID/分支不注册"，排障先看三个参与服务的 `seata.registry.type` 是否统一为 file。

---

## 6. 推荐方案与实施要点

### 6.1 推荐：方案 A + 静态指向（融入 C），Seata TC 容器化

```
┌─────────────────────────────── 测试 profile（每个服务 src/test/resources/application-e2e.yml）──┐
│ spring.config.import: []                        # 绕过 Nacos 配置中心（optional:nacos 已不可达）│
│ spring.cloud.nacos.config.enabled: false        # 关闭 SCA Nacos 配置客户端                      │
│ spring.cloud.nacos.discovery.enabled: false     # 关闭 SCA Nacos 注册发现                         │
│ spring.cloud.discovery.client.simple.instances: # 静态实例列表 → lb:// 仍可解析                  │
│   sc-auth-service[0].uri: http://host:port      #   （网关走 SimpleReactiveDiscoveryClient）      │
│   ...                                            #   （Feign 走 SimpleDiscoveryClient）           │
│ seata.registry.type: file + grouplist: host:port # Seata TC 直连（apache/seata-server:2.1.0 容器）│
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 理由

1. **与 Spec 对齐**：主接缝已定为「Nacos 用测试本地配置替代」，方案 A 是字面实现；Nacos 并非 E2E 被测对象。
2. **成本与稳定性**：不起 Nacos 容器 → 启动快、无 8848/9848 就绪时序、无配置灌入逻辑；CI 资源与 flake 面都最小。
3. **保真度足够**：`lb://` + LoadBalancer + Feign/网关真实链路保留；Seata/Outbox/Kafka 全部真实中间件，满足 Spec「不 mock 中间件」。
4. **生产配置零改动**：替代全部收敛在测试 profile；生产 `application.yml`、`nacos-config/*.yml` 不动。

### 6.3 建议实施骨架

- 在根 pom 的 dependencyManagement 已有 `testcontainers-bom 1.20.4`，各服务测试依赖加：
  `org.testcontainers:mysql`、`org.testcontainers:kafka`、`org.testcontainers:junit-jupiter`（Seata 用 `org.testcontainers:testcontainers` 的 GenericContainer）。
- 建共享测试基类：启动 MySQL + Kafka + seata-server 三个容器（`@Testcontainers(parallel = false)`），导出端口到 `System.setProperty` 或 `DynamicPropertySource`。
- 6 服务黑盒拉起：建议**固定端口 + 独立进程（或每服务一个独立 Spring 上下文、固定 server.port）**，静态实例列表/网关 uri 写固定端口；若用随机端口，则在测试基类动态构建
  `spring.cloud.discovery.client.simple.instances` 与网关路由属性。
- 测试配置以「覆盖项最少」为原则，业务配置源仍指向 `nacos-config/*.yml`（注释标注 dataId/group），降低双份漂移。

### 6.4 何时升级到方案 B

当出现以下需求时，单独加一个「Nacos 集成专项测试」（方案 B，GenericContainer + nacos-server，灌入 `nacos-config/*.yml` 后断言服务能正确拉取配置/注册/路由）：
- 验证 `spring.config.import`、dataId/group、动态刷新（`refreshEnabled=true`）等真实 Nacos 行为；
- 验证 Nacos 3.x（生产 3.1.0）与 SCA 2023.0.3.2 客户端 2.4.2 的互操作回归。

---

## 7. 可行性与版本兼容性 · 已知坑汇总

| 项 | 结论 | 来源/依据 |
|---|---|---|
| `optional:nacos:` 在 Nacos 不可达时是否阻塞启动 | **不阻塞**：loader 在 optional 且失败时返回 null；resolver 在 `config.enabled=false` 时不解析 | SCA 2023.x 源码 `NacosConfigDataLoader` / `NacosConfigDataLocationResolver` |
| 禁用 SCA Nacos 客户端 | `spring.cloud.nacos.config.enabled=false` / `spring.cloud.nacos.discovery.enabled=false`（SCA 属性） | SCA 配置属性体系 |
| `lb://` 无注册中心时如何解析 | `SimpleDiscoveryClient`（阻塞）/ `SimpleReactiveDiscoveryClient`（响应式），属性 `spring.cloud.discovery.client.simple.instances.<svc>[i].uri` | Spring Cloud Commons 官方文档 |
| Testcontainers 是否有官方 Nacos 模块 | **无**；需 GenericContainer + `nacos/nacos-server` 镜像 | testcontainers-java `modules/` 目录 |
| SCA 2023.0.3.2 + Nacos server 3.x 兼容性 | 客户端 2.4.2，**3.x 服务端兼容 2.x 客户端**；官方 2023.x 测试用 Nacos 2.4.2 容器，仓库生产用 3.1.0 | Nacos 官方升级手册 / SCA `nacos-compose-test.yml` / 本仓库 docker-compose |
| Nacos 容器端口 | standalone 需 8848（HTTP）+ 9848（客户端 gRPC）+ 8080（3.x 控制台）；2.x/3.x 客户端按主端口偏移计算 gRPC 端口 | Nacos 部署手册概览 / Docker 快速开始 |
| Nacos 存储 | standalone 默认内置 Derby，无需 MySQL | Nacos 部署手册概览 |
| Nacos 鉴权 | Docker 快速开始默认**未开启鉴权，仅限测试**；本仓库 compose 显式 `NACOS_AUTH_ENABLE=false` | Nacos Docker 快速开始 / docker-compose.yml |
| 网关静态路由 | 支持 `uri: http://host:port`；**route uri 上的路径被忽略**；无端口默认 80/443 | Spring Cloud Gateway 4.1.4 官方文档 |
| Seata TC | 容器 `apache/seata-server:2.1.0` + `STORE_MODE=file`；客户端 `registry.type=file` + `service.grouplist.default` 直连 | Seata 官方 Docker 部署 / 参数配置 |
| Seata 嵌入式 TC | 无官方支持，不推荐 | Seata 架构（独立 TC 进程） |
| AT 模式前置 | 参与库需 `undo_log` 表（仓库 `docker/mysql/init/03-seata-undo-log.sql`） | 本仓库初始化 SQL / Seata AT 文档 |
| Nacos 3.x 特有 | `server.port→nacos.server.main.port` 等配置大改；空/public 命名空间迁移（升级场景） | Nacos 官方升级手册 |

---

## 8. 来源

- Spec #1（Testing Decisions 主接缝）：https://github.com/huazaiki/spring-cloud-demo/issues/1
- 决策 ticket #11：https://github.com/huazaiki/spring-cloud-demo/issues/11
- Spring Boot `spring.config.import` 机制（3.2.x）：https://docs.spring.io/spring-boot/docs/3.2.12/reference/html/features.html#features.external-config.files.importing
- SCA 2023.x `NacosConfigDataLoader` 源码：https://github.com/alibaba/spring-cloud-alibaba/blob/2023.x/spring-cloud-alibaba-starters/spring-alibaba-nacos-config/src/main/java/com/alibaba/cloud/nacos/configdata/NacosConfigDataLoader.java
- SCA 2023.x `NacosConfigDataLocationResolver` 源码：https://github.com/alibaba/spring-cloud-alibaba/blob/2023.x/spring-cloud-alibaba-starters/spring-alibaba-nacos-config/src/main/java/com/alibaba/cloud/nacos/configdata/NacosConfigDataLocationResolver.java
- SCA 2023.0.3.2 `spring-cloud-starter-alibaba-nacos-discovery` pom（nacos-client 2.4.2）：https://repo1.maven.org/maven2/com/alibaba/cloud/spring-cloud-starter-alibaba-nacos-discovery/2023.0.3.2/spring-cloud-starter-alibaba-nacos-discovery-2023.0.3.2.pom
- SCA 2023.x `nacos-config-test` docker-compose：https://github.com/alibaba/spring-cloud-alibaba/blob/2023.x/spring-cloud-alibaba-tests/nacos-tests/nacos-config-test/src/test/resources/docker/nacos-compose-test.yml
- Spring Cloud Commons（SimpleDiscoveryClient）：https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/common-abstractions.html
- Spring Cloud Gateway 4.1.4 how-it-works：https://github.com/spring-cloud/spring-cloud-gateway/blob/v4.1.4/docs/modules/ROOT/pages/spring-cloud-gateway/how-it-works.adoc
- Spring Cloud Gateway 4.1.4 configuration（静态 uri 示例）：https://github.com/spring-cloud/spring-cloud-gateway/blob/v4.1.4/docs/modules/ROOT/pages/spring-cloud-gateway/configuration.adoc
- testcontainers-java 官方模块目录（无 Nacos）：https://github.com/testcontainers/testcontainers-java/tree/main/modules
- Testcontainers GenericContainer：https://java.testcontainers.org/features/creating_container/
- Nacos 升级手册（3.x 服务端 ↔ 2.x 客户端兼容）：https://nacos-group.github.io/docs/v3.1/manual/admin/upgrading/
- Nacos 部署手册概览（端口/存储/单机模式）：https://nacos-group.github.io/docs/v3.1/manual/admin/deployment/deployment-overview/
- Nacos Docker 快速开始：https://nacos.io/zh-cn/docs/latest/quickstart/quick-start-docker/
- Seata Docker 部署（v2.1.0）：https://seata.apache.org/docs/v2.1/ops/deploy-by-docker/
- Seata 参数配置（file registry / grouplist）：https://seata.apache.org/docs/v2.1/user/configurations/
- 本仓库：`pom.xml`、`docker-compose.yml`、`nacos-config/*.yml`、`docs/adr/0002-transaction-consistency.md`、各服务 `src/main/resources/application.yml`