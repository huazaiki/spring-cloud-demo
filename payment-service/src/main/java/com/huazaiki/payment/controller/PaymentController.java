package com.huazaiki.payment.controller;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.payment.entity.Payable;
import com.huazaiki.payment.service.PaymentService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public ApiResponse<List<Payable>> list() {
        return ApiResponse.success(paymentService.listPayables());
    }

    @PostMapping
    public ApiResponse<Payable> create(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        Long supplierId = Long.valueOf(body.get("supplierId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        return ApiResponse.success(paymentService.createPayable(orderId, supplierId, amount));
    }

    @PutMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        paymentService.approvePayment(id);
        return ApiResponse.success();
    }
}