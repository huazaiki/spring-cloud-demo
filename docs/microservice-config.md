# 微服务配置详解

> 本文档详解本仓库（spring-cloud-demo，Procure-to-Pay 供应链采购-入库-付款演示项目）中
> 各个微服务的配置信息：配置存放位置、加载机制、每个配置项的用途、在代码中的消费点，
> 以及基础设施（MySQL / Nacos / Kafka / Zipkin）的相关配置。
>
> 配套文档：[`nacos-config/README.md`](../nacos-config/README.md)（配置中心设计与导入方式）。

---

## 1. 配置架构总览

本项目采用 **本地引导配置 + Nacos 配置中心** 的两层配置架构：

```
┌─────────────────────────────────────────────────────────────┐
│ 本地 application.yml（每个服务）                                │
│    server.port / spring.application.name                      │
│    nacos server-addr / config.group / file-extension          │
│    spring.config.import（决定从 Nacos 拉哪些配置）                 │
└──────────────────────────┬──────────────────────────────────┘
                           │ spring.config.import（Spring Boot 原生机制）
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ Nacos 配置中心（nacos-config/ 目录为源文件，可脚本导入）              │
│   common.yml             ← 全服务共享配置                        │
│   sc-gateway-service.yml ← 网关专属                              │
│   sc-auth-service.yml    ← 认证服务专属                           │
│   sc-supplier-service.yml← 供应商服务专属                          │
│   sc-purchase-service.yml← 采购服务专属                           │
│   sc-inventory-service.yml← 库存服务专属                          │
│   sc-payment-service.yml ← 付款服务专属                           │
└─────────────────────────────────────────────────────────────┘
```

**设计原则**：业务配置（数据源、JWT、Kafka、路由等）全部托管到 Nacos，本地 `application.yml`
只保留启动引导所需的极简配置，避免每个服务重复维护，并支持运行时动态刷新。

### 1.1 版本信息（以 pom.xml 为准）

| 组件 | 版本 |
|---|---|
| Spring Boot | 3.2.12 |
| Spring Cloud | 2023.0.3 |
| Spring Cloud Alibaba | 2023.0.3.2 |
| MyBatis-Plus | 3.5.9 |
| MySQL Connector/J | 8.3.0 |
| jjwt | 0.12.6 |
| Seata（spring-cloud-starter-alibaba-seata） | 2.1.0 |
| Testcontainers | 1.20.4 |
| Java | 17 |

> ⚠️ 注意：根 `README.md` 技术栈表格写的是 Spring Boot 4.1.0 / Spring Cloud 2025.0.0 /
> Cloud Alibaba 2025.0.0.0，与实际 `pom.xml`（3.2.12 / 2023.0.3 / 2023.0.3.2）不一致，
> 实际以 pom.xml 为准。

### 1.2 配置加载机制（spring.config.import）

每个服务的本地 `application.yml` 尾部都有：

```yaml
spring:
  config:
    import:
      - optional:nacos:common.yml?group=DEFAULT_GROUP&refreshEnabled=true
      - optional:nacos:<服务名>.yml?group=DEFAULT_GROUP&refreshEnabled=true
```

关键点：

| 片段 | 含义 |
|---|---|
| `optional:` 前缀 | 配置不存在时不阻断启动（便于 Nacos 未就绪时本地调试） |
| `nacos:<dataId>` | 从 Nacos 拉取的配置标识，dataId 即配置文件名 |
| `group=DEFAULT_GROUP` | 配置所属分组，本项目统一使用 `DEFAULT_GROUP` |
| `refreshEnabled=true` | 开启该配置的动态刷新，修改后推送到服务，`@RefreshScope` Bean 生效 |

**优先级**：`spring.config.import` 列表**后者覆盖前者**，即 `<服务名>.yml` 覆盖 `common.yml`。
数据源用户名/密码/驱动在 `common.yml` 定义，各服务的 URL 在专属配置中定义，二者合并生效。

---

## 2. 共享配置：common.yml

文件：`nacos-config/common.yml` → Nacos dataId `common.yml`，group `DEFAULT_GROUP`
（所有服务都导入）。

```yaml
mybatis-plus:
  global-config:
    db-config:
      id-type: assign_id
  configuration:
    map-underscore-to-camel-case: true

spring:
  datasource:
    username: root
    password: root@123!
    driver-class-name: com.mysql.cj.jdbc.Driver
```

| 配置项 | 值 | 说明 |
|---|---|---|
| `mybatis-plus.global-config.db-config.id-type` | `assign_id` | 主键策略：使用雪花算法（Snowflake）生成分布式 Long 主键。所有表主键为 `BIGINT`，与 MyBatis-Plus 配合 |
| `mybatis-plus.configuration.map-underscore-to-camel-case` | `true` | 开启数据库下划线字段名 ↔ Java 驼峰属性自动映射（如 `available_qty` ↔ `availableQty`） |
| `spring.datasource.username` | `root` | MySQL 用户名（所有服务共用） |
| `spring.datasource.password` | `root@123!` | MySQL 密码（所有服务共用） |
| `spring.datasource.driver-class-name` | `com.mysql.cj.jdbc.Driver` | MySQL 8 驱动类 |

> ⚠️ 密码为明文，且需与 docker-compose 中 MySQL 的 `MYSQL_ROOT_PASSWORD` 保持一致。
> 当前 `.env` 中 `MYSQL_ROOT_PASSWORD=root123`，而 `common.yml` 密码为 `root@123!`，**两者不一致**
> （docker-compose 创建的 root 密码是 `root123`，服务实际连接密码是 `root@123!`）。本地开发若连不上库，
> 请先统一两处密码。

---

## 3. sc-gateway-service（API 网关，端口 8080）

### 3.1 本地 application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: sc-gateway-service
  cloud:
    nacos:
      server-addr: localhost:8848
      config:
        group: DEFAULT_GROUP
        file-extension: yml
  config:
    import:
      - optional:nacos:common.yml?group=DEFAULT_GROUP&refreshEnabled=true
      - optional:nacos:sc-gateway-service.yml?group=DEFAULT_GROUP&refreshEnabled=true
```

| 配置项 | 值 | 说明 |
|---|---|---|
| `server.port` | `8080` | 网关监听端口，所有外部请求的统一入口 |
| `spring.application.name` | `sc-gateway-service` | 服务名，同时作为 Nacos 注册名（其他服务按此名发现/调用） |
| `spring.cloud.nacos.server-addr` | `localhost:8848` | Nacos 地址，**同时作用于服务注册发现（discovery）与配置中心（config）** |
| `spring.cloud.nacos.config.group` | `DEFAULT_GROUP` | 拉取配置的分组 |
| `spring.cloud.nacos.config.file-extension` | `yml` | 配置文件扩展名（配合 config 相关机制使用） |

### 3.2 Nacos 配置：sc-gateway-service.yml

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: sc-auth-service
          uri: lb://sc-auth-service
          predicates:
            - Path=/api/v1/auth/**
        - id: sc-supplier-service
          uri: lb://sc-supplier-service
          predicates:
            - Path=/api/v1/suppliers/**
        - id: sc-purchase-service
          uri: lb://sc-purchase-service
          predicates:
            - Path=/api/v1/orders/**
        - id: sc-inventory-service
          uri: lb://sc-inventory-service
          predicates:
            - Path=/api/v1/inventory/**
        - id: sc-payment-service
          uri: lb://sc-payment-service
          predicates:
            - Path=/api/v1/payments/**

jwt:
  secret: change-me-to-a-long-random-string-at-least-256-bits
```

**路由表**

| 路由 id | uri | Path 断言 | 转发目标 |
|---|---|---|---|
| `sc-auth-service` | `lb://sc-auth-service` | `/api/v1/auth/**` | 认证服务 :8081 |
| `sc-supplier-service` | `lb://sc-supplier-service` | `/api/v1/suppliers/**` | 供应商服务 :8082 |
| `sc-purchase-service` | `lb://sc-purchase-service` | `/api/v1/orders/**` | 采购服务 :8083 |
| `sc-inventory-service` | `lb://sc-inventory-service` | `/api/v1/inventory/**` | 库存服务 :8084 |
| `sc-payment-service` | `lb://sc-payment-service` | `/api/v1/payments/**` | 付款服务 :8085 |

- `lb://` 前缀表示通过 **Spring Cloud LoadBalancer + Nacos 服务发现**按服务名负载均衡转发，无需写死实例地址。
- 路由为**静态路由**，从 Nacos 加载；动态路由刷新需额外引入响应式路由刷新机制。

**JWT 鉴权配置**

| 配置项 | 值 | 说明 |
|---|---|---|
| `jwt.secret` | `change-me-to-...-at-least-256-bits` | JWT 签名密钥，必须 ≥ 256 bit（HS256）。**必须与 sc-auth-service 的 `jwt.secret` 保持一致**，否则网关验签失败 |

### 3.3 配置在代码中的消费点

- `com.huazaiki.gateway.filter.JwtAuthFilter`（`sc-gateway-service/.../filter/JwtAuthFilter.java`）
  - 构造器通过 `@Value("${jwt.secret}")` 注入 `jwt.secret`，启动时用 `Keys.hmacShaKeyFor` 构建密钥。
  - 全局过滤器（`GlobalFilter`，order = -100）：
    - 放行公开路径：`/api/v1/auth/register`、`/api/v1/auth/login`。
    - 其余请求校验 `Authorization: Bearer <token>`，验签失败返回 401。
    - 校验通过后向转发请求注入 `X-User-Id`、`X-User-Role` 头，供下游服务读取用户身份。
- 路由配置由 Spring Cloud Gateway 自动装配，无需额外代码。

### 3.4 依赖要点

`spring-cloud-starter-gateway`（响应式网关）、`spring-cloud-starter-loadbalancer`（lb:// 负载均衡）、
`spring-cloud-starter-alibaba-nacos-discovery/config`、`jjwt-*`、`spring-boot-starter-actuator`、
`micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`（链路追踪）。

---

## 4. sc-auth-service（认证服务，端口 8081）

### 4.1 本地 application.yml

与网关结构一致，仅端口与服务名不同：

| 配置项 | 值 | 说明 |
|---|---|---|
| `server.port` | `8081` | 认证服务端口 |
| `spring.application.name` | `sc-auth-service` | 服务名 / Nacos 注册名 |
| nacos / config.import | 同网关 | 导入 `common.yml` + `sc-auth-service.yml` |

### 4.2 Nacos 配置：sc-auth-service.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/auth_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai

jwt:
  secret: change-me-to-a-long-random-string-at-least-256-bits
  expiration-ms: 3600000
```

| 配置项 | 值 | 说明 |
|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/auth_db?...` | 认证库连接串；`auth_db` 存放 `sys_user` 表 |
| — `useSSL=false` | 关闭 SSL（本地演示） |
| — `allowPublicKeyRetrieval=true` | 允许客户端获取服务器公钥（配合 caching_sha2_password 认证，本地开发常见） |
| — `serverTimezone=Asia/Shanghai` | 时区设置为上海，避免时间偏移 |
| `jwt.secret` | 同网关 | 签发 JWT 的密钥，**必须与网关一致** |
| `jwt.expiration-ms` | `3600000` | token 有效期，单位毫秒，1 小时 |

> ⚠️ **已知不一致**：Nacos 文件头部注释写的是 `dataId: auth-service.yml`，但实际 dataId 应为
> `sc-auth-service.yml`（`spring.config.import` 与文件名都是 `sc-auth-service.yml`）。注释属历史遗留，建议修正。
> `sc-supplier-service.yml` 头部注释同样写的是 `supplier-service.yml`。

### 4.3 配置在代码中的消费点

- `com.huazaiki.auth.config.SecurityConfig`：
  - `SecurityFilterChain`：关闭 CSRF、无状态会话（STATELESS）、`/api/v1/auth/**` 放行、其余需认证。
  - `PasswordEncoder`：`BCryptPasswordEncoder` 做密码哈希。
  - `JwtUtil` Bean：**注意当前是硬编码** `"change-me-to-a-long-random-string-at-least-256-bits"` 和
    `3600000L`，并未从 Nacos 的 `jwt.secret` / `jwt.expiration-ms` 读取。若修改 Nacos 中的这两个配置，
    **sc-auth-service 不会生效**（网关会生效）。建议改造为 `@Value` 注入或 `@ConfigurationProperties`。
- `com.huazaiki.auth.config.JwtUtil`：`generateToken(userId, role)` 生成 HS256 签名 token，
  subject = userId，claim = role，过期时间来自 `expirationMs`。
- `com.huazaiki.auth.service.AuthService`：注册（用户名查重、BCrypt 加密入库）、登录（校验密码、签发 token）。

### 4.4 依赖要点

`spring-boot-starter-web`、`spring-boot-starter-security`、`mybatis-plus-spring-boot3-starter`、
`mysql-connector-j`、nacos discovery/config、`jjwt-*`、actuator、tracing。

---

## 5. sc-supplier-service（供应商服务，端口 8082）

### 5.1 本地 application.yml

| 配置项 | 值 | 说明 |
|---|---|---|
| `server.port` | `8082` | 供应商服务端口 |
| `spring.application.name` | `sc-supplier-service` | 服务名 / Nacos 注册名 |
| nacos / config.import | 同网关 | 导入 `common.yml` + `sc-supplier-service.yml` |

### 5.2 Nacos 配置：sc-supplier-service.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/supplier_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
```

| 配置项 | 值 | 说明 |
|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/supplier_db?...` | 供应商库连接串；`supplier_db` 存放 `supplier` 表 |

该服务是纯 CRUD 主数据服务，除共享配置外**只有数据源 URL 一项专属配置**，
说明其不依赖 Kafka / Feign / JWT 等额外组件。

### 5.3 配置在代码中的消费点

- `com.huazaiki.supplier.service.SupplierService`：供应商增删改查、状态更新。
- 无 `@Value` / `@ConfigurationProperties` 直接消费自定义配置项，仅依赖数据源。

### 5.4 依赖要点

`spring-boot-starter-web`、`mybatis-plus-spring-boot3-starter`、`mysql-connector-j`、
nacos discovery/config、actuator、tracing。**没有** openfeign / kafka / security。

---

## 6. sc-purchase-service（采购服务，端口 8083）

### 6.1 本地 application.yml

| 配置项 | 值 | 说明 |
|---|---|---|
| `server.port` | `8083` | 采购服务端口 |
| `spring.application.name` | `sc-purchase-service` | 服务名 / Nacos 注册名 |
| nacos / config.import | 同网关 | 导入 `common.yml` + `sc-purchase-service.yml` |

### 6.2 Nacos 配置：sc-purchase-service.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/purchase_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 5000
            read-timeout: 5000
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

| 配置项 | 值 | 说明 |
|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/purchase_db?...` | 采购库连接串；`purchase_db` 存放 `purchase_order`、`purchase_order_item` 表 |
| `spring.cloud.openfeign.client.config.default.connect-timeout` | `5000` | Feign 默认连接超时（毫秒），5 秒 |
| `spring.cloud.openfeign.client.config.default.read-timeout` | `5000` | Feign 默认读超时（毫秒），5 秒 |
| `spring.cloud.openfeign.circuitbreaker.enabled` | `true` | 为每个 Feign 客户端启用 Resilience4j 熔断 |
| `resilience4j.circuitbreaker.instances.sc-inventory-service` / `sc-supplier-service` | 滑动窗口 10、失败率阈值 50%、开路 10s | Feign 调用熔断；开路/超时由 fallback 降级（详见 §15） |
| `resilience4j.timelimiter.instances.*` | `5s` | 与 Feign read-timeout 对齐的调用超时 |
| `seata.enabled` / `seata.tx-service-group` | `true` / `sc_tx_group` | Seata 全局事务开关与事务分组（TM，详见 §15） |
| `seata.registry.nacos.*` | `localhost:8848`、`SEATA_GROUP` | 通过 Nacos 发现 Seata Server（application=seata-server, cluster=default） |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka 集群地址（KRaft 单节点） |
| `spring.kafka.producer.key-serializer` | `StringSerializer` | 消息 key 序列化器：String |
| `spring.kafka.producer.value-serializer` | `JsonSerializer` | 消息 value 序列化器：JSON |

### 6.3 配置在代码中的消费点

- **Feign**：`com.huazaiki.purchase.feign.InventoryFeignClient`（`/api/v1/inventory/reserve`、
  `/api/v1/inventory/receive`）、`com.huazaiki.purchase.feign.SupplierFeignClient`（`/api/v1/suppliers/{id}`）。
  启动类标注 `@EnableFeignClients`，通过 `@FeignClient(name = "sc-inventory-service" / "sc-supplier-service")`
  按服务名 + LoadBalancer 调用下游，超时时间由上面 openfeign 配置控制。
  - `OrderService.createOrder`：先 Feign 校验供应商存在，再落库订单/明细，再逐条 Feign 预留库存。
  - **Seata**：`createOrder` / `approveOrder` 标注 `@GlobalTransactional`，Feign 调用库存预留纳入全局事务；XID 通过 `SeataFeignConfig` 的 `RequestInterceptor`（`TX_XID` 请求头）传播到下游。
  - **Resilience4j**：启用 Feign 熔断，`sc-inventory-service` / `sc-supplier-service` 独立熔断；开路/超时时 fallback 返回 503，`OrderService` 检查响应码并抛出 `BusinessException` 触发全局回滚。
  - `OrderService.approveOrder`：订单 DRAFT → APPROVED，再逐条 Feign 预留库存。
- **Kafka**：`spring.kafka.producer.*` 已配置（String/JSON 序列化），但**当前代码中未发现
  `KafkaTemplate` / 生产消息的调用**——配置是为“入库完成 → 事件 → 付款生成应付”的事件流预留的。
- 数据源由 MyBatis-Plus 使用。

### 6.4 依赖要点

`spring-cloud-starter-openfeign`、`spring-cloud-starter-loadbalancer`、`spring-kafka`、
`mybatis-plus-spring-boot3-starter`、`mysql-connector-j`、nacos discovery/config、actuator、tracing。

---

## 7. sc-inventory-service（库存服务，端口 8084）

### 7.1 本地 application.yml

| 配置项 | 值 | 说明 |
|---|---|---|
| `server.port` | `8084` | 库存服务端口 |
| `spring.application.name` | `sc-inventory-service` | 服务名 / Nacos 注册名 |
| nacos / config.import | 同网关 | 导入 `common.yml` + `sc-inventory-service.yml` |

### 7.2 Nacos 配置：sc-inventory-service.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/inventory_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

| 配置项 | 值 | 说明 |
|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/inventory_db?...` | 库存库连接串；`inventory_db` 存放 `item`、`inventory`、`receive_record` 表 |
| `seata.enabled` / `seata.tx-service-group` | `true` / `sc_tx_group` | Seata 全局事务开关与事务分组（RM，详见 §15） |
| `seata.registry.nacos.*` | `localhost:8848`、`SEATA_GROUP` | 通过 Nacos 发现 Seata Server |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka 地址 |
| `spring.kafka.producer.*` | String / Json 序列化器 | 同采购服务，预留事件发送能力（如“入库完成”事件） |

### 7.3 配置在代码中的消费点

- `com.huazaiki.inventory.service.InventoryService`：
  - `receiveItem`：写入 `receive_record`，更新/新建 `inventory` 可用库存（`available_qty`）。
  - `reserveStock`：校验可用库存充足后，`available_qty` 减少、`reserved_qty` 增加。
  - `createItem` / `listItems`：物料 CRUD。
  - **Seata**：作为 RM 参与全局事务（需 `undo_log` 表，见 `docker/mysql/init/03-seata-undo-log.sql`）。
- Kafka producer 配置已就绪，但代码中暂无发送逻辑。

### 7.4 依赖要点

`spring-boot-starter-web`、`spring-kafka`、`mybatis-plus-spring-boot3-starter`、
`mysql-connector-j`、nacos discovery/config、actuator、tracing。

---

## 8. sc-payment-service（付款服务，端口 8085）

### 8.1 本地 application.yml

| 配置项 | 值 | 说明 |
|---|---|---|
| `server.port` | `8085` | 付款服务端口 |
| `spring.application.name` | `sc-payment-service` | 服务名 / Nacos 注册名 |
| nacos / config.import | 同网关 | 导入 `common.yml` + `sc-payment-service.yml` |

### 8.2 Nacos 配置：sc-payment-service.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/payment_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: payment-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
```

| 配置项 | 值 | 说明 |
|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/payment_db?...` | 付款库连接串；`payment_db` 存放 `payable` 表 |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka 地址 |
| `spring.kafka.consumer.group-id` | `payment-service` | 消费组 ID：`payment-service`（同一组内多个实例分摊消息） |
| `spring.kafka.consumer.key-deserializer` | `StringDeserializer` | 消息 key 反序列化器 |
| `spring.kafka.consumer.value-deserializer` | `JsonDeserializer` | 消息 value 反序列化器（JSON） |
| `spring.kafka.consumer.properties.spring.json.trusted.packages` | `"*"` | JSON 反序列化信任包，`*` 表示信任所有包（演示环境放宽；生产建议限定具体包名） |
| `seata.enabled` / `seata.tx-service-group` | `true` / `sc_tx_group` | Seata 全局事务开关与事务分组（RM，详见 §15） |
| `seata.registry.nacos.*` | `localhost:8848`、`SEATA_GROUP` | 通过 Nacos 发现 Seata Server |

### 8.3 配置在代码中的消费点

- `com.huazaiki.payment.service.PaymentService`：
  - `createPayable`：生成应付账款，账期 `due_date` = 当前日期 + 30 天，状态 PENDING。
  - `approvePayment`：PENDING → APPROVED。
  - **Seata**：作为 RM 参与全局事务（需 `undo_log` 表，见 `docker/mysql/init/03-seata-undo-log.sql`）。
- Kafka consumer 配置已就绪，但代码中**暂无 `@KafkaListener` 消费者**——预期消费“入库完成”事件以自动创建应付，
  目前仍需手动调用 `/api/v1/payments` 创建。

### 8.4 依赖要点

`spring-boot-starter-web`、`spring-kafka`、`mybatis-plus-spring-boot3-starter`、
`mysql-connector-j`、nacos discovery/config、actuator、tracing。

---

## 9. sc-common（公共模块，无端口、无配置）

- 非微服务，是共享 jar 包：`com.huazaiki.common.api.ApiResponse`（统一响应体）、
  `com.huazaiki.common.exception.BusinessException` / `ErrorCode`（业务异常与错误码）。
- 无 application.yml、不注册到 Nacos，仅作为各服务的依赖被引用。

---

## 10. 基础设施配置（docker-compose.yml + .env）

### 10.1 服务清单

| 服务 | 镜像 | 端口映射 | 关键环境变量 | 作用 |
|---|---|---|---|---|
| `mysql` | `mysql:8.3` | `3306:3306` | `MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}` | 业务库，启动时执行 `docker/mysql/init/*.sql` 建库建表 |
| `nacos` | `nacos/nacos-server:v3.1.0` | `9090:8080`、`8848:8848`、`9848:9848` | `MODE=standalone`、`NACOS_AUTH_ENABLE=false` | 注册中心 + 配置中心 |
| `seata-server` | `seataio/seata-server:2.1.0` | `8091:8091` | `SEATA_IP=127.0.0.1`、`SEATA_PORT=8091` | Seata TC（事务协调器），配置见 `docker/seata/resources/` |
| `kafka` | `apache/kafka:3.9.2` | `9092:9092` | KRaft 单节点、`KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` | 消息队列（事件驱动） |
| `zipkin` | `openzipkin/zipkin:3` | `9411:9411` | — | 链路追踪展示 |

- Nacos 端口说明：`9090` 为控制台（容器内 8080），`8848` 为客户端 API（服务配置的 `server-addr`），
  `9848` 为 gRPC 通信端口（Nacos 2.x/3.x 客户端必需）。
- Kafka 采用 KRaft 模式（无 ZooKeeper），自动创建 topic。
- MySQL 初始化脚本：`01-databases.sql` 创建 5 个业务库，`02-tables.sql` 建表。

### 10.2 数据库 → 服务 → 表映射

| 数据库 | 归属服务 | 表 |
|---|---|---|
| `auth_db` | sc-auth-service | `sys_user` |
| `supplier_db` | sc-supplier-service | `supplier` |
| `purchase_db` | sc-purchase-service | `purchase_order`、`purchase_order_item` |
| `inventory_db` | sc-inventory-service | `item`、`inventory`、`receive_record` |
| `payment_db` | sc-payment-service | `payable` |

### 10.3 .env

```bash
MYSQL_ROOT_PASSWORD=root123
NACOS_AUTH_TOKEN=change-me-to-a-base64-string
```

- `MYSQL_ROOT_PASSWORD`：docker-compose 创建 MySQL root 的密码。
- `NACOS_AUTH_TOKEN`：Nacos 鉴权 token（当前 `NACOS_AUTH_ENABLE=false`，未启用）。

> ⚠️ 见第 2 节：`common.yml` 中数据源密码是 `root@123!`，而 `.env` 的 MySQL root 密码是 `root123`，
> 两处需保持一致，否则服务连不上库。

---

## 11. 链路追踪配置说明

- 所有业务服务（auth / gateway / supplier / purchase / inventory / payment）都引入了
  `micrometer-tracing-bridge-brave` 与 `zipkin-reporter-brave` 两个依赖。
- **没有任何显式配置**（application.yml 中无 `management.tracing.*`），完全依赖 Spring Boot 自动装配：
  - 默认采样率 10%（`0.1`）；
  - 默认上报地址 `http://localhost:9411/api/v2/spans`，与 docker-compose 中 zipkin 的 `9411` 端口对应。
- 需要调整采样率或上报地址时，可在对应服务配置中增加：

```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # 全量采样（演示/调试时）
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans
```

---

## 12. 配置修改与发布流程

配置源文件在 `nacos-config/*.yml`，修改后需发布到 Nacos 才能生效（本地直接改文件不会热更新到运行中的服务）。

```bash
# 方式一：脚本批量导入（推荐）
bash nacos-config/import-to-nacos.sh                       # 默认 localhost:8848
bash nacos-config/import-to-nacos.sh 192.168.1.10:8848     # 指定地址
NACOS_USER=nacos NACOS_PASSWORD=nacos bash nacos-config/import-to-nacos.sh  # 开启鉴权时

# 方式二：控制台手动
# http://localhost:9090 → 配置管理 → 配置列表 → 新建（dataId/group 见各节，格式 YAML）
```

发布后：

- 标注 `@RefreshScope` 的 Bean 会自动刷新；
- 纯启动期属性（`server.port`、`spring.datasource.*` 等）**不会热更新**，需重启服务；
- `JwtAuthFilter` 通过构造器 `@Value` 注入 `jwt.secret`，修改后不热更新（需重启网关）。

---

## 13. 已知问题与改进建议（配置相关）

| # | 问题 | 影响 | 建议 |
|---|---|---|---|
| 1 | ~~SecurityConfig 中 JwtUtil Bean 硬编码 secret/过期时间~~ | ✅ 已修复：`JwtUtil` Bean 改为 `@Value` 从 Nacos 注入 `jwt.secret` / `jwt.expiration-ms` | — |
| 2 | ~~数据源密码不一致~~ | ✅ 已修复：`.env` / `.env.example` 已统一为 `root@123!`，与 `common.yml` 一致 | 若 MySQL 数据卷已创建，需手动 `ALTER USER` 或重建卷 |
| 3 | Kafka producer/consumer 配置已就绪，但代码中没有 `KafkaTemplate` / `@KafkaListener` | 事件驱动流程（入库→生成应付）未真正打通，README 描述的流程与代码不一致 | 按业务补充生产/消费逻辑与 topic 定义 |
| 4 | ~~README 版本表与实际 pom 不符~~ | ✅ 已修复：README 已按 pom 更新（Boot 3.2.12 / Cloud 2023.0.3 / Alibaba 2023.0.3.2 / Seata 2.1.0） | — |
| 5 | ~~README 声称的 Resilience4j / Seata 未集成~~ | ✅ 已修复：采购服务接入 Feign 熔断，purchase/inventory/payment 接入 Seata AT（详见 §15） | — |
| 6 | Nacos 配置头部注释 dataId 写错（`auth-service.yml` / `supplier-service.yml`，实际应为 `sc-*`） | 易误导 | 修正注释 |
| 7 | `jwt.secret`、数据库密码明文存于 Nacos | 生产安全风险 | 生产开启 Nacos 鉴权、独立 namespace，或接入密钥管理（如 KMS/Vault） |
| 8 | Gateway 路由为静态路由 | 新增路由需重新发布配置/重启 | 需要时引入动态路由刷新机制 |
| 9 | `spring.json.trusted.packages: "*"` 过于宽泛 | 反序列化安全风险 | 生产限定为具体包名 |

---

## 14. 快速对照：各服务配置一览

| 服务 | 端口 | 数据源库 | 专属配置亮点 | 关键依赖 |
|---|---|---|---|---|
| sc-gateway-service | 8080 | —（不直连库） | 5 条静态路由、jwt.secret | gateway, loadbalancer, nacos, jjwt |
| sc-auth-service | 8081 | auth_db | datasource URL、jwt.secret、jwt.expiration-ms | web, security, mybatis-plus, jjwt |
| sc-supplier-service | 8082 | supplier_db | datasource URL | web, mybatis-plus |
| sc-purchase-service | 8083 | purchase_db | datasource URL、openfeign 超时、kafka producer | openfeign, loadbalancer, spring-kafka |
| sc-inventory-service | 8084 | inventory_db | datasource URL、kafka producer | web, spring-kafka, mybatis-plus |
| sc-payment-service | 8085 | payment_db | datasource URL、kafka consumer（group-id=payment-service） | web, spring-kafka, mybatis-plus |
| sc-common | — | — | 无 | 纯 POJO 共享包 |

---

## 15. Resilience4j 与 Seata 集成（关键业务）

### 15.1 Resilience4j 熔断（sc-purchase-service）

- 依赖：`spring-cloud-starter-circuitbreaker-resilience4j`。
- 开关：`spring.cloud.openfeign.circuitbreaker.enabled: true`（`sc-purchase-service.yml`）。
- 每个 Feign 客户端一个独立熔断器（`sc-inventory-service` / `sc-supplier-service`），参数见 §6.2。
- 降级：`InventoryFeignFallback` / `SupplierFeignFallback` 在熔断/超时时返回 `code=503`；
  `OrderService` 检查响应码，非 200 抛出 `BusinessException`。
- 覆盖的关键链路：创建采购订单（供应商校验 + 库存预留）、订单审批（库存预留）。

### 15.2 Seata 分布式事务（AT 模式）

- 依赖：`spring-cloud-starter-alibaba-seata`（SCA 2023.0.3.2 内置 Seata 2.1.0，groupId 为 `org.apache.seata`）。
- 参与服务：sc-purchase-service（TM）、sc-inventory-service（RM）、sc-payment-service（RM）。
- 事务分组：`seata.tx-service-group=sc_tx_group`，`service.vgroup-mapping.sc_tx_group=default`；
  通过 Nacos 发现 TC（`application=seata-server`，group=`SEATA_GROUP`，cluster=`default`）。
- 注解：`OrderService.createOrder` / `approveOrder` 标注 `@GlobalTransactional`；
  XID 由 `SeataFeignConfig` 的 `RequestInterceptor`（`TX_XID` 头）传播到库存服务。
- 基础设施：
  - `docker-compose.yml` 新增 `seata-server`（`seataio/seata-server:2.1.0`，端口 8091）；
  - 配置在 `docker/seata/resources/registry.conf`（Nacos 注册）与 `file.conf`（文件存储）；
  - 每个参与库需要 `undo_log` 表：`docker/mysql/init/03-seata-undo-log.sql`
    （注意：MySQL 初始化脚本只在数据卷首次创建时执行，已有 `sc-mysql` 卷需手动执行该 SQL 或重建卷）。

### 15.3 验证方式

1. `docker compose up -d`（含 seata-server）。
2. `bash nacos-config/import-to-nacos.sh` 发布更新后的配置。
3. 启动 purchase / inventory / payment 服务。
4. 调用 `POST /api/v1/orders`（创建订单）触发全局事务；可停掉 inventory 服务观察熔断降级（返回 503），
   再通过数据库核对 purchase_order / inventory 数据验证 Seata 回滚。