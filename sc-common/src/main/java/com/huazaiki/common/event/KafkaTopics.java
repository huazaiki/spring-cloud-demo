package com.huazaiki.common.event;

/**
 * Kafka topic 常量（docs/design/events.md）。
 */
public final class KafkaTopics {

    public static final String STOCK_IN_COMPLETED = "sc.inventory.stock-in.completed";
    public static final String ORDER_CANCELLED = "sc.purchase.order.cancelled";
    public static final String SETTLEMENT_COMPLETED = "sc.payment.settlement.completed";

    private KafkaTopics() {}
}