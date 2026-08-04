package com.huazaiki.inventory.controller;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.inventory.entity.InventoryLedger;
import com.huazaiki.inventory.entity.Item;
import com.huazaiki.inventory.service.InventoryService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/items")
    public ApiResponse<List<Item>> listItems() {
        return ApiResponse.success(inventoryService.listItems());
    }

    @PostMapping("/items")
    public ApiResponse<Item> createItem(@RequestBody Map<String, String> body) {
        Item item = inventoryService.createItem(
                body.get("name"), body.get("spec"),
                body.get("unit"), body.get("sku"));
        return ApiResponse.success(item);
    }

    @PostMapping("/receive")
    public ApiResponse<Void> receive(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        Long itemId = Long.valueOf(body.get("itemId").toString());
        BigDecimal quantity = new BigDecimal(body.get("quantity").toString());
        inventoryService.receiveItem(orderId, itemId, quantity);
        return ApiResponse.success();
    }

    @PostMapping("/reserve")
    public ApiResponse<Void> reserve(@RequestBody Map<String, Object> body) {
        Long itemId = Long.valueOf(body.get("itemId").toString());
        BigDecimal quantity = new BigDecimal(body.get("quantity").toString());
        Long orderId = body.get("orderId") != null ? Long.valueOf(body.get("orderId").toString()) : null;
        inventoryService.reserveStock(itemId, quantity, orderId);
        return ApiResponse.success();
    }

    @PostMapping("/release")
    public ApiResponse<Void> release(@RequestBody Map<String, Object> body) {
        Long itemId = Long.valueOf(body.get("itemId").toString());
        BigDecimal quantity = new BigDecimal(body.get("quantity").toString());
        Long orderId = body.get("orderId") != null ? Long.valueOf(body.get("orderId").toString()) : null;
        inventoryService.releaseReservation(itemId, quantity, orderId);
        return ApiResponse.success();
    }

    @GetMapping("/ledger")
    public ApiResponse<List<InventoryLedger>> ledger(@RequestParam(required = false) Long itemId) {
        return ApiResponse.success(inventoryService.listLedger(itemId));
    }
}