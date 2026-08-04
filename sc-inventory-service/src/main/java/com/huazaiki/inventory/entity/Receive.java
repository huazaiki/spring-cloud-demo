package com.huazaiki.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("receive")
public class Receive {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String receiveNo;
    private Long orderId;
    private Long supplierId;
    private LocalDateTime receiveDate;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReceiveNo() { return receiveNo; }
    public void setReceiveNo(String receiveNo) { this.receiveNo = receiveNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public LocalDateTime getReceiveDate() { return receiveDate; }
    public void setReceiveDate(LocalDateTime receiveDate) { this.receiveDate = receiveDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}