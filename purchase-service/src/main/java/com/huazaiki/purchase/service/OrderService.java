package com.huazaiki.purchase.service;

import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.purchase.entity.PurchaseOrder;
import com.huazaiki.purchase.entity.PurchaseOrderItem;
import com.huazaiki.purchase.mapper.PurchaseOrderItemMapper;
import com.huazaiki.purchase.mapper.PurchaseOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;

    public OrderService(PurchaseOrderMapper orderMapper, PurchaseOrderItemMapper itemMapper) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
    }

    @Transactional
    public PurchaseOrder createOrder(Long supplierId, List<ItemLine> items) {
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

        return order;
    }

    @Transactional
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
