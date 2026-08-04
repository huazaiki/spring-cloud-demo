package com.huazaiki.purchase.controller;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.common.security.RequirePermission;
import com.huazaiki.purchase.entity.PurchaseOrder;
import com.huazaiki.purchase.entity.PurchaseRequisition;
import com.huazaiki.purchase.service.RequisitionService;
import com.huazaiki.purchase.service.RequisitionService.ItemLine;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/requisitions")
public class RequisitionController {

    private final RequisitionService requisitionService;

    public RequisitionController(RequisitionService requisitionService) {
        this.requisitionService = requisitionService;
    }

    @RequirePermission("pr:view")
    @GetMapping
    public ApiResponse<List<PurchaseRequisition>> list() {
        return ApiResponse.success(requisitionService.list());
    }

    @RequirePermission("pr:view")
    @GetMapping("/{id}")
    public ApiResponse<PurchaseRequisition> getById(@PathVariable Long id) {
        return ApiResponse.success(requisitionService.getById(id));
    }

    @RequirePermission("pr:create")
    @PostMapping
    public ApiResponse<PurchaseRequisition> create(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Dept", required = false) String deptId,
            @RequestBody Map<String, Object> body) {
        Long supplierId = body.get("supplierId") != null ? Long.valueOf(body.get("supplierId").toString()) : null;
        LocalDate expectedDate = body.get("expectedDate") != null
                ? LocalDate.parse(body.get("expectedDate").toString()) : null;
        String purpose = (String) body.get("purpose");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");
        List<ItemLine> items = new ArrayList<>();
        if (rawItems != null) {
            for (Map<String, Object> it : rawItems) {
                items.add(new ItemLine(
                        Long.valueOf(it.get("itemId").toString()),
                        (String) it.get("itemName"),
                        new BigDecimal(it.get("quantity").toString()),
                        new BigDecimal(it.getOrDefault("amount", "0").toString())));
            }
        }
        Long applicantDeptId = deptId != null && !deptId.isBlank() ? Long.valueOf(deptId) : 0L;
        return ApiResponse.success(requisitionService.create(
                Long.valueOf(userId), applicantDeptId, supplierId, expectedDate, purpose, items));
    }

    @RequirePermission("pr:submit")
    @PostMapping("/{id}/submit")
    public ApiResponse<Void> submit(@PathVariable Long id, @RequestHeader("X-User-Id") String userId) {
        requisitionService.submit(id, Long.valueOf(userId));
        return ApiResponse.success();
    }

    @RequirePermission("pr:convert")
    @PostMapping("/{id}/convert")
    public ApiResponse<PurchaseOrder> convert(@PathVariable Long id) {
        return ApiResponse.success(requisitionService.convert(id));
    }

    @RequirePermission("pr:update")
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id, @RequestHeader("X-User-Id") String userId) {
        requisitionService.cancel(id, Long.valueOf(userId));
        return ApiResponse.success();
    }
}