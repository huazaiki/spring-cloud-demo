# Spring Cloud Demo — Procure-to-Pay

供应链采购-入库-付款微服务演示项目，覆盖 Spring Cloud 全家桶核心组件。

## 架构

```
                    ┌──────────────┐
                    │   Gateway    │  :8080 路由 + JWT 鉴权
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
| Framework | Spring Boot | 4.1.0 |
| Cloud | Spring Cloud | 2025.0.0 |
| Cloud Alibaba | Nacos + Seata | 2025.0.0.0 |
| Registry / Config | Nacos | 3.x |
| Gateway | Spring Cloud Gateway | — |
| RPC | OpenFeign + LoadBalancer | — |
| Circuit Breaker | Resilience4j | — |
| ORM | MyBatis-Plus | 3.5.9 |
| DB | MySQL | 8.3 |
| Message Queue | Kafka (KRaft) | 3.9.2 |
| Distributed Tx | Seata AT | 2.2.0 |
| Tracing | Micrometer + Brave + Zipkin | — |
| Auth | JWT (jjwt) | 0.12.6 |

## 模块

| 模块 | 端口 | 职责 |
|---|---|---|
| `common` | — | 统一响应体 ApiResponse、BusinessException、ErrorCode |
| `gateway-service` | 8080 | 路由转发、JWT 鉴权、限流 |
| `auth-service` | 8081 | 用户注册、登录、JWT 签发 |
| `supplier-service` | 8082 | 供应商主数据 CRUD |
| `purchase-service` | 8083 | 采购订单创建、审批、状态流转 |
| `inventory-service` | 8084 | 物料管理、库存预留、入库 |
| `payment-service` | 8085 | 应付账款生成、审批、付款 |

## 业务流程

1. 采购员在 `purchase-service` 创建采购订单（DRAFT）
2. 审批通过后订单变为 APPROVED，调用 `inventory-service` 预留库存
3. 货物到达，仓管员在 `inventory-service` 做入库操作
4. 入库完成 → Kafka 事件 → `payment-service` 生成应付账款（PENDING）
5. 财务审批付款，应付变为 APPROVED → Kafka 事件 → `purchase-service` 标记订单 SETTLED

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
mvn -pl auth-service spring-boot:run
mvn -pl supplier-service spring-boot:run
mvn -pl inventory-service spring-boot:run
mvn -pl payment-service spring-boot:run
mvn -pl purchase-service spring-boot:run
mvn -pl gateway-service spring-boot:run
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

导入 `bruno/spring-cloud-demo/` 到 [Bruno](https://www.usebruno.com/)。

## 运行测试

```bash
mvn test -Duser.home=%USERPROFILE% -Dmaven.repo.local=.m2/repository
```
