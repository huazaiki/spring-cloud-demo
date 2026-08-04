package com.huazaiki.payment.controller;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.security.RequirePermission;
import com.huazaiki.payment.entity.Invoice;
import com.huazaiki.payment.entity.InvoiceMatch;
import com.huazaiki.payment.entity.Payable;
import com.huazaiki.payment.entity.Payment;
import com.huazaiki.payment.service.PaymentService;
import com.huazaiki.payment.service.PaymentService.InvoiceLine;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // ---------- 应付账款（/api/v1/payments 为兼容旧命名，语义=应付管理） ----------

    @GetMapping("/api/v1/payments")
    public ApiResponse<List<Payable>> list() {
        return ApiResponse.success(paymentService.listPayables());
    }

    @PostMapping("/api/v1/payments")
    public ApiResponse<Payable> create(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        Long supplierId = Long.valueOf(body.get("supplierId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        return ApiResponse.success(paymentService.createPayable(orderId, supplierId, amount));
    }

    @RequirePermission("approval:task:approve")
    @PutMapping("/api/v1/payments/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        paymentService.approvePayment(id);
        return ApiResponse.success();
    }

    @RequirePermission("payment:pay")
    @PutMapping("/api/v1/payments/{id}/settle")
    public ApiResponse<Void> settle(@PathVariable Long id) {
        paymentService.settlePayable(id);
        return ApiResponse.success();
    }

    // ---------- 发票 ----------

    @RequirePermission("invoice:view")
    @GetMapping("/api/v1/invoices")
    public ApiResponse<List<Invoice>> listInvoices() {
        return ApiResponse.success(paymentService.listInvoices());
    }

    @RequirePermission("invoice:view")
    @GetMapping("/api/v1/invoices/{id}")
    public ApiResponse<Invoice> getInvoice(@PathVariable Long id) {
        return ApiResponse.success(paymentService.getInvoice(id));
    }

    @RequirePermission("invoice:create")
    @PostMapping("/api/v1/invoices")
    public ApiResponse<Invoice> createInvoice(@RequestBody Map<String, Object> body) {
        Long supplierId = Long.valueOf(body.get("supplierId").toString());
        Long orderId = body.get("orderId") != null ? Long.valueOf(body.get("orderId").toString()) : null;
        String invoiceNo = (String) body.get("invoiceNo");
        LocalDate invoiceDate = LocalDate.parse(body.get("invoiceDate").toString());
        BigDecimal totalAmount = new BigDecimal(body.get("totalAmount").toString());
        BigDecimal taxAmount = body.get("taxAmount") != null ? new BigDecimal(body.get("taxAmount").toString()) : BigDecimal.ZERO;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");
        List<InvoiceLine> items = new ArrayList<>();
        if (rawItems != null) {
            for (Map<String, Object> it : rawItems) {
                items.add(new InvoiceLine(
                        it.get("orderItemId") != null ? Long.valueOf(it.get("orderItemId").toString()) : null,
                        Long.valueOf(it.get("itemId").toString()),
                        new BigDecimal(it.get("quantity").toString()),
                        new BigDecimal(it.get("unitPrice").toString()),
                        new BigDecimal(it.get("amount").toString())));
            }
        }
        return ApiResponse.success(paymentService.createInvoice(
                supplierId, orderId, invoiceNo, invoiceDate, totalAmount, taxAmount, items));
    }

    @RequirePermission("invoice:match")
    @PostMapping("/api/v1/invoices/{id}/match")
    public ApiResponse<InvoiceMatch> match(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long receiveId = body.get("receiveId") != null ? Long.valueOf(body.get("receiveId").toString()) : null;
        return ApiResponse.success(paymentService.matchInvoice(id, receiveId));
    }

    // ---------- 付款单 ----------

    @RequirePermission("payment:view")
    @GetMapping("/api/v1/payment-vouchers")
    public ApiResponse<List<Payment>> listPayments() {
        return ApiResponse.success(paymentService.listPayments());
    }

    @RequirePermission("payment:pay")
    @PostMapping("/api/v1/payment-vouchers")
    public ApiResponse<Payment> payVoucher(@RequestBody Map<String, Object> body) {
        Long supplierId = Long.valueOf(body.get("supplierId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String method = (String) body.getOrDefault("method", "TRANSFER");
        @SuppressWarnings("unchecked")
        List<Object> rawPayableIds = (List<Object>) body.get("payableIds");
        List<Long> payableIds = new ArrayList<>();
        if (rawPayableIds != null) {
            for (Object o : rawPayableIds) {
                payableIds.add(Long.valueOf(o.toString()));
            }
        }
        return ApiResponse.success(paymentService.payVoucher(supplierId, amount, method, payableIds));
    }
}