package com.huazaiki.payment.service;

import com.huazaiki.payment.entity.Payable;
import com.huazaiki.payment.mapper.PayableMapper;
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
}
