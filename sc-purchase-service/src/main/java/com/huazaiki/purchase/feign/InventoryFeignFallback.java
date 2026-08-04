package com.huazaiki.purchase.feign;

import com.huazaiki.common.api.ApiResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InventoryFeignFallback implements InventoryFeignClient {

    @Override
    public ApiResponse<Void> reserveStock(Map<String, Object> body) {
        return ApiResponse.fail(503, "inventory-service unavailable (circuit open or timeout)");
    }

    @Override
    public ApiResponse<Void> receiveItem(Map<String, Object> body) {
        return ApiResponse.fail(503, "inventory-service unavailable (circuit open or timeout)");
    }
}
