package com.huazaiki.purchase.service;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.purchase.entity.PurchaseOrder;
import com.huazaiki.purchase.entity.PurchaseOrderItem;
import com.huazaiki.purchase.feign.InventoryFeignClient;
import com.huazaiki.purchase.feign.SupplierFeignClient;
import com.huazaiki.purchase.mapper.PurchaseOrderItemMapper;
import com.huazaiki.purchase.mapper.PurchaseOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock private PurchaseOrderMapper orderMapper;
    @Mock private PurchaseOrderItemMapper itemMapper;
    @Mock private InventoryFeignClient inventoryClient;
    @Mock private SupplierFeignClient supplierClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderMapper, itemMapper, inventoryClient, supplierClient);
    }

    @Nested
    @DisplayName("createOrder")
    class Create {

        @Test
        @DisplayName("should create order with DRAFT status and order number")
        void shouldCreateDraftOrder() {
            when(supplierClient.getById(1L))
                    .thenReturn(ApiResponse.success(Map.of("id", 1)));
            when(inventoryClient.reserveStock(any()))
                    .thenReturn(ApiResponse.success());
            when(orderMapper.insert(any(PurchaseOrder.class)))
                    .thenAnswer(inv -> {
                        PurchaseOrder po = inv.getArgument(0);
                        po.setId(1L); // 模拟 MyBatis-Plus ASSIGN_ID 回填主键
                        return 1;
                    });
            when(itemMapper.insert(any(PurchaseOrderItem.class)))
                    .thenReturn(1);

            List<OrderService.ItemLine> items = List.of(
                    new OrderService.ItemLine(1L, "Steel Rod",
                            BigDecimal.TEN, BigDecimal.valueOf(100), BigDecimal.valueOf(1000)));

            PurchaseOrder result = orderService.createOrder(1L, items);

            assertNotNull(result);
            assertEquals("DRAFT", result.getStatus());
            assertNotNull(result.getOrderNo());
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

            when(itemMapper.selectList(any())).thenReturn(List.of());
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
