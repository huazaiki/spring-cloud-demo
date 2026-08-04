package com.huazaiki.purchase.controller;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.security.RequirePermission;
import com.huazaiki.purchase.entity.PurchaseOrder;
import com.huazaiki.purchase.mapper.PurchaseOrderMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采购报表（docs/design/api-contract.md §2.9）。
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequirePermission("report:procurement")
public class ProcurementReportController {

    private final PurchaseOrderMapper orderMapper;

    public ProcurementReportController(PurchaseOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @GetMapping("/procurement-dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        List<PurchaseOrder> orders = orderMapper.selectList(null);
        BigDecimal totalAmount = orders.stream()
                .map(PurchaseOrder::getTotalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> countByStatus = orders.stream()
                .collect(Collectors.groupingBy(PurchaseOrder::getStatus, Collectors.counting()));
        Map<String, BigDecimal> amountByStatus = orders.stream()
                .collect(Collectors.groupingBy(PurchaseOrder::getStatus,
                        Collectors.mapping(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount(),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        List<Map<String, Object>> byStatus = new ArrayList<>();
        countByStatus.forEach((status, count) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", status);
            row.put("count", count);
            row.put("amount", amountByStatus.getOrDefault(status, BigDecimal.ZERO));
            byStatus.add(row);
        });

        return ApiResponse.success(Map.of(
                "totalOrders", orders.size(),
                "totalAmount", totalAmount,
                "byStatus", byStatus));
    }
}