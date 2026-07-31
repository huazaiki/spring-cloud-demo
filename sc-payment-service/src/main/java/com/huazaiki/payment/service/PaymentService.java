package com.huazaiki.payment.service;

import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.payment.entity.Payable;
import com.huazaiki.payment.mapper.PayableMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PaymentService {

    private final PayableMapper payableMapper;

    public PaymentService(PayableMapper payableMapper) {
        this.payableMapper = payableMapper;
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
}