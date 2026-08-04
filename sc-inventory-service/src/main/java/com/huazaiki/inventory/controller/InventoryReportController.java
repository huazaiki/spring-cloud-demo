package com.huazaiki.inventory.controller;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.security.RequirePermission;
import com.huazaiki.inventory.entity.Inventory;
import com.huazaiki.inventory.entity.Item;
import com.huazaiki.inventory.mapper.InventoryMapper;
import com.huazaiki.inventory.mapper.ItemMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存汇总报表（docs/design/api-contract.md §2.9）。
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequirePermission("report:inventory")
public class InventoryReportController {

    private final InventoryMapper inventoryMapper;
    private final ItemMapper itemMapper;

    public InventoryReportController(InventoryMapper inventoryMapper, ItemMapper itemMapper) {
        this.inventoryMapper = inventoryMapper;
        this.itemMapper = itemMapper;
    }

    @GetMapping("/inventory-summary")
    public ApiResponse<Map<String, Object>> summary() {
        List<Inventory> inventories = inventoryMapper.selectList(null);
        Map<Long, Item> itemMap = itemMapper.selectList(null).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity(), (a, b) -> a));

        BigDecimal totalAvailable = BigDecimal.ZERO;
        BigDecimal totalReserved = BigDecimal.ZERO;
        List<Map<String, Object>> lowStock = new ArrayList<>();
        for (Inventory inv : inventories) {
            BigDecimal avail = inv.getAvailableQty() == null ? BigDecimal.ZERO : inv.getAvailableQty();
            BigDecimal reserved = inv.getReservedQty() == null ? BigDecimal.ZERO : inv.getReservedQty();
            totalAvailable = totalAvailable.add(avail);
            totalReserved = totalReserved.add(reserved);
            Item item = itemMap.get(inv.getItemId());
            BigDecimal threshold = item != null && item.getReorderPoint() != null && item.getReorderPoint().signum() > 0
                    ? item.getReorderPoint() : BigDecimal.ZERO;
            if (item != null && threshold.signum() > 0 && avail.compareTo(threshold) <= 0) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("itemId", inv.getItemId());
                row.put("itemName", item.getName());
                row.put("available", avail);
                row.put("reorderPoint", threshold);
                lowStock.add(row);
            }
        }

        return ApiResponse.success(Map.of(
                "totalItems", inventories.size(),
                "totalAvailable", totalAvailable,
                "totalReserved", totalReserved,
                "lowStock", lowStock));
    }
}