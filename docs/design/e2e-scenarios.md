# 主接缝 E2E 测试场景与验收断言

> 决策 ticket：「定义主接缝 E2E 测试场景与验收断言」（[#14](https://github.com/huazaiki/spring-cloud-demo/issues/14)）的产出。
> 依据：Q10/Q11 决策、Nacos 测试替代调研（`docs/research/nacos-test-strategy.md`）、API 契约（`docs/design/api-contract.md`）、roadmap 验收标准。

## 1. 测试基建（主接缝）

- **形态**：网关公共 API 黑盒 E2E；Testcontainers 起 MySQL + Kafka；Nacos 按「方案 A」用测试本地配置替代（`spring.cloud.nacos.config.enabled=false` + `SimpleDiscoveryClient` 静态实例）；Seata TC 用 `apache/seata-server:2.1.0` 容器 + file 直连。
- **被测对象**：6 服务 + 网关以真实容器/进程运行，测试只经网关 HTTP 调用，不注入 mock。
- **位置**：独立模块 `sc-e2e-tests`（或根级 test 目录），`mvn verify` 中执行（标 `@E2E`，可跳过本地短跑）。

## 2. 场景清单

### 主链路（happy path）

| 编号 | 场景 | 关键步骤 | 验收断言 |
|---|---|---|---|
| E2E-01 | 登录与权限初始化 | 管理员登录 → 建部门/角色/用户（采购员、仓管员、财务） | 各用户 `GET /auth/me` 返回正确权限点/菜单；越权访问返回 403 |
| E2E-02 | 请购→审批→转订单 | 采购员创建请购 → 提交 → 部门经理通过 → 采购经理通过 → 转订单 | PR/PO 状态流转正确；approval_task/record 完整；PO 关联 prId |
| E2E-03 | 订单审批+预留库存 | 提交订单审批 → 通过 | 审批通过回调触发预留：inventory available/reserved 正确变化；inventory_ledger 两条（预留） |
| E2E-04 | 收货→质检→入库 | 仓管员按订单收货（分批）→ 质检合格 → 入库 | receive/receive_item 差异正确；质检 PASS；入库核销 reserved→available；ledger 记录入库；PO status=RECEIVED |
| E2E-05 | 发票→三单匹配→付款→核销 | 财务登记发票 → 三单匹配 → 生成应付 → 付款单提交审批 → 通过 → 执行付款 | invoice_match MATCHED；payable PENDING→APPROVED→PAID；payable_payment 核销；PO status=SETTLED |
| E2E-06 | 事件流（Outbox→Kafka） | 入库/付款后检查 outbox 与消费者 | 入库事件 → payment 生成应付（幂等）；付款事件 → purchase 订单 SETTLED；重复投递不重复记账 |

### 异常与边界

| 编号 | 场景 | 步骤 | 断言 |
|---|---|---|---|
| E2E-07 | 库存不足 | 预留数量超可用库存 | 预留失败；Seata 回滚（订单/库存均无残留）；返回 4xx |
| E2E-08 | 审批驳回（回草稿） | PR 提交 → 部门经理驳回（BACK_TO_SUBMITTER） | PR 回 DRAFT；可改再提交；记录含驳回意见 |
| E2E-09 | 审批驳回（终态） | PO 提交 → 驳回（TERMINATE） | PO=REJECTED；后续任务 CANCELLED |
| E2E-10 | 收货差异 | 实收 > 订单（多收）或 < 订单（少收） | receive_item.diff_qty 正确；入库按实收核销；差异在单据可见 |
| E2E-11 | 质检不合格 | 质检 FAIL（全不合格） | 不入库；拒收记录；预留不核销（或按合格数量部分核销） |
| E2E-12 | 幂等重放（HTTP） | 同 Idempotency-Key 提交两次创建/付款 | 第二次返回首次结果，不重复建单/扣款 |
| E2E-13 | 幂等消费（Kafka） | 同一事件重复投递两次 | 下游只记一次（应付/流水不重复） |
| E2E-14 | 数据权限 | 采购员 A（部门1）查请购/订单列表 | 看不到部门2 数据；直接访问部门2 单据返回 403 |
| E2E-15 | 取消与清理 | 草稿/审批中单据取消 | 状态 CANCELLED；未完成任务 CANCELLED；预留释放（如已预留） |

## 3. 断言规范

- **状态**：单据 status 迁移必须精确（含中间态 SUBMITTED）。
- **数据**：金额/数量精确比对（DECIMAL 字符串）；库存余额 = 期初 + 流水求和（对账恒等式）。
- **事件**：outbox 行存在且最终 PUBLISHED；消费方幂等（幂等键去重）；死信无堆积（正常路径）。
- **权限**：403 断言覆盖无权限/越权两类。
- **失败可见性**：任一断言失败，输出相关表快照（单据/库存/outbox）便于定位。

## 4. 与 roadmap 验收标准的映射

- 核心闭环端到端可操作 → E2E-01~06 全绿。
- CI 全绿（单测+集成+契约+覆盖率门禁）→ E2E 并入 `mvn verify`。
- 库存流水/审计/权限/Outbox 可验证 → E2E-03/04/06/14 断言覆盖。
- 文档与代码一致 → 契约文件（api-contract.md）与 openapi.yml 同步校验（CI 检查）。