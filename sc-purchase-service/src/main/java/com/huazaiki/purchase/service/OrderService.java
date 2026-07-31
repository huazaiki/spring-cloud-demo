package com.huazaiki.purchase.service;

import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.purchase.entity.PurchaseOrder;
import com.huazaiki.purchase.entity.PurchaseOrderItem;
import com.huazaiki.purchase.feign.InventoryFeignClient;
import com.huazaiki.purchase.feign.SupplierFeignClient;
import com.huazaiki.purchase.mapper.PurchaseOrderItemMapper;
import com.huazaiki.purchase.mapper.PurchaseOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final InventoryFeignClient inventoryClient;
    private final SupplierFeignClient supplierClient;

    public OrderService(PurchaseOrderMapper orderMapper,
                        PurchaseOrderItemMapper itemMapper,
                        InventoryFeignClient inventoryClient,
                        SupplierFeignClient supplierClient) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.inventoryClient = inventoryClient;
        this.supplierClient = supplierClient;
    }

    public List<PurchaseOrder> listOrders() {
        return orderMapper.selectList(null);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder createOrder(Long supplierId, List<ItemLine> items) {
        // verify supplier exists via Feign
        var supplierResp = supplierClient.getById(supplierId);
        if (supplierResp.getCode() != 200 || supplierResp.getData() == null) {
            throw new BusinessException(() -> 404, "Supplier not found: " + supplierId);
        }

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(generateOrderNo());
        order.setSupplierId(supplierId);
        order.setStatus("DRAFT");

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ItemLine item : items) {
            totalAmount = totalAmount.add(item.amount());
        }
        order.setTotalAmount(totalAmount);
        orderMapper.insert(order);

        for (ItemLine item : items) {
            PurchaseOrderItem orderItem = new PurchaseOrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setItemId(item.itemId());
            orderItem.setItemName(item.itemName());
            orderItem.setQuantity(item.quantity());
            orderItem.setUnitPrice(item.unitPrice());
            orderItem.setAmount(item.amount());
            itemMapper.insert(orderItem);
        }

        // reserve stock for each item via Feign
        for (ItemLine item : items) {
            inventoryClient.reserveStock(Map.of(
                "itemId", item.itemId(),
                "quantity", item.quantity()
            ));
        }

        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveOrder(Long orderId) {
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(() -> 404, "Order not found: " + orderId);
        }
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessException(() -> 400, "Only DRAFT orders can be approved");
        }
        order.setStatus("APPROVED");
        orderMapper.updateById(order);

        // query items for this order, then reserve stock via Feign
        var wrapper = new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, orderId);
        List<PurchaseOrderItem> items = itemMapper.selectList(wrapper);
        for (PurchaseOrderItem item : items) {
            inventoryClient.reserveStock(Map.of(
                "itemId", item.getItemId(),
                "quantity", item.getQuantity()
            ));
        }
    }

    public PurchaseOrder getById(Long id) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(() -> 404, "Order not found: " + id);
        }
        return order;
    }

    private String generateOrderNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuidPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "PO-" + datePart + "-" + uuidPart;
    }

    public record ItemLine(Long itemId, String itemName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {}
}
