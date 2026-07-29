package com.huazaiki.supplier.controller;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.supplier.entity.Supplier;
import com.huazaiki.supplier.service.SupplierService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PostMapping
    public ApiResponse<Void> create(@RequestBody Map<String, String> body) {
        supplierService.create(
                body.get("name"),
                body.get("creditCode"),
                body.get("contactName"),
                body.get("contactPhone"));
        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    public ApiResponse<Supplier> getById(@PathVariable Long id) {
        return ApiResponse.success(supplierService.getById(id));
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name) {
        return ApiResponse.success(supplierService.list(page, size, name));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        supplierService.updateStatus(id, body.get("status"));
        return ApiResponse.success();
    }
}
