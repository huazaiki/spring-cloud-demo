 package com.huazaiki.purchase.feign;

 import com.huazaiki.common.api.ApiResponse;
 import org.springframework.cloud.openfeign.FeignClient;
 import org.springframework.web.bind.annotation.PostMapping;
 import org.springframework.web.bind.annotation.RequestBody;

 import java.util.Map;

 @FeignClient(name = "inventory-service")
 public interface InventoryFeignClient {

     @PostMapping("/api/v1/inventory/reserve")
     ApiResponse<Void> reserveStock(@RequestBody Map<String, Object> body);

     @PostMapping("/api/v1/inventory/receive")
     ApiResponse<Void> receiveItem(@RequestBody Map<String, Object> body);
 }
