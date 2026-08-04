package com.huazaiki.purchase.feign;

import com.huazaiki.common.api.ApiResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SupplierFeignFallback implements SupplierFeignClient {

    @Override
    public ApiResponse<Map<String, Object>> getById(Long id) {
        return ApiResponse.fail(503, "supplier-service unavailable (circuit open or timeout)");
    }
}
