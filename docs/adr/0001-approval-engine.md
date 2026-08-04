# 一期采购审批流采用自研轻量审批链（预留 Flowable 替换点）

Status: accepted

采购域一期需要请购、采购订单、付款三类审批，均为线性多级审批；现有代码只有硬编码的 approve 接口。我们决定一期自建轻量审批链：`approval_flow`（流程定义，审批规则 JSON 数据化：节点、条件、审批人角色）+ `approval_task`（任务实例），由 `ApprovalEngine` 组件驱动，业务方只依赖其接口。二期审批场景复杂化（会签、条件路由、加签）后再评估 Flowable/Camunda，仅替换引擎实现，业务代码不动。

## Considered Options

- **Flowable（一期直接引入）**：BPMN 2.0、设计器、会签等开箱即用；但引入几十张引擎表、事务/Seata 集成复杂度、学习与运维成本，与一期"分期交付"节奏冲突。
- **硬编码审批（延续现状）**：最省事；但二期所有审批都要重写，返工成本最高。

## Consequences

- 引擎表零外部依赖，与 Seata 本地事务天然兼容，部署无新增组件。
- 二期若换 Flowable，需将 JSON 审批规则映射为 BPMN，属中等迁移成本；`ApprovalEngine` 接口抽象用于隔离这一成本。