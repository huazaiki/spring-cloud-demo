package com.huazaiki.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("quality_inspection")
public class QualityInspection {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String inspectNo;
    private Long receiveItemId;
    private Long orderId;
    private Long itemId;
    private String inspectType;
    private BigDecimal inspectQty;
    private BigDecimal qualifiedQty;
    private BigDecimal unqualifiedQty;
    private String result;
    private Long inspectorId;
    private LocalDateTime inspectTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getInspectNo() { return inspectNo; }
    public void setInspectNo(String inspectNo) { this.inspectNo = inspectNo; }
    public Long getReceiveItemId() { return receiveItemId; }
    public void setReceiveItemId(Long receiveItemId) { this.receiveItemId = receiveItemId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getInspectType() { return inspectType; }
    public void setInspectType(String inspectType) { this.inspectType = inspectType; }
    public BigDecimal getInspectQty() { return inspectQty; }
    public void setInspectQty(BigDecimal inspectQty) { this.inspectQty = inspectQty; }
    public BigDecimal getQualifiedQty() { return qualifiedQty; }
    public void setQualifiedQty(BigDecimal qualifiedQty) { this.qualifiedQty = qualifiedQty; }
    public BigDecimal getUnqualifiedQty() { return unqualifiedQty; }
    public void setUnqualifiedQty(BigDecimal unqualifiedQty) { this.unqualifiedQty = unqualifiedQty; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Long getInspectorId() { return inspectorId; }
    public void setInspectorId(Long inspectorId) { this.inspectorId = inspectorId; }
    public LocalDateTime getInspectTime() { return inspectTime; }
    public void setInspectTime(LocalDateTime inspectTime) { this.inspectTime = inspectTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
}