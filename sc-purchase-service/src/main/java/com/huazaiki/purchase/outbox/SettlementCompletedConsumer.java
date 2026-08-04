package com.huazaiki.purchase.outbox;

import com.huazaiki.common.event.KafkaTopics;
import com.huazaiki.purchase.entity.PurchaseOrder;
import com.huazaiki.purchase.mapper.PurchaseOrderMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消费付款结算完成事件，将订单标记 SETTLED（幂等：仅 RECEIVED 状态可推进）。
 */
@Component
public class SettlementCompletedConsumer {

    private final PurchaseOrderMapper orderMapper;

    public SettlementCompletedConsumer(PurchaseOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @KafkaListener(topics = KafkaTopics.SETTLEMENT_COMPLETED, groupId = "purchase-service")
    public void onSettlementCompleted(Map<String, Object> payload) {
        Long orderId = ((Number) payload.get("orderId")).longValue();
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        if ("RECEIVED".equals(order.getStatus())) {
            order.setStatus("SETTLED");
            orderMapper.updateById(order);
        }
    }
}