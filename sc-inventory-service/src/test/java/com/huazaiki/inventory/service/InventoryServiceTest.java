package com.huazaiki.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.inventory.entity.Inventory;
import com.huazaiki.inventory.entity.InventoryLedger;
import com.huazaiki.inventory.entity.Item;
import com.huazaiki.inventory.entity.ReceiveRecord;
import com.huazaiki.inventory.mapper.InventoryLedgerMapper;
import com.huazaiki.inventory.mapper.InventoryMapper;
import com.huazaiki.inventory.mapper.ItemMapper;
import com.huazaiki.inventory.mapper.ReceiveRecordMapper;
import com.huazaiki.inventory.outbox.OutboxService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService")
class InventoryServiceTest {

    @Mock private ItemMapper itemMapper;
    @Mock private InventoryMapper inventoryMapper;
    @Mock private ReceiveRecordMapper receiveRecordMapper;
    @Mock private InventoryLedgerMapper ledgerMapper;
    @Mock private OutboxService outboxService;
    @InjectMocks private InventoryService inventoryService;

    @Nested
    @DisplayName("receiveItem")
    class Receive {

        @Test
        @DisplayName("should create inventory, receive record and ledger when no inventory row")
        void shouldCreateRecords() {
            when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(inventoryMapper.insert(any(Inventory.class))).thenReturn(1);
            when(receiveRecordMapper.insert(any(ReceiveRecord.class))).thenAnswer(inv -> {
                ReceiveRecord r = inv.getArgument(0);
                r.setId(1L);
                return 1;
            });
            when(ledgerMapper.insert(any(InventoryLedger.class))).thenReturn(1);

            inventoryService.receiveItem(1L, 10L, BigDecimal.valueOf(100));

            verify(inventoryMapper).insert(any(Inventory.class));
            verify(receiveRecordMapper).insert(any(ReceiveRecord.class));
            verify(ledgerMapper).insert(any(InventoryLedger.class));
            verify(outboxService).saveEvent(anyString(), eq("ORDER"), eq(1L), anyString(), any());
        }

        @Test
        @DisplayName("should release reserved and increase available on receive")
        void shouldReleaseReservedOnReceive() {
            Inventory inv = new Inventory();
            inv.setId(1L);
            inv.setItemId(10L);
            inv.setAvailableQty(BigDecimal.valueOf(50));
            inv.setReservedQty(BigDecimal.valueOf(100));

            when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
            when(inventoryMapper.updateById(any(Inventory.class))).thenReturn(1);
            when(receiveRecordMapper.insert(any(ReceiveRecord.class))).thenAnswer(inv2 -> {
                ReceiveRecord r = inv2.getArgument(0);
                r.setId(2L);
                return 1;
            });
            when(ledgerMapper.insert(any(InventoryLedger.class))).thenReturn(1);

            inventoryService.receiveItem(1L, 10L, BigDecimal.valueOf(100));

            // 预留全部核销：available 50+100=150，reserved 100-100=0（修复 reserved 只增不减缺陷）
            assertEquals(BigDecimal.valueOf(150), inv.getAvailableQty());
            assertEquals(BigDecimal.ZERO, inv.getReservedQty());
            verify(ledgerMapper).insert(any(InventoryLedger.class));
            verify(outboxService).saveEvent(anyString(), eq("ORDER"), eq(1L), anyString(), any());
        }

        @Test
        @DisplayName("should not release more reserved than exists")
        void shouldReleaseOnlyExistingReserved() {
            Inventory inv = new Inventory();
            inv.setId(1L);
            inv.setItemId(10L);
            inv.setAvailableQty(BigDecimal.valueOf(0));
            inv.setReservedQty(BigDecimal.valueOf(30));

            when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
            when(inventoryMapper.updateById(any(Inventory.class))).thenReturn(1);
            when(receiveRecordMapper.insert(any(ReceiveRecord.class))).thenAnswer(inv2 -> {
                ReceiveRecord r = inv2.getArgument(0);
                r.setId(3L);
                return 1;
            });
            when(ledgerMapper.insert(any(InventoryLedger.class))).thenReturn(1);

            inventoryService.receiveItem(1L, 10L, BigDecimal.valueOf(100));

            assertEquals(BigDecimal.valueOf(100), inv.getAvailableQty());
            assertEquals(BigDecimal.ZERO, inv.getReservedQty());
        }
    }

    @Nested
    @DisplayName("reserveStock")
    class Reserve {

        @Test
        @DisplayName("should reduce available, increase reserved and write ledger")
        void shouldUpdateQuantities() {
            Inventory inv = new Inventory();
            inv.setId(1L);
            inv.setItemId(10L);
            inv.setAvailableQty(BigDecimal.valueOf(500));
            inv.setReservedQty(BigDecimal.ZERO);

            when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
            when(inventoryMapper.updateById(any(Inventory.class))).thenReturn(1);
            when(ledgerMapper.insert(any(InventoryLedger.class))).thenReturn(1);

            inventoryService.reserveStock(10L, BigDecimal.valueOf(100), 1L);

            assertEquals(BigDecimal.valueOf(400), inv.getAvailableQty());
            assertEquals(BigDecimal.valueOf(100), inv.getReservedQty());
            verify(ledgerMapper).insert(any(InventoryLedger.class));
        }

        @Test
        @DisplayName("should throw when insufficient stock")
        void shouldThrowWhenInsufficient() {
            Inventory inv = new Inventory();
            inv.setAvailableQty(BigDecimal.valueOf(50));

            when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);

            assertThrows(BusinessException.class, () ->
                    inventoryService.reserveStock(10L, BigDecimal.valueOf(100), 1L));
        }
    }
}