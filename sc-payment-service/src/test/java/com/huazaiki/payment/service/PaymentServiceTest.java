package com.huazaiki.payment.service;

import com.huazaiki.payment.entity.Payable;
import com.huazaiki.payment.feign.PurchaseFeignClient;
import com.huazaiki.payment.mapper.InvoiceItemMapper;
import com.huazaiki.payment.mapper.InvoiceMapper;
import com.huazaiki.payment.mapper.InvoiceMatchMapper;
import com.huazaiki.payment.mapper.PayableMapper;
import com.huazaiki.payment.mapper.PayablePaymentMapper;
import com.huazaiki.payment.mapper.PaymentMapper;
import com.huazaiki.payment.outbox.OutboxService;
import com.huazaiki.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    @Mock private PayableMapper payableMapper;
    @Mock private InvoiceMapper invoiceMapper;
    @Mock private InvoiceItemMapper invoiceItemMapper;
    @Mock private InvoiceMatchMapper invoiceMatchMapper;
    @Mock private PaymentMapper paymentMapper;
    @Mock private PayablePaymentMapper payablePaymentMapper;
    @Mock private PurchaseFeignClient purchaseClient;
    @Mock private OutboxService outboxService;
    @InjectMocks private PaymentService paymentService;

    @Nested
    @DisplayName("createPayable")
    class Create {

        @Test
        @DisplayName("should create payable with PENDING status")
        void shouldCreatePendingPayable() {
            when(payableMapper.insert(any(Payable.class))).thenReturn(1);

            paymentService.createPayable(1L, 100L, BigDecimal.valueOf(5000));

            verify(payableMapper).insert(argThat((Payable p) ->
                    "PENDING".equals(p.getStatus()) && p.getAmount().compareTo(BigDecimal.valueOf(5000)) == 0));
        }
    }

    @Nested
    @DisplayName("approvePayment")
    class Approve {

        @Test
        @DisplayName("should transition from PENDING to APPROVED")
        void shouldApprovePending() {
            Payable payable = new Payable();
            payable.setId(1L);
            payable.setStatus("PENDING");

            when(payableMapper.selectById(1L)).thenReturn(payable);
            when(payableMapper.updateById(any(Payable.class))).thenReturn(1);

            paymentService.approvePayment(1L);
            assertEquals("APPROVED", payable.getStatus());
        }
    }

    @Nested
    @DisplayName("settlePayable")
    class Settle {

        @Test
        @DisplayName("should settle APPROVED payable to PAID and write outbox event")
        void shouldSettleApproved() {
            Payable payable = new Payable();
            payable.setId(1L);
            payable.setOrderId(100L);
            payable.setAmount(BigDecimal.valueOf(5000));
            payable.setStatus("APPROVED");

            when(payableMapper.selectById(1L)).thenReturn(payable);
            when(payableMapper.updateById(any(Payable.class))).thenReturn(1);

            paymentService.settlePayable(1L);

            assertEquals("PAID", payable.getStatus());
            verify(outboxService).saveEvent(any(), any(), eq(1L), any(), any());
        }

        @Test
        @DisplayName("should throw when payable is not APPROVED")
        void shouldThrowWhenNotApproved() {
            Payable payable = new Payable();
            payable.setId(2L);
            payable.setStatus("PENDING");

            when(payableMapper.selectById(2L)).thenReturn(payable);

            assertThrows(BusinessException.class, () -> paymentService.settlePayable(2L));
        }
    }
}