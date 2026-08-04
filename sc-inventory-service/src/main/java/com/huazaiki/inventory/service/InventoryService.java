package com.huazaiki.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.common.event.KafkaTopics;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 库存服务。
 *
 * <p>语义（docs/design/schema.md §4）：
 * <ul>
 *   <li>预留 reserve：available − qty / reserved + qty，写流水 RESERVE；</li>
 *   <li>入库核销 receive：reserved − min(reserved, qty) / available + qty，写流水 RECEIVE（修复 reserved 只增不减的虚增缺陷），并写 Outbox 事件 StockInCompleted；</li>
 *   <li>释放预留 release：reserved − qty / available + qty，写流水 RELEASE（订单取消等场景）。</li>
 * </ul>
 * 流水行记录的是【可用库存】的变动（qty_change 带符号、before/after 为可用库存），保证"期初 + 流水 = 余额"。
 */
@Service
public class InventoryService {

    private final ItemMapper itemMapper;
    private final InventoryMapper inventoryMapper;
    private final ReceiveRecordMapper receiveRecordMapper;
    private final InventoryLedgerMapper ledgerMapper;
    private final OutboxService outboxService;

    public InventoryService(ItemMapper itemMapper,
                            InventoryMapper inventoryMapper,
                            ReceiveRecordMapper receiveRecordMapper,
                            InventoryLedgerMapper ledgerMapper,
                            OutboxService outboxService) {
        this.itemMapper = itemMapper;
        this.inventoryMapper = inventoryMapper;
        this.receiveRecordMapper = receiveRecordMapper;
        this.ledgerMapper = ledgerMapper;
        this.outboxService = outboxService;
    }

    public List<Item> listItems() {
        return itemMapper.selectList(null);
    }

    public Item createItem(String name, String spec, String unit, String sku) {
        Item item = new Item();
        item.setName(name);
        item.setSpec(spec);
        item.setUnit(unit);
        item.setSku(sku);
        itemMapper.insert(item);
        return item;
    }

    /**
     * 入库：登记收货记录，核销预留（reserved→available），写流水并发布 StockInCompleted 事件。
     */
    @Transactional
    public void receiveItem(Long orderId, Long itemId, BigDecimal quantity) {
        ReceiveRecord record = new ReceiveRecord();
        record.setOrderId(orderId);
        record.setItemId(itemId);
        record.setQuantity(quantity);
        record.setReceivedAt(LocalDateTime.now());
        receiveRecordMapper.insert(record);

        Inventory inventory = findInventory(itemId);
        BigDecimal beforeAvailable = BigDecimal.ZERO;
        if (inventory == null) {
            Inventory created = new Inventory();
            created.setItemId(itemId);
            created.setAvailableQty(quantity);
            created.setReservedQty(BigDecimal.ZERO);
            inventoryMapper.insert(created);
        } else {
            beforeAvailable = inventory.getAvailableQty();
            BigDecimal release = inventory.getReservedQty().min(quantity);
            inventory.setReservedQty(inventory.getReservedQty().subtract(release));
            inventory.setAvailableQty(beforeAvailable.add(quantity));
            inventoryMapper.updateById(inventory);
        }
        BigDecimal afterAvailable = inventory == null ? quantity : inventory.getAvailableQty();
        writeLedger(itemId, "RECEIVE", "ORDER", orderId, quantity, beforeAvailable, afterAvailable);

        outboxService.saveEvent(
                KafkaTopics.STOCK_IN_COMPLETED, "ORDER", orderId,
                "stock-in:" + orderId + ":" + itemId + ":" + record.getId(),
                Map.of("orderId", orderId, "itemId", itemId, "quantity", quantity));
    }

    /**
     * 预留：available→reserved。
     *
     * @param orderId 关联采购订单（可为 null）
     */
    @Transactional
    public void reserveStock(Long itemId, BigDecimal quantity, Long orderId) {
        Inventory inventory = findInventory(itemId);
        if (inventory == null || inventory.getAvailableQty().compareTo(quantity) < 0) {
            throw new BusinessException(() -> 400, "Insufficient stock for item: " + itemId);
        }
        BigDecimal before = inventory.getAvailableQty();
        inventory.setAvailableQty(before.subtract(quantity));
        inventory.setReservedQty(inventory.getReservedQty().add(quantity));
        inventoryMapper.updateById(inventory);
        writeLedger(itemId, "RESERVE", "ORDER", orderId, quantity.negate(), before, inventory.getAvailableQty());
    }

    /**
     * 释放预留：reserved→available（订单取消/变更时调用）。
     */
    @Transactional
    public void releaseReservation(Long itemId, BigDecimal quantity, Long orderId) {
        Inventory inventory = findInventory(itemId);
        if (inventory == null || inventory.getReservedQty().compareTo(quantity) < 0) {
            throw new BusinessException(() -> 400, "No enough reservation for item: " + itemId);
        }
        BigDecimal before = inventory.getAvailableQty();
        inventory.setReservedQty(inventory.getReservedQty().subtract(quantity));
        inventory.setAvailableQty(before.add(quantity));
        inventoryMapper.updateById(inventory);
        writeLedger(itemId, "RELEASE", "ORDER", orderId, quantity, before, inventory.getAvailableQty());
    }

    public List<InventoryLedger> listLedger(Long itemId) {
        LambdaQueryWrapper<InventoryLedger> wrapper = new LambdaQueryWrapper<>();
        if (itemId != null) {
            wrapper.eq(InventoryLedger::getItemId, itemId);
        }
        wrapper.orderByDesc(InventoryLedger::getCreateTime);
        return ledgerMapper.selectList(wrapper);
    }

    private Inventory findInventory(Long itemId) {
        return inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getItemId, itemId));
    }

    private void writeLedger(Long itemId, String changeType, String refType, Long refId,
                             BigDecimal qtyChange, BigDecimal beforeQty, BigDecimal afterQty) {
        InventoryLedger ledger = new InventoryLedger();
        ledger.setItemId(itemId);
        ledger.setChangeType(changeType);
        ledger.setRefType(refType);
        ledger.setRefId(refId);
        ledger.setQtyChange(qtyChange);
        ledger.setBeforeQty(beforeQty);
        ledger.setAfterQty(afterQty);
        ledgerMapper.insert(ledger);
    }
}