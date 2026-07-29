package com.huazaiki.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.inventory.entity.Inventory;
import com.huazaiki.inventory.entity.Item;
import com.huazaiki.inventory.entity.ReceiveRecord;
import com.huazaiki.inventory.mapper.InventoryMapper;
import com.huazaiki.inventory.mapper.ItemMapper;
import com.huazaiki.inventory.mapper.ReceiveRecordMapper;
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
@DisplayName("InventoryService")
class InventoryServiceTest {

    @Mock private ItemMapper itemMapper;
    @Mock private InventoryMapper inventoryMapper;
    @Mock private ReceiveRecordMapper receiveRecordMapper;
    @InjectMocks private InventoryService inventoryService;

    @Nested
    @DisplayName("receiveItem")
    class Receive {

        @Test
        @DisplayName("should create inventory record and receive record")
        void shouldCreateRecords() {
            when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(inventoryMapper.insert(any(Inventory.class))).thenReturn(1);
            when(receiveRecordMapper.insert(any(ReceiveRecord.class))).thenReturn(1);

            inventoryService.receiveItem(1L, 10L, BigDecimal.valueOf(100));

            verify(inventoryMapper).insert(any(Inventory.class));
            verify(receiveRecordMapper).insert(any(ReceiveRecord.class));
        }
    }

    @Nested
    @DisplayName("reserveStock")
    class Reserve {

        @Test
        @DisplayName("should reduce available and increase reserved")
        void shouldUpdateQuantities() {
            Inventory inv = new Inventory();
            inv.setId(1L);
            inv.setItemId(10L);
            inv.setAvailableQty(BigDecimal.valueOf(500));
            inv.setReservedQty(BigDecimal.ZERO);

            when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
            when(inventoryMapper.updateById(any(Inventory.class))).thenReturn(1);

            inventoryService.reserveStock(10L, BigDecimal.valueOf(100));

            assertEquals(BigDecimal.valueOf(400), inv.getAvailableQty());
            assertEquals(BigDecimal.valueOf(100), inv.getReservedQty());
        }

        @Test
        @DisplayName("should throw when insufficient stock")
        void shouldThrowWhenInsufficient() {
            Inventory inv = new Inventory();
            inv.setAvailableQty(BigDecimal.valueOf(50));

            when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);

            assertThrows(BusinessException.class, () ->
                    inventoryService.reserveStock(10L, BigDecimal.valueOf(100)));
        }
    }
}
