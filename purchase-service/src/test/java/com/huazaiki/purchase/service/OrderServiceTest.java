package com.huazaiki.purchase.service;

import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.purchase.entity.PurchaseOrder;
import com.huazaiki.purchase.entity.PurchaseOrderItem;
import com.huazaiki.purchase.mapper.PurchaseOrderItemMapper;
import com.huazaiki.purchase.mapper.PurchaseOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock private PurchaseOrderMapper orderMapper;
    @Mock private PurchaseOrderItemMapper itemMapper;
    @InjectMocks private OrderService orderService;

    @Nested
    @DisplayName("createOrder")
    class Create {

        @Test
        @DisplayName("should create order with DRAFT status and order number")
        void shouldCreateDraftOrder() {
            when(orderMapper.insert(any(PurchaseOrder.class))).thenReturn(1);
            when(itemMapper.insert(any(PurchaseOrderItem.class))).thenReturn(1);

            List<OrderService.ItemLine> items = List.of(
                    new OrderService.ItemLine(1L, "Steel Rod", BigDecimal.TEN, BigDecimal.valueOf(100), BigDecimal.valueOf(1000))
            );

            orderService.createOrder(1L, items);

            verify(orderMapper).insert(argThat((PurchaseOrder o) ->
                    "DRAFT".equals(o.getStatus()) && o.getOrderNo() != null));
            verify(itemMapper, times(1)).insert(any(PurchaseOrderItem.class));
        }
    }

    @Nested
    @DisplayName("approveOrder")
    class Approve {

        @Test
        @DisplayName("should transition from DRAFT to APPROVED")
        void shouldApproveDraftOrder() {
            PurchaseOrder order = new PurchaseOrder();
            order.setId(1L);
            order.setStatus("DRAFT");

            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.updateById(any(PurchaseOrder.class))).thenReturn(1);

            orderService.approveOrder(1L);

            assertEquals("APPROVED", order.getStatus());
        }

        @Test
        @DisplayName("should throw when order is not DRAFT")
        void shouldThrowWhenNotDraft() {
            PurchaseOrder order = new PurchaseOrder();
            order.setId(2L);
            order.setStatus("APPROVED");

            when(orderMapper.selectById(2L)).thenReturn(order);

            assertThrows(BusinessException.class, () -> orderService.approveOrder(2L));
        }
    }
}
