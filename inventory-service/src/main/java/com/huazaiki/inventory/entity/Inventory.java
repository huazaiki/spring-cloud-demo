package com.huazaiki.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("inventory")
public class Inventory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long itemId;
    private BigDecimal availableQty;
    private BigDecimal reservedQty;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(BigDecimal availableQty) { this.availableQty = availableQty; }
    public BigDecimal getReservedQty() { return reservedQty; }
    public void setReservedQty(BigDecimal reservedQty) { this.reservedQty = reservedQty; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
