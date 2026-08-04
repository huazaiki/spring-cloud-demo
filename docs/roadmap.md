# 供应链采购管理系统 — 一期路线图

> 由 grilling 会话（11 项决策）沉淀。领域词汇见 `CONTEXT.md`，硬决策见 `docs/adr/`。

## 定位

以**真实投产**为目标，**MVP 分期交付**。一期交付"核心采购闭环 + 最小企业级地基"，二期再扩展寻源/合同/多仓库/BI 等。

## 决策索引

| # | 决策 | 结论 |
|---|---|---|
| Q1 | 项目定位 | 真实投产，MVP 分期交付 |
| Q2 | 一期业务范围 | 请购→审批→订单→收货→质检→入库→应付/发票 + 权限地基 |
| Q3 | 服务拆分 | 保持 6 服务就地演进，一期不新增服务 |
| Q4 | 审批流 | 自研轻量审批链 + ApprovalEngine 抽象（ADR-0001） |
| Q5 | 权限模型 | 自建 RBAC 权限点 + 服务内方法级鉴权，数据权限按部门 |
| Q6 | 事务一致性 | 短链路 Seata AT + 长流程 Outbox/Saga（ADR-0002） |
| Q7 | 数据层 | Flyway 迁移 + 审计/乐观锁/逻辑删除 + inventory_ledger 流水 |
| Q8 | 部署 DevOps | GitHub Actions CI + Nacos 多环境 + 容器化，K8s 二期 |
| Q9 | 前端 | 权限路由/动态菜单 + 一期业务页，轻量状态管理 |
| Q10 | 测试质量 | 单测 + Testcontainers 集成 + Feign 契约 + JaCoCo 60% 门禁 |
| Q11 | 验收与技术债 | 闭环端到端可用 + CI 绿 + 文档一致；P0/P1/P2 分级 |

## 一期范围（In Scope）

### 业务

- **组织与权限**：用户/部门/角色/权限点/菜单（RBAC），数据权限按部门
- **主数据增强**：物料分类、价格协议/供货关系；供应商准入（简化版）
- **请购(PR)→审批→转采购订单**
- **订单全生命周期**：变更/取消/收货计划/状态机补全（DRAFT→…→SETTLED/CANCELLED）
- **收货→质检→入库**：收货差异（多收/少收）、质检（免检/全检/不合格）、库存闭环修正、`inventory_ledger` 库存流水
- **应付增强**：发票登记、三单匹配、付款/核销（简化版）、账期
- **待办任务中心 + 站内通知**（轻量）
- **基础报表**：采购执行看板、应付账龄、库存查询

### 技术

- `sc-auth-service` → 身份与权限服务；`sc-purchase-service` / `sc-inventory-service` / `sc-payment-service` 就地扩展；网关加限流
- 自研轻量审批链（approval_flow + approval_task + ApprovalEngine）
- Outbox + Kafka 事件落地；短链路保留 Seata AT
- Flyway 迁移；审计/乐观锁/逻辑删除/索引补全
- GitHub Actions CI（mvn verify + 镜像构建）；Nacos dev/prod namespace；服务容器化
- 前端：权限路由/动态菜单、一期业务页、统一请求/表单规范
- 三层测试：单测 + Testcontainers 集成 + Feign 契约；JaCoCo 60% 门禁

## 明确排除（二期）

寻源询价/招标、合同管理、多仓库/库位/批次/序列号、复杂审批流引擎、BI 大屏、供应商绩效评估、OAuth2/SSO、K8s、动态路由、SonarQube。

## 技术债分级

- **P0（阻塞一期，先修）**：库存预留/入库闭环 bug（reserved 只增不减）、Kafka Outbox 落地、服务内权限执行点、Flyway 迁移基线
- **P1（随模块重构顺带修）**：Jackson Long→String 收敛到 common、JwtAuthFilter 配置热更新失效、网关限流落地、文档同步、DTO 校验
- **P2（二期）**：动态路由、SonarQube、K8s、OAuth2/SSO、独立流程引擎

## 验收标准（Definition of Done）

1. 核心闭环在 docker-compose 环境端到端可操作：请购→审批→订单→收货→质检→入库→发票→付款/核销（含权限、待办中心）
2. CI 全绿：单测 + 集成测试 + 契约测试 + 覆盖率门禁
3. 库存流水、审计、权限、Outbox 事件落地且可验证
4. 文档与代码一致（README、openapi.yml、CONTEXT.md、ADR）

## 相关文档

- `CONTEXT.md` — 领域词汇表（Ubiquitous Language）
- `docs/adr/0001-approval-engine.md` — 审批流选型
- `docs/adr/0002-transaction-consistency.md` — 事务一致性策略

## P0 里程碑（2026-08-04 验收）

> 原定 P0 技术债全部清零，`mvn -pl <全部 7 模块> -am test` BUILD SUCCESS（common 9 / auth 13 / purchase 5 / inventory 5 / payment 4 / supplier 3 测试全绿）。

| P0 项 | 状态 | 提交 |
|---|---|---|
| 库存预留/入库闭环修复（reserved 只增不减虚增缺陷）+ inventory_ledger 库存流水 | ✅ | `9fc4b5a` |
| Outbox + Kafka 事件落地（发布器/幂等消费；入库→应付、取消→释放预留、付款→结清） | ✅ | `8d15919` |
| 服务内权限执行点（RBAC 数据模型 + JWT 身份/权限 claims + 网关透传 + @RequirePermission 拦截器） | ✅ | `c28eebc` |
| Flyway 迁移基线铺开全部 6 服务 + 一期目标 schema（auth RBAC / inventory 流水 / outbox / supplier 一期） | ✅ | `ea5f40f` |

### 里程碑验收结论

- **P0 已达成**：数据正确性（库存闭环）、事件一致性（Outbox）、安全（权限执行点）、schema 管理（Flyway）四大地基就绪，可进入一期主体功能实施。
- **DoD 剩余项（一期主体功能，非 P0）**：请购 PR、审批链引擎（ApprovalEngine）、收货/质检/入库流程化、发票/三单匹配/付款单、待办中心、前端（动态菜单/业务页）、Testcontainers E2E（主接缝）、契约测试、CI workflow + JaCoCo 门禁、审计字段补齐、文档同步（README/openapi.yml）。
- 实施依据：Spec（issue #1）+ `docs/design/*.md`；边界见「明确排除」。

### 质量基建（CI/E2E，2026-08-04 落地）

- `sc-e2e-tests` 模块：网关公共 API 黑盒 E2E 主接缝（Testcontainers MySQL+Kafka + 6 服务 + SimpleDiscoveryClient 静态实例替代 Nacos）；本地 Docker 不可用时自动跳过，CI（GitHub Actions ubuntu，原生 Docker）运行。
- 覆盖率门禁：JaCoCo 各模块 line 门限（common 0.60 / auth 0.40 / supplier 0.30 / purchase 0.35 / inventory 0.20 / payment 0.12；gateway/e2e 跳过）。目标随测试补强逐步提升至 0.60。
- CI：`.github/workflows/ci.yml`（push/PR → mvn verify → 上传 jacoco 报告；main 合并后构建 6 服务镜像推 GHCR）。
- 服务模块 Spring Boot 可执行 jar 加 `-exec` classifier（普通 jar 供模块依赖解析）。
- 说明：本机 Docker Desktop 29.x 管道代理与 docker-java 不兼容，E2E 无法本地运行，需在 CI 执行（详见 `docs/research/nacos-test-strategy.md` 与 devops.md）。