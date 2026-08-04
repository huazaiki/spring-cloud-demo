package com.huazaiki.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.common.event.KafkaTopics;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.inventory.entity.Inventory;
import com.huazaiki.inventory.entity.InventoryLedger;
import com.huazaiki.inventory.entity.Item;
import com.huazaiki.inventory.entity.QualityInspection;
import com.huazaiki.inventory.entity.Receive;
import com.huazaiki.inventory.entity.ReceiveItem;
import com.huazaiki.inventory.entity.ReceiveRecord;
import com.huazaiki.inventory.mapper.InventoryLedgerMapper;
import com.huazaiki.inventory.mapper.InventoryMapper;
import com.huazaiki.inventory.mapper.ItemMapper;
import com.huazaiki.inventory.mapper.QualityInspectionMapper;
import com.huazaiki.inventory.mapper.ReceiveItemMapper;
import com.huazaiki.inventory.mapper.ReceiveMapper;
import com.huazaiki.inventory.mapper.ReceiveRecordMapper;
import com.huazaiki.inventory.outbox.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 库存服务（docs/design/schema.md §4）：
 * <ul>
 *   <li>预留 reserve：available − / reserved +，流水 RESERVE；</li>
 *   <li>入库核销：reserved − min(reserved, qty) / available + qty，流水 RECEIVE，并发布 StockInCompleted；</li>
 *   <li>释放预留 release：reserved − / available +，流水 RELEASE；</li>
 *   <li>收货→质检→入库：createReceive → createQualityInspection → stockIn（按合格数量入库）。</li>
 * </ul>
 */
@Service
public class InventoryService {

    private final ItemMapper itemMapper;
    private final InventoryMapper inventoryMapper;
    private final ReceiveRecordMapper receiveRecordMapper;
    private final InventoryLedgerMapper ledgerMapper;
    private final ReceiveMapper receiveMapper;
    private final ReceiveItemMapper receiveItemMapper;
    private final QualityInspectionMapper qualityInspectionMapper;
    private final OutboxService outboxService;

    public InventoryService(ItemMapper itemMapper,
                            InventoryMapper inventoryMapper,
                            ReceiveRecordMapper receiveRecordMapper,
                            InventoryLedgerMapper ledgerMapper,
                            ReceiveMapper receiveMapper,
                            ReceiveItemMapper receiveItemMapper,
                            QualityInspectionMapper qualityInspectionMapper,
                            OutboxService outboxService) {
        this.itemMapper = itemMapper;
        this.inventoryMapper = inventoryMapper;
        this.receiveRecordMapper = receiveRecordMapper;
        this.ledgerMapper = ledgerMapper;
        this.receiveMapper = receiveMapper;
        this.receiveItemMapper = receiveItemMapper;
        this.qualityInspectionMapper = qualityInspectionMapper;
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
     * 收货（旧接口，直接入库核销；保留兼容）。
     */
    @Transactional
    public void receiveItem(Long orderId, Long itemId, BigDecimal quantity) {
        ReceiveRecord record = new ReceiveRecord();
        record.setOrderId(orderId);
        record.setItemId(itemId);
        record.setQuantity(quantity);
        record.setReceivedAt(LocalDateTime.now());
        receiveRecordMapper.insert(record);
        applyStockIn(orderId, itemId, quantity, "stock-in:" + orderId + ":" + itemId + ":" + record.getId());
    }

    /**
     * 收货单登记（不做库存变动，质检通过后 stockIn 入库）。
     */
    @Transactional
    public Receive createReceive(Long orderId, Long supplierId, List<ReceiveLine> items) {
        Receive receive = new Receive();
        receive.setReceiveNo(generateReceiveNo());
        receive.setOrderId(orderId);
        receive.setSupplierId(supplierId);
        receive.setReceiveDate(LocalDateTime.now());
        receive.setStatus("RECEIVED");
        receiveMapper.insert(receive);
        for (ReceiveLine it : items) {
            ReceiveItem ri = new ReceiveItem();
            ri.setReceiveId(receive.getId());
            ri.setOrderItemId(it.orderItemId());
            ri.setItemId(it.itemId());
            ri.setOrderQty(it.orderQty());
            ri.setReceivedQty(it.receivedQty());
            ri.setDiffQty(it.receivedQty().subtract(it.orderQty()));
            ri.setRemark(it.remark());
            receiveItemMapper.insert(ri);
        }
        return receive;
    }

    /**
     * 质检登记（免检/全检/抽检，记录合格/不合格数量）。
     */
    @Transactional
    public QualityInspection createQualityInspection(Long receiveItemId, Long inspectorId,
                                                    String inspectType, BigDecimal inspectQty, BigDecimal qualifiedQty) {
        ReceiveItem ri = receiveItemMapper.selectById(receiveItemId);
        if (ri == null) {
            throw new BusinessException(() -> 404, "Receive item not found: " + receiveItemId);
        }
        Receive receive = receiveMapper.selectById(ri.getReceiveId());
        QualityInspection qc = new QualityInspection();
        qc.setInspectNo(generateInspectNo());
        qc.setReceiveItemId(receiveItemId);
        qc.setOrderId(receive != null ? receive.getOrderId() : null);
        qc.setItemId(ri.getItemId());
        qc.setInspectType(inspectType);
        qc.setInspectQty(inspectQty);
        qc.setQualifiedQty(qualifiedQty);
        qc.setUnqualifiedQty(inspectQty.subtract(qualifiedQty));
        qc.setResult(qualifiedQty.compareTo(BigDecimal.ZERO) == 0 ? "FAIL"
                : qualifiedQty.compareTo(inspectQty) == 0 ? "PASS" : "PARTIAL");
        qc.setInspectorId(inspectorId);
        qc.setInspectTime(LocalDateTime.now());
        qualityInspectionMapper.insert(qc);
        return qc;
    }

    /**
     * 入库：按质检合格数量（无质检记录视为全部合格）核销预留并入可用，写流水并发 StockInCompleted。
     */
    @Transactional
    public void stockIn(Long receiveId) {
        Receive receive = receiveMapper.selectById(receiveId);
        if (receive == null) {
            throw new BusinessException(() -> 404, "Receive not found: " + receiveId);
        }
        List<ReceiveItem> items = receiveItemMapper.selectList(
                new LambdaQueryWrapper<ReceiveItem>().eq(ReceiveItem::getReceiveId, receiveId));
        for (ReceiveItem ri : items) {
            QualityInspection qc = qualityInspectionMapper.selectOne(
                    new LambdaQueryWrapper<QualityInspection>().eq(QualityInspection::getReceiveItemId, ri.getId()));
            BigDecimal qty = qc != null ? qc.getQualifiedQty() : ri.getReceivedQty();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            applyStockIn(receive.getOrderId(), ri.getItemId(), qty,
                    "stock-in:" + receive.getOrderId() + ":" + ri.getItemId() + ":" + receiveId);
        }
    }

    public List<Receive> listReceives() {
        return receiveMapper.selectList(new LambdaQueryWrapper<Receive>().orderByDesc(Receive::getCreateTime));
    }

    public Receive getReceive(Long id) {
        return receiveMapper.selectById(id);
    }

    /**
     * 预留：available→reserved。
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

    private void applyStockIn(Long orderId, Long itemId, BigDecimal qty, String idempotencyKey) {
        Inventory inventory = findInventory(itemId);
        BigDecimal beforeAvailable;
        if (inventory == null) {
            beforeAvailable = BigDecimal.ZERO;
            Inventory created = new Inventory();
            created.setItemId(itemId);
            created.setAvailableQty(qty);
            created.setReservedQty(BigDecimal.ZERO);
            inventoryMapper.insert(created);
        } else {
            beforeAvailable = inventory.getAvailableQty();
            BigDecimal release = inventory.getReservedQty().min(qty);
            inventory.setReservedQty(inventory.getReservedQty().subtract(release));
            inventory.setAvailableQty(beforeAvailable.add(qty));
            inventoryMapper.updateById(inventory);
        }
        writeLedger(itemId, "RECEIVE", "ORDER", orderId, qty, beforeAvailable,
                inventory == null ? qty : inventory.getAvailableQty());
        outboxService.saveEvent(KafkaTopics.STOCK_IN_COMPLETED, "ORDER", orderId, idempotencyKey,
                Map.of("orderId", orderId, "itemId", itemId, "quantity", qty));
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

    private String generateReceiveNo() {
        return "RC-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String generateInspectNo() {
        return "QC-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public record ReceiveLine(Long orderItemId, Long itemId, BigDecimal orderQty, BigDecimal receivedQty, String remark) {}
}