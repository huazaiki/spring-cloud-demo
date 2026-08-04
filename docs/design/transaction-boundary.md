# Seata 与 Outbox 边界清单

> 决策 ticket：「划定 Seata 与 Outbox 的边界清单」（[#4](https://github.com/huazaiki/spring-cloud-demo/issues/4)）的产出（HITL 已确认）。
> 依据：Q6 决策、ADR-0002；事件 topic/payload 见 `docs/design/events.md`。

## 1. 原则

- **Seata AT**：只用于"要么全成、要么全败"的**短链路强一致**操作（跨服务写，且任一失败必须回滚）。
- **Outbox + Kafka**：用于**可最终一致**的异步推进（先本地落库，事件驱动下游），配合幂等消费。
- 同一操作只选一种机制，不混用；Seata 范围收敛到 purchase（TM）+ inventory（RM）。

## 2. 边界清单（已确认）

| 操作 | 机制 | 说明 |
|---|---|---|
| 创建订单 + 供应商校验 + 库存预留 | **Seata AT** | 短链路强一致；任一失败整体回滚（现状延续） |
| 订单审批通过 + 库存预留 | **Seata AT** | 同上；审批回调触发预留 |
| 入库完成 → 生成应付/发票 | **Outbox 事件** | `sc.inventory.stock-in.completed` → payment 消费 |
| 付款/核销 → 订单结清 | **Outbox 事件** | `sc.payment.settlement.completed` → purchase 消费 |
| 订单取消/变更 → 释放预留 | **Outbox 事件** | `sc.purchase.order.cancelled` → inventory 消费 |
| 请购审批通过 → 转采购订单 | **本地事务**（purchase 内） | 澄清：请购与订单同属 purchase 域（同库），转单不跨服务，无需事件 |

> 澄清说明：原候选清单中的"请购转单"经复核为 purchase 服务内操作（Q3 决策：请购/审批链/订单都在 sc-purchase-service），故采用本地事务而非事件；其余 5 项按确认结果执行。

## 3. 各操作数据流

### Seata 短链路（同步）
```
订单审批通过 (purchase, TM)
  └─ @GlobalTransactional
       ├─ purchase_db: order.status=APPROVED
       └─ Feign → inventory reserve (RM, TX_XID 传播)
            └─ inventory_db: available− / reserved+（乐观锁）
```
失败任意分支 → 全局回滚（purchase/inventory 均无残留）。

### Outbox 长流程（异步）
```
入库核销完成 (inventory)
  ├─ 本地事务: inventory_ledger 入账 + outbox 写入 sc.inventory.stock-in.completed
  └─ Outbox 发布器 → Kafka → payment 消费（幂等）→ 生成 payable/invoice
```
```
付款/核销完成 (payment)
  ├─ 本地事务: payable→PAID + payable_payment + outbox 写入 sc.payment.settlement.completed
  └─ Outbox 发布器 → Kafka → purchase 消费（幂等）→ order.status=SETTLED
```
```
订单取消 (purchase)
  ├─ 本地事务: order.status=CANCELLED + outbox 写入 sc.purchase.order.cancelled
  └─ Outbox 发布器 → Kafka → inventory 消费（幂等）→ 释放预留 reserved−/available+ + 流水
```

## 4. 一致性保障

- **Outbox 与业务同事务**：事件行与业务写同库同事务，杜绝"业务成功消息丢"。
- **消费幂等**：按 `idempotencyKey`（业务唯一键）去重，重复投递不重复记账（见 `docs/design/events.md`）。
- **重试/死信**：发布失败重试，超限进 DLQ 人工处理。
- **Seata 与 Outbox 互斥**：Seata 链路上不写 outbox、不发事件，避免"已提交消息无法回滚"（ADR-0002）。

## 5. 落地影响

- 依赖：purchase 保留 `@GlobalTransactional`（创建/审批）；inventory 保留 RM 配置。
- 新增：outbox 表（schema.md 已含）、Outbox 发布器（各服务）、Kafka 消费者（payment/inventory/purchase 按需）。
- Seata TC 在测试中的处理见 `docs/research/nacos-test-strategy.md`。