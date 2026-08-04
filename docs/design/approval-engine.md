# 审批链规则 JSON 与 ApprovalEngine 接口设计

> 决策 ticket：「设计审批链规则 JSON 与 ApprovalEngine 接口」（[#7](https://github.com/huazaiki/spring-cloud-demo/issues/7)）的产出。
> 依据：Q4 决策、ADR-0001、表结构设计（`docs/design/schema.md`，approval_flow / approval_task / approval_record）。
> 前置依赖：「定义权限点清单与数据权限实现方案」（#6）定义角色码；本设计引用的角色码最终以 #6 为准。

## 1. 定位

- `ApprovalEngine` 是**通用审批组件**（位于 sc-common 或独立模块），由 purchase 域（PR/PO）与 payment 域（PAYMENT）复用；表按服务库部署（purchase_db / payment_db 各一份），组件共享，服务间不跨服务调用审批。
- 审批链只回答"**谁批、按什么顺序批、批完单据到哪个状态**"；业务单据状态机由各域维护，审批引擎通过回调/事件驱动状态迁移。

## 2. approval_flow.rule_json 结构

规则 JSON 数据化，`approval_flow.rule_json` 存一份；节点**按数组顺序执行**，`enabled` 条件为 false 时自动跳过该节点。

```json
{
  "flowCode": "PR_DEFAULT",
  "bizType": "PR",
  "nodes": [
    {
      "key": "dept_manager",
      "name": "部门经理审批",
      "approver": { "type": "ROLE", "value": "DEPT_MANAGER", "scope": "APPLICANT_DEPT" },
      "enabled": { "field": "totalAmount", "op": "gte", "value": 0 },
      "optional": false
    },
    {
      "key": "purchase_manager",
      "name": "采购经理审批",
      "approver": { "type": "ROLE", "value": "PURCHASE_MANAGER", "scope": "ANY" },
      "enabled": { "field": "totalAmount", "op": "gte", "value": 50000 },
      "optional": true
    }
  ],
  "rejectStrategy": "BACK_TO_SUBMITTER",
  "transferAllowed": true
}
```

### 字段说明

| 字段 | 取值 | 说明 |
|---|---|---|
| `flowCode` | 字符串 | 流程唯一编码（如 PR_DEFAULT / PO_DEFAULT / PAYMENT_DEFAULT） |
| `bizType` | `PR` / `PO` / `PAYMENT` | 单据类型 |
| `nodes[].key` | 字符串 | 节点唯一标识（流程内） |
| `nodes[].approver.type` | `ROLE` / `USER` | `ROLE`=按角色解析审批人；`USER`=指定具体用户 |
| `nodes[].approver.value` | 字符串 | `ROLE` 时角色码（如 DEPT_MANAGER）；`USER` 时用户 ID |
| `nodes[].approver.scope` | `APPLICANT_DEPT` / `ANY` | `APPLICANT_DEPT`=申请人所在部门内拥有该角色的用户（数据权限）；`ANY`=任意拥有该角色的用户 |
| `nodes[].enabled` | 条件对象 | `field`（单据字段，如 totalAmount）、`op`（gte/lte/gt/lt/eq/always）、`value`；默认 `always` |
| `nodes[].optional` | bool | 该节点是否允许跳过（与 enabled 配合，预留） |
| `rejectStrategy` | `BACK_TO_SUBMITTER` / `TERMINATE` | 驳回后单据回到草稿可改再提交，或直接终态驳回 |
| `transferAllowed` | bool | 当前节点是否允许转交他人审批 |

### 条件求值

引擎通过业务方提供的 `FieldResolver`（按 bizType 注册）读取单据字段求值，引擎本身不感知单据结构。

## 3. ApprovalEngine 接口

```java
public interface ApprovalEngine {

    /** 提交：为单据创建审批任务链，返回首个待办任务 */
    ApprovalContext submit(ApprovalSubmitRequest req);

    /** 通过当前节点：推进到下一节点，或全部通过后完成（回调业务完成审批） */
    void approve(ApprovalAction action);

    /** 驳回：按 rejectStrategy 处理（回草稿 / 终态驳回） */
    void reject(ApprovalAction action);

    /** 转交：把当前任务转给指定用户 */
    void transfer(ApprovalAction action, Long targetUserId);

    /** 取消：单据取消/转单时终止所有未完成任务 */
    void cancel(String bizType, Long bizId, Long operatorId);

    /** 查询某用户的待办任务（按 approver 解析结果匹配） */
    List<ApprovalTask> listMyTasks(Long userId);

    /** 查询单据审批轨迹 */
    List<ApprovalRecord> listRecords(String bizType, Long bizId);
}
```

关键入参：

- `ApprovalSubmitRequest`：`bizType`、`bizId`、`flowCode`（或按 bizType+金额自动选链）、`operatorId`、单据快照字段（供条件求值）
- `ApprovalAction`：`taskId`、`operatorId`、`opinion`、`version`（乐观锁）

**实现要点**

- 提交：查 `approval_flow`（bizType 匹配、ACTIVE）→ 解析节点链（跳过 enabled=false 的节点）→ 创建 `approval_task`（第一个 PENDING）→ 写 `approval_record`（SUBMIT）→ 回调业务方置"审批中"状态。
- 通过：校验 `version` 与审批人身份 → 任务置 APPROVED → 创建下一节点任务（如有）→ 全部通过后回调业务方"审批通过"。
- 驳回：按 `rejectStrategy` 回调业务方（回草稿 / 终态驳回），任务置 REJECTED，后续任务置 CANCELLED。
- 转交：任务改 `approver_id` 并记录 TRANSFER。
- 取消：未完成任务置 CANCELLED，写记录。
- 并发：任务乐观锁 `version` 防重复审批；审批人身份按节点 approver 解析结果校验。

## 4. 与业务状态机的联动

审批引擎不持有单据状态，通过**回调接口**驱动：`onSubmitted / onApproved / onRejectedBack / onRejectedTerminal / onCancelled`。

### 请购单（PR）

```
DRAFT ──提交──▶ SUBMITTED(审批中) ──全部通过──▶ APPROVED
                    │                              │
                    │ 驳回(回草稿)                  │ 转单
                    ▼                              ▼
                  DRAFT(可改再提交)              CONVERTED
                    │
                    │ 驳回(终态)
                    ▼
                 REJECTED

任意未完成状态 ──取消──▶ CANCELLED
```

### 采购订单（PO）

```
DRAFT ──提交──▶ SUBMITTED(审批中) ──通过──▶ APPROVED ──▶ SHIPPED ──▶ RECEIVED ──▶ SETTLED
                    │
                    │ 驳回(终态)
                    ▼
                 REJECTED

任意未完成状态 ──取消──▶ CANCELLED；APPROVED 后变更：生成变更版本走新审批链（一期简化：修改后重新提交）
```

> 注：PO 审批通过时按「划定 Seata 与 Outbox 的边界清单」（#4）回调触发"预留库存"（短链路强一致）。

### 付款（PAYMENT）

```
应付 PENDING ──提交──▶ 审批中 ──通过──▶ APPROVED ──(付款/核销)──▶ PAID / PARTIAL
                          │
                          │ 驳回(终态)
                          ▼
                       CANCELLED
```

### 表结构微调（对 schema.md 的补充）

`purchase_requisition.status` 与 `purchase_order.status` 枚举**补充 `SUBMITTED`（审批中）**，用于表达"已提交待审批"；审批中的流转以 `approval_task.status` 为准，两者由回调保持一致。该微调不改表结构（VARCHAR 枚举），在 Flyway 基线（#10）中直接落为最终枚举。

## 5. 三类单据默认审批链（种子数据，随 #6 角色码对齐）

| flowCode | bizType | 节点 | 条件 | 驳回策略 |
|---|---|---|---|---|
| PR_DEFAULT | PR | 1. 部门经理（DEPT_MANAGER，申请人部门）；2. 采购经理（PURCHASE_MANAGER） | 节点2：totalAmount ≥ 50,000 启用 | BACK_TO_SUBMITTER |
| PO_DEFAULT | PO | 1. 采购经理（PURCHASE_MANAGER）；2. 财务总监（FINANCE_DIRECTOR） | 节点2：totalAmount ≥ 100,000 启用 | TERMINATE |
| PAYMENT_DEFAULT | PAYMENT | 1. 财务经理（FINANCE_MANAGER） | — | TERMINATE |

> 金额阈值/角色码为**一期默认值**，通过 `approval_flow.rule_json` 可配置，不写死在代码。实际角色码（DEPT_MANAGER / PURCHASE_MANAGER / FINANCE_DIRECTOR / FINANCE_MANAGER）以「定义权限点清单与数据权限实现方案」（#6）为准，若 #6 收敛为更少角色（如部门经理=审批人角色化），审批链随之调整。

## 6. 安全与审计

- 审批人身份：仅当前任务解析出的审批人可操作（`approver_id` 匹配 + 角色校验），防止越权审批。
- 全部操作（SUBMIT/APPROVE/REJECT/TRANSFER/CANCEL）写 `approval_record`，满足"审批历史轨迹"用户故事与审计要求。
- 任务乐观锁 `version` 防并发重复审批。

## 7. 接口与页面对应（与 #9/#13 衔接）

- 待办中心 = `listMyTasks`；单据详情页展示 `listRecords` 轨迹。
- 审批操作按钮（通过/驳回/转交）权限点：`approval:task:approve` / `approval:task:reject` / `approval:task:transfer`（并入 #6 权限点清单）。
- REST 契约由「设计一期新增 API 契约」（#9）落地：`POST /api/v1/approval-tasks/{id}/approve` 等。