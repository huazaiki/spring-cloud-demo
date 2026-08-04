package com.huazaiki.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("invoice_match")
public class InvoiceMatch {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long invoiceId;
    private Long orderId;
    private Long receiveId;
    private BigDecimal quantityDiff;
    private BigDecimal amountDiff;
    private String matchStatus;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getReceiveId() { return receiveId; }
    public void setReceiveId(Long receiveId) { this.receiveId = receiveId; }
    public BigDecimal getQuantityDiff() { return quantityDiff; }
    public void setQuantityDiff(BigDecimal quantityDiff) { this.quantityDiff = quantityDiff; }
    public BigDecimal getAmountDiff() { return amountDiff; }
    public void setAmountDiff(BigDecimal amountDiff) { this.amountDiff = amountDiff; }
    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}