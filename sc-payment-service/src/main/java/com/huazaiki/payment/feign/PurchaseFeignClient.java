package com.huazaiki.payment.feign;

import com.huazaiki.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "sc-purchase-service")
public interface PurchaseFeignClient {

    @GetMapping("/api/v1/orders/{id}")
    ApiResponse<OrderInfo> getById(@PathVariable("id") Long id);
}