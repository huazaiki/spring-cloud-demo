# 事件规范：topic / payload / 幂等键 / 死信

> 决策 ticket：「定义事件规范：topic/payload/幂等键/死信」（[#8](https://github.com/huazaiki/spring-cloud-demo/issues/8)）的产出。
> 依据：Q6 决策、ADR-0002、边界清单（`docs/design/transaction-boundary.md`）、outbox 表（`docs/design/schema.md`）。

## 1. 事件清单（一期）

| 事件 | topic | 生产者 | 消费者 | 触发 |
|---|---|---|---|---|
| 入库完成 | `sc.inventory.stock-in.completed` | sc-inventory-service | sc-payment-service | 质检合格入库核销后 |
| 订单取消 | `sc.purchase.order.cancelled` | sc-purchase-service | sc-inventory-service | 订单取消（释放预留） |
| 付款结算完成 | `sc.payment.settlement.completed` | sc-payment-service | sc-purchase-service | 付款+核销后 |

预留（一期不使用）：`sc.purchase.order.approved`（金额快照，若未来应付生成需要）。

## 2. topic 命名规范

`sc.<domain>.<past-tense-event>`：
- domain = 事件发生域（inventory / purchase / payment）；
- 事件名用过去式完成时（stock-in.completed / order.cancelled / settlement.completed）。
- DLQ：`sc.<domain>.dlq`（每域一张），人工重放。

## 3. payload schema（统一信封）

```json
{
  "eventId": "uuid",
  "eventType": "StockInCompleted",
  "source": "sc-inventory-service",
  "occurredAt": "2026-08-04T10:00:00+08:00",
  "idempotencyKey": "stock-in:<orderId>:<receiveNo>",
  "data": {
    "orderId": 1024,
    "supplierId": 88,
    "receiveNo": "RC-20260804-001",
    "itemId": 55,
    "quantity": 100.0000
  }
}
```

- `eventId`：全局唯一（UUID），Outbox `uk_event_id`。
- `idempotencyKey`：业务唯一键，**消费幂等依据**（Outbox `uk_idempotency`）。构造规则：`<事件域>:<业务单号>:<明细键>`。
- `data`：事件所需业务快照；**不携带跨服务可推导的敏感全量**（如金额由消费者按需查询，见 §4）。

各事件 `data` 定义：

| 事件 | data 字段 |
|---|---|
| StockInCompleted | orderId、supplierId、receiveNo、itemId、quantity |
| OrderCancelled | orderId、items[{itemId, quantity}] |
| SettlementCompleted | orderId、payableId、paymentNo、amount |

## 4. 消费与交互

- **幂等**：消费者按 `idempotencyKey` 或业务唯一键查重（唯一索引）；重复消息直接 ACK，不重复记账。
- **补全数据**：消费者按需**同步 Feign 查询**生产者域补全（如 payment 消费 StockInCompleted 后查 purchase 订单金额生成应付）——查询不写、不参与事务，可接受。
- **失败重试**：消费异常不提交 offset，按退避重试；连续失败进 DLQ。
- **顺序**：一期事件按单据维度无强顺序要求；如需保证（如同一订单多次入库），用 key=orderId 保证分区内顺序。

## 5. Outbox 发布器设计

- 轮询各库 `outbox` 表 `status=PENDING`（`next_retry_at <= now`）→ 发送 Kafka（key=`idempotencyKey`）→ 成功后 `status=PUBLISHED, published_at`。
- 失败：`status=FAILED, retry_count+1, next_retry_at=指数退避`；超过上限（默认 5 次）保持 FAILED 待人工，或直接投递到 DLQ 并置 PUBLISHED（人工重放后恢复）。
- 启动补偿：服务启动时扫描历史 PENDING 补发（防宕机窗口丢失）。
- 幂等键唯一约束兜底：重复插入同 `idempotencyKey` 直接忽略。

## 6. Kafka 配置

- 生产者/消费者序列化：JSON（现有 nacos-config 已配 JsonSerializer/JsonDeserializer）。
- `spring.json.trusted.packages` 收敛为具体包（`com.huazaiki.*`），不再用 `*`（安全，P1 已列）。
- topic 自动创建保留（compose `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`）；生产建议显式 topic 管理（二期）。
- 消费组：payment-service（stock-in）、inventory-service（order-cancelled）、purchase-service（settlement）。

## 7. 与验收对应

- E2E-06（事件流幂等）、E2E-13（重复投递幂等）断言本规范落地（`docs/design/e2e-scenarios.md`）。