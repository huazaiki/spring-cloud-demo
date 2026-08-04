package com.huazaiki.purchase.controller;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.purchase.entity.PurchaseOrder;
import com.huazaiki.purchase.service.OrderService;
import com.huazaiki.purchase.service.OrderService.ItemLine;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class PurchaseController {

    private final OrderService orderService;

    public PurchaseController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<List<PurchaseOrder>> list() {
        return ApiResponse.success(orderService.listOrders());
    }

    @GetMapping("/{id}")
    public ApiResponse<PurchaseOrder> getById(@PathVariable Long id) {
        return ApiResponse.success(orderService.getById(id));
    }

    @PostMapping
    public ApiResponse<PurchaseOrder> create(@RequestBody Map<String, Object> body) {
        Long supplierId = Long.valueOf(body.get("supplierId").toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");
        List<ItemLine> items = new ArrayList<>();
        if (rawItems != null) {
            for (Map<String, Object> it : rawItems) {
                items.add(new ItemLine(
                    Long.valueOf(it.get("itemId").toString()),
                    (String) it.get("itemName"),
                    new BigDecimal(it.get("quantity").toString()),
                    new BigDecimal(it.get("unitPrice").toString()),
                    new BigDecimal(it.getOrDefault("amount", "0").toString())
                ));
            }
        }
        return ApiResponse.success(orderService.createOrder(supplierId, items));
    }

    @PutMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        orderService.approveOrder(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return ApiResponse.success();
    }
}