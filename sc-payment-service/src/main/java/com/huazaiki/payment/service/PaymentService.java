package com.huazaiki.payment.service;

import com.huazaiki.common.event.KafkaTopics;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.payment.entity.Payable;
import com.huazaiki.payment.mapper.PayableMapper;
import com.huazaiki.payment.outbox.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    private final PayableMapper payableMapper;
    private final OutboxService outboxService;

    public PaymentService(PayableMapper payableMapper, OutboxService outboxService) {
        this.payableMapper = payableMapper;
        this.outboxService = outboxService;
    }

    public List<Payable> listPayables() {
        return payableMapper.selectList(null);
    }

    @Transactional
    public Payable createPayable(Long orderId, Long supplierId, BigDecimal amount) {
        Payable payable = new Payable();
        payable.setOrderId(orderId);
        payable.setSupplierId(supplierId);
        payable.setAmount(amount);
        payable.setDueDate(LocalDate.now().plusDays(30));
        payable.setStatus("PENDING");
        payableMapper.insert(payable);
        return payable;
    }

    @Transactional
    public void approvePayment(Long id) {
        Payable payable = payableMapper.selectById(id);
        if (payable == null) {
            throw new BusinessException(() -> 404, "Payable not found: " + id);
        }
        if (!"PENDING".equals(payable.getStatus())) {
            throw new BusinessException(() -> 400, "Only PENDING payables can be approved");
        }
        payable.setStatus("APPROVED");
        payableMapper.updateById(payable);
    }

    /**
     * 付款结算：应付 APPROVED → PAID，并写 Outbox 事件 SettlementCompleted（purchase 消费后订单 SETTLED）。
     * 幂等：已 PAID 直接返回。
     */
    @Transactional
    public void settlePayable(Long id) {
        Payable payable = payableMapper.selectById(id);
        if (payable == null) {
            throw new BusinessException(() -> 404, "Payable not found: " + id);
        }
        if ("PAID".equals(payable.getStatus())) {
            return;
        }
        if (!"APPROVED".equals(payable.getStatus())) {
            throw new BusinessException(() -> 400, "Only APPROVED payables can be settled");
        }
        payable.setStatus("PAID");
        payableMapper.updateById(payable);

        outboxService.saveEvent(KafkaTopics.SETTLEMENT_COMPLETED, "PAYABLE", id,
                "settlement:" + id,
                Map.of("orderId", payable.getOrderId(), "payableId", id, "amount", payable.getAmount()));
    }
}