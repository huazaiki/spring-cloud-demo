package com.huazaiki.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.inventory.entity.Inventory;
import com.huazaiki.inventory.entity.Item;
import com.huazaiki.inventory.entity.ReceiveRecord;
import com.huazaiki.inventory.mapper.InventoryMapper;
import com.huazaiki.inventory.mapper.ItemMapper;
import com.huazaiki.inventory.mapper.ReceiveRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class InventoryService {

    private final ItemMapper itemMapper;
    private final InventoryMapper inventoryMapper;
    private final ReceiveRecordMapper receiveRecordMapper;

    public InventoryService(ItemMapper itemMapper, InventoryMapper inventoryMapper, ReceiveRecordMapper receiveRecordMapper) {
        this.itemMapper = itemMapper;
        this.inventoryMapper = inventoryMapper;
        this.receiveRecordMapper = receiveRecordMapper;
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

    @Transactional
    public void receiveItem(Long orderId, Long itemId, BigDecimal quantity) {
        ReceiveRecord record = new ReceiveRecord();
        record.setOrderId(orderId);
        record.setItemId(itemId);
        record.setQuantity(quantity);
        record.setReceivedAt(LocalDateTime.now());
        receiveRecordMapper.insert(record);

        Inventory inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getItemId, itemId));

        if (inventory == null) {
            inventory = new Inventory();
            inventory.setItemId(itemId);
            inventory.setAvailableQty(quantity);
            inventory.setReservedQty(BigDecimal.ZERO);
            inventoryMapper.insert(inventory);
        } else {
            inventory.setAvailableQty(inventory.getAvailableQty().add(quantity));
            inventoryMapper.updateById(inventory);
        }
    }

    @Transactional
    public void reserveStock(Long itemId, BigDecimal quantity) {
        Inventory inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getItemId, itemId));

        if (inventory == null || inventory.getAvailableQty().compareTo(quantity) < 0) {
            throw new BusinessException(() -> 400, "Insufficient stock for item: " + itemId);
        }

        inventory.setAvailableQty(inventory.getAvailableQty().subtract(quantity));
        inventory.setReservedQty(inventory.getReservedQty().add(quantity));
        inventoryMapper.updateById(inventory);
    }
}
