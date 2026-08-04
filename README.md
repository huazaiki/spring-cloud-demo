# Spring Cloud Demo — Procure-to-Pay

供应链采购-入库-付款微服务演示项目，覆盖 Spring Cloud 全家桶核心组件。

> **当前阶段**：正在从演示项目升级为**企业级供应链采购管理系统**（一期：核心采购闭环 + 最小企业级地基）。
> 领域词汇见 [`CONTEXT.md`](./CONTEXT.md)，路线与验收见 [`docs/roadmap.md`](./docs/roadmap.md)，
> 硬决策见 [`docs/adr/`](./docs/adr/)。

## 架构

```
                    ┌──────────────┐
                    │   Gateway    │  :8080 路由 + JWT 鉴权（限流规划中）
                    └──────┬───────┘
           ┌───────────────┼───────────────┐
    ┌──────┴──────┐ ┌──────┴──────┐ ┌──────┴──────┐
    │ Auth        │ │ Supplier    │ │ Purchase    │
    │ :8081       │ │ :8082       │ │ :8083       │
    └─────────────┘ └─────────────┘ └──────┬──────┘
                                           │ Feign / Kafka
                              ┌────────────┼────────────┐
                       ┌──────┴──────┐              ┌───┴──────────┐
                       │ Inventory   │              │ Payment      │
                       │ :8084       │              │ :8085        │
                       └─────────────┘              └──────────────┘
```

## 技术栈

| 组件 | 选型 | 版本 |
|---|---|---|
| Framework | Spring Boot | 3.2.12 |
| Cloud | Spring Cloud | 2023.0.3 |
| Cloud Alibaba | Nacos + Seata | 2023.0.3.2 |
| Registry / Config | Nacos | 3.x |
| Gateway | Spring Cloud Gateway | — |
| RPC | OpenFeign + LoadBalancer | — |
| Circuit Breaker | Resilience4j | — |
| ORM | MyBatis-Plus | 3.5.9 |
| DB | MySQL | 8.3 |
| Message Queue | Kafka (KRaft) | 3.9.2 |
| Distributed Tx | Seata AT | 2.1.0 |
| Tracing | Micrometer + Brave + Zipkin | — |
| Auth | JWT (jjwt) | 0.12.6 |

## 模块

| 模块 | 端口 | 职责 |
|---|---|---|
| `common` | — | 统一响应体 ApiResponse、BusinessException、ErrorCode |
| `sc-gateway-service` | 8080 | 路由转发、JWT 鉴权、（规划）限流 |
| `sc-auth-service` | 8081 | （规划升级为身份与权限服务）用户、部门、角色、权限点、JWT 签发 |
| `sc-supplier-service` | 8082 | 供应商主数据 CRUD、（规划）准入/供货关系 |
| `sc-purchase-service` | 8083 | （规划）请购→审批→采购订单、状态机、审批链 |
| `sc-inventory-service` | 8084 | 物料管理、库存预留、入库；（规划）收货/质检/库存流水 |
| `sc-payment-service` | 8085 | 应付账款生成、审批、付款；（规划）发票/三单匹配/核销 |

> 标注"（规划）"的职责为升级一期目标，详见 [`docs/roadmap.md`](./docs/roadmap.md)。

## 业务流程（现状 / 目标）

1. 采购员创建采购订单（DRAFT）——目标：由**请购(PR)→审批→转单**驱动
2. 审批通过后订单变为 APPROVED，调用 `sc-inventory-service` 预留库存
3. 货物到达，仓管员收货/质检后入库——目标：**收货差异处理 + 质检（免检/全检/不合格）**
4. 入库完成 → **Kafka 事件 →** `sc-payment-service` 生成应付账款（PENDING）——*事件流为一期落地项（Outbox，见 ADR-0002），当前代码尚未实现*
5. 财务审批付款，应付变为 APPROVED → **Kafka 事件 →** `sc-purchase-service` 标记订单 SETTLED——*同上，一期落地*

## 快速开始

### 1. 环境要求

- Java 17+
- Maven 3.9+
- Docker & Docker Compose

### 2. 启动基础设施

```bash
cp .env.example .env   # 编辑 .env 中的密码
docker compose up -d
```

### 3. 启动微服务

```bash
mvn clean package -DskipTests

# 按顺序启动（每个终端一个）
mvn -pl sc-auth-service spring-boot:run "-Dmaven.repo.local=.m2/repository"

mvn -pl sc-supplier-service spring-boot:run "-Dmaven.repo.local=.m2/repository"
mvn -pl sc-inventory-service spring-boot:run "-Dmaven.repo.local=.m2/repository"
mvn -pl sc-payment-service spring-boot:run "-Dmaven.repo.local=.m2/repository"
mvn -pl sc-purchase-service spring-boot:run "-Dmaven.repo.local=.m2/repository"
mvn -pl sc-gateway-service spring-boot:run "-Dmaven.repo.local=.m2/repository"
```

### 4. 验证

```bash
# 注册用户
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"pass123","role":"ADMIN"}'

# 登录获取 token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"pass123"}'
```

### 5. API 调试

导入 `openapi.yml` 到 Postman / Insomnia / Swagger Editor 等工具。

## 运行测试

```bash
mvn test -Duser.home=%USERPROFILE% -Dmaven.repo.local=.m2/repository
```