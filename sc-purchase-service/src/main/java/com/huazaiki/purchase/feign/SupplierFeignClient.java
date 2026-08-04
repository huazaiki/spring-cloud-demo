package com.huazaiki.purchase.feign;

import com.huazaiki.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "sc-supplier-service", fallback = SupplierFeignFallback.class)
public interface SupplierFeignClient {

    @GetMapping("/api/v1/suppliers/{id}")
    ApiResponse<Map<String, Object>> getById(@PathVariable Long id);
}
