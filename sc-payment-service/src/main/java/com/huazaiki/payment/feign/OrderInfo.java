package com.huazaiki.payment.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * 采购订单只读快照（Feign 查询用，字段与 sc-purchase-service 的 PurchaseOrder 对齐）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderInfo {

    private Long id;
    private String orderNo;
    private Long supplierId;
    private BigDecimal totalAmount;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}