package com.huazaiki.purchase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("purchase_requisition_item")
public class PurchaseRequisitionItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long prId;
    private Long itemId;
    private String itemName;
    private String spec;
    private String unit;
    private BigDecimal quantity;
    private LocalDate expectedDate;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPrId() { return prId; }
    public void setPrId(Long prId) { this.prId = prId; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public LocalDate getExpectedDate() { return expectedDate; }
    public void setExpectedDate(LocalDate expectedDate) { this.expectedDate = expectedDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}