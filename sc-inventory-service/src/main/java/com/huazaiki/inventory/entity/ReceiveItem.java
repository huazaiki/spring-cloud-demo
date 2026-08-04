package com.huazaiki.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

@TableName("receive_item")
public class ReceiveItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long receiveId;
    private Long orderItemId;
    private Long itemId;
    private BigDecimal orderQty;
    private BigDecimal receivedQty;
    private BigDecimal diffQty;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReceiveId() { return receiveId; }
    public void setReceiveId(Long receiveId) { this.receiveId = receiveId; }
    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public BigDecimal getOrderQty() { return orderQty; }
    public void setOrderQty(BigDecimal orderQty) { this.orderQty = orderQty; }
    public BigDecimal getReceivedQty() { return receivedQty; }
    public void setReceivedQty(BigDecimal receivedQty) { this.receivedQty = receivedQty; }
    public BigDecimal getDiffQty() { return diffQty; }
    public void setDiffQty(BigDecimal diffQty) { this.diffQty = diffQty; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}