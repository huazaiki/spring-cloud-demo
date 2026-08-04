package com.huazaiki.purchase.service;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.event.KafkaTopics;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.purchase.entity.PurchaseOrder;
import com.huazaiki.purchase.entity.PurchaseOrderItem;
import com.huazaiki.purchase.feign.InventoryFeignClient;
import com.huazaiki.purchase.feign.SupplierFeignClient;
import com.huazaiki.purchase.mapper.PurchaseOrderItemMapper;
import com.huazaiki.purchase.mapper.PurchaseOrderMapper;
import com.huazaiki.purchase.outbox.OutboxService;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final InventoryFeignClient inventoryClient;
    private final SupplierFeignClient supplierClient;
    private final OutboxService outboxService;

    public OrderService(PurchaseOrderMapper orderMapper,
                        PurchaseOrderItemMapper itemMapper,
                        InventoryFeignClient inventoryClient,
                        SupplierFeignClient supplierClient,
                        OutboxService outboxService) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.inventoryClient = inventoryClient;
        this.supplierClient = supplierClient;
        this.outboxService = outboxService;
    }

    public List<PurchaseOrder> listOrders() {
        return orderMapper.selectList(null);
    }

    @GlobalTransactional(rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder createOrder(Long supplierId, List<ItemLine> items) {
        // verify supplier exists via Feign
        var supplierResp = supplierClient.getById(supplierId);
        if (supplierResp.getCode() != 200 || supplierResp.getData() == null) {
            throw new BusinessException(() -> supplierResp.getCode(),
                    "Supplier unavailable or not found: " + supplierId);
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

        // reserve stock for each item via Feign (protected by Resilience4j circuit breaker)
        for (ItemLine item : items) {
            ApiResponse<Void> resp = inventoryClient.reserveStock(Map.of(
                "itemId", item.itemId(),
                "quantity", item.quantity(),
                "orderId", order.getId()
            ));
            if (resp.getCode() != 200) {
                throw new BusinessException(() -> resp.getCode(),
                        "Stock reservation failed for item " + item.itemId() + ": " + resp.getMessage());
            }
        }

        return order;
    }

    @GlobalTransactional(rollbackFor = Exception.class)
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

        // query items for this order, then reserve stock via Feign (protected by circuit breaker)
        var wrapper = new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, orderId);
        List<PurchaseOrderItem> items = itemMapper.selectList(wrapper);
        for (PurchaseOrderItem item : items) {
            ApiResponse<Void> resp = inventoryClient.reserveStock(Map.of(
                "itemId", item.getItemId(),
                "quantity", item.getQuantity(),
                "orderId", order.getId()
            ));
            if (resp.getCode() != 200) {
                throw new BusinessException(() -> resp.getCode(),
                        "Stock reservation failed for item " + item.getItemId() + ": " + resp.getMessage());
            }
        }
    }

    /**
     * 取消订单：本地事务置 CANCELLED，并写 Outbox 事件 OrderCancelled（inventory 消费后释放预留）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(() -> 404, "Order not found: " + orderId);
        }
        if ("CANCELLED".equals(order.getStatus())) {
            return;
        }
        if (!List.of("DRAFT", "SUBMITTED", "APPROVED").contains(order.getStatus())) {
            throw new BusinessException(() -> 400, "Order in status " + order.getStatus() + " cannot be cancelled");
        }
        order.setStatus("CANCELLED");
        orderMapper.updateById(order);

        var wrapper = new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, orderId);
        List<PurchaseOrderItem> items = itemMapper.selectList(wrapper);
        List<Map<String, Object>> itemList = new ArrayList<>();
        for (PurchaseOrderItem item : items) {
            itemList.add(Map.of("itemId", item.getItemId(), "quantity", item.getQuantity()));
        }
        outboxService.saveEvent(KafkaTopics.ORDER_CANCELLED, "PO", orderId,
                "order-cancel:" + orderId,
                Map.of("orderId", orderId, "items", itemList));
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