package com.huazaiki.inventory.controller;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.security.RequirePermission;
import com.huazaiki.inventory.entity.InventoryLedger;
import com.huazaiki.inventory.entity.Item;
import com.huazaiki.inventory.entity.QualityInspection;
import com.huazaiki.inventory.entity.Receive;
import com.huazaiki.inventory.service.InventoryService;
import com.huazaiki.inventory.service.InventoryService.ReceiveLine;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/inventory/items")
    public ApiResponse<List<Item>> listItems() {
        return ApiResponse.success(inventoryService.listItems());
    }

    @PostMapping("/inventory/items")
    public ApiResponse<Item> createItem(@RequestBody Map<String, String> body) {
        Item item = inventoryService.createItem(
                body.get("name"), body.get("spec"),
                body.get("unit"), body.get("sku"));
        return ApiResponse.success(item);
    }

    // 内部 Feign / 兼容旧接口，不做权限注解
    @PostMapping("/inventory/receive")
    public ApiResponse<Void> receive(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        Long itemId = Long.valueOf(body.get("itemId").toString());
        BigDecimal quantity = new BigDecimal(body.get("quantity").toString());
        inventoryService.receiveItem(orderId, itemId, quantity);
        return ApiResponse.success();
    }

    // 内部 Feign（purchase→inventory）
    @PostMapping("/inventory/reserve")
    public ApiResponse<Void> reserve(@RequestBody Map<String, Object> body) {
        Long itemId = Long.valueOf(body.get("itemId").toString());
        BigDecimal quantity = new BigDecimal(body.get("quantity").toString());
        Long orderId = body.get("orderId") != null ? Long.valueOf(body.get("orderId").toString()) : null;
        inventoryService.reserveStock(itemId, quantity, orderId);
        return ApiResponse.success();
    }

    // 内部 Feign / 事件消费
    @PostMapping("/inventory/release")
    public ApiResponse<Void> release(@RequestBody Map<String, Object> body) {
        Long itemId = Long.valueOf(body.get("itemId").toString());
        BigDecimal quantity = new BigDecimal(body.get("quantity").toString());
        Long orderId = body.get("orderId") != null ? Long.valueOf(body.get("orderId").toString()) : null;
        inventoryService.releaseReservation(itemId, quantity, orderId);
        return ApiResponse.success();
    }

    @RequirePermission("inventory:ledger")
    @GetMapping("/inventory/ledger")
    public ApiResponse<List<InventoryLedger>> ledger(@RequestParam(required = false) Long itemId) {
        return ApiResponse.success(inventoryService.listLedger(itemId));
    }

    // ---------- 收货 / 质检 / 入库 ----------

    @RequirePermission("receive:create")
    @PostMapping("/receives")
    public ApiResponse<Receive> createReceive(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        Long supplierId = body.get("supplierId") != null ? Long.valueOf(body.get("supplierId").toString()) : null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");
        List<ReceiveLine> items = new ArrayList<>();
        if (rawItems != null) {
            for (Map<String, Object> it : rawItems) {
                items.add(new ReceiveLine(
                        Long.valueOf(it.get("orderItemId").toString()),
                        Long.valueOf(it.get("itemId").toString()),
                        new BigDecimal(it.getOrDefault("orderQty", it.get("receivedQty")).toString()),
                        new BigDecimal(it.get("receivedQty").toString()),
                        (String) it.get("remark")));
            }
        }
        return ApiResponse.success(inventoryService.createReceive(orderId, supplierId, items));
    }

    @RequirePermission("receive:view")
    @GetMapping("/receives")
    public ApiResponse<List<Receive>> listReceives() {
        return ApiResponse.success(inventoryService.listReceives());
    }

    @RequirePermission("receive:view")
    @GetMapping("/receives/{id}")
    public ApiResponse<Receive> getReceive(@PathVariable Long id) {
        return ApiResponse.success(inventoryService.getReceive(id));
    }

    @RequirePermission("qc:create")
    @PostMapping("/quality-inspections")
    public ApiResponse<QualityInspection> createQualityInspection(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> body) {
        QualityInspection qc = inventoryService.createQualityInspection(
                Long.valueOf(body.get("receiveItemId").toString()),
                Long.valueOf(userId),
                (String) body.getOrDefault("inspectType", "FULL"),
                new BigDecimal(body.get("inspectQty").toString()),
                new BigDecimal(body.get("qualifiedQty").toString()));
        return ApiResponse.success(qc);
    }

    @RequirePermission("stock:stock-in")
    @PostMapping("/receives/{id}/stock-in")
    public ApiResponse<Void> stockIn(@PathVariable Long id) {
        inventoryService.stockIn(id);
        return ApiResponse.success();
    }
}