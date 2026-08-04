package com.huazaiki.payment.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.event.KafkaTopics;
import com.huazaiki.payment.entity.Payable;
import com.huazaiki.payment.feign.OrderInfo;
import com.huazaiki.payment.feign.PurchaseFeignClient;
import com.huazaiki.payment.mapper.PayableMapper;
import com.huazaiki.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消费入库完成事件，生成应付账款（幂等：订单已有应付则跳过）。
 */
@Component
public class StockInCompletedConsumer {

    private final PayableMapper payableMapper;
    private final PurchaseFeignClient purchaseClient;
    private final PaymentService paymentService;

    public StockInCompletedConsumer(PayableMapper payableMapper,
                                    PurchaseFeignClient purchaseClient,
                                    PaymentService paymentService) {
        this.payableMapper = payableMapper;
        this.purchaseClient = purchaseClient;
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = KafkaTopics.STOCK_IN_COMPLETED, groupId = "payment-service")
    public void onStockInCompleted(Map<String, Object> payload) {
        Long orderId = ((Number) payload.get("orderId")).longValue();
        Long existing = payableMapper.selectCount(new LambdaQueryWrapper<Payable>()
                .eq(Payable::getOrderId, orderId));
        if (existing != null && existing > 0) {
            return; // 幂等：已生成过应付
        }
        ApiResponse<OrderInfo> resp = purchaseClient.getById(orderId);
        if (resp.getCode() != 200 || resp.getData() == null) {
            throw new IllegalStateException("Order not found for stock-in, orderId=" + orderId);
        }
        OrderInfo order = resp.getData();
        paymentService.createPayable(orderId, order.getSupplierId(), order.getTotalAmount());
    }
}