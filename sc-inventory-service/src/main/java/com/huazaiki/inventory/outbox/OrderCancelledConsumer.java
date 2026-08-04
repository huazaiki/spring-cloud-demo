package com.huazaiki.inventory.outbox;

import com.huazaiki.common.event.KafkaTopics;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.inventory.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 消费采购订单取消事件，释放预留库存（幂等：reserved 不足视为已释放）。
 */
@Component
public class OrderCancelledConsumer {

    private final InventoryService inventoryService;

    public OrderCancelledConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "inventory-service")
    public void onOrderCancelled(Map<String, Object> payload) {
        Long orderId = ((Number) payload.get("orderId")).longValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        if (items == null) {
            return;
        }
        for (Map<String, Object> item : items) {
            Long itemId = ((Number) item.get("itemId")).longValue();
            BigDecimal qty = new BigDecimal(item.get("quantity").toString());
            try {
                inventoryService.releaseReservation(itemId, qty, orderId);
            } catch (BusinessException ignored) {
                // 幂等：预留已释放或不存在，忽略重复消息
            }
        }
    }
}