package com.huazaiki.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.purchase.approval.ApprovalEngine;
import com.huazaiki.purchase.approval.BizApprovalCallback;
import com.huazaiki.purchase.entity.PurchaseOrder;
import com.huazaiki.purchase.entity.PurchaseOrderItem;
import com.huazaiki.purchase.entity.PurchaseRequisition;
import com.huazaiki.purchase.entity.PurchaseRequisitionItem;
import com.huazaiki.purchase.mapper.PurchaseOrderItemMapper;
import com.huazaiki.purchase.mapper.PurchaseOrderMapper;
import com.huazaiki.purchase.mapper.PurchaseRequisitionItemMapper;
import com.huazaiki.purchase.mapper.PurchaseRequisitionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 请购单服务：创建/编辑/提交/审批（经 ApprovalEngine）/转采购订单/取消。
 */
@Service
public class RequisitionService implements BizApprovalCallback {

    private final PurchaseRequisitionMapper requisitionMapper;
    private final PurchaseRequisitionItemMapper itemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final ApprovalEngine approvalEngine;

    public RequisitionService(PurchaseRequisitionMapper requisitionMapper,
                              PurchaseRequisitionItemMapper itemMapper,
                              PurchaseOrderMapper orderMapper,
                              PurchaseOrderItemMapper orderItemMapper,
                              ApprovalEngine approvalEngine) {
        this.requisitionMapper = requisitionMapper;
        this.itemMapper = itemMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.approvalEngine = approvalEngine;
        approvalEngine.registerCallback("PR", this);
    }

    public List<PurchaseRequisition> list() {
        return requisitionMapper.selectList(new LambdaQueryWrapper<PurchaseRequisition>()
                .orderByDesc(PurchaseRequisition::getCreateTime));
    }

    public PurchaseRequisition getById(Long id) {
        return require(id);
    }

    @Transactional
    public PurchaseRequisition create(Long applicantId, Long deptId, Long supplierId,
                                      LocalDate expectedDate, String purpose, List<ItemLine> items) {
        PurchaseRequisition pr = new PurchaseRequisition();
        pr.setPrNo(generatePrNo());
        pr.setApplicantId(applicantId);
        pr.setApplicantDeptId(deptId);
        pr.setSupplierId(supplierId);
        pr.setExpectedDate(expectedDate);
        pr.setPurpose(purpose);
        pr.setStatus("DRAFT");
        BigDecimal total = BigDecimal.ZERO;
        for (ItemLine it : items) {
            total = total.add(it.amount());
        }
        pr.setTotalAmount(total);
        requisitionMapper.insert(pr);
        for (ItemLine it : items) {
            PurchaseRequisitionItem item = new PurchaseRequisitionItem();
            item.setPrId(pr.getId());
            item.setItemId(it.itemId());
            item.setItemName(it.itemName());
            item.setQuantity(it.quantity());
            itemMapper.insert(item);
        }
        return pr;
    }

    @Transactional
    public void submit(Long prId, Long operatorId) {
        PurchaseRequisition pr = require(prId);
        if (!"DRAFT".equals(pr.getStatus())) {
            throw new BusinessException(() -> 400, "Only DRAFT requisitions can be submitted");
        }
        Map<String, Object> snapshot = Map.of(
                "totalAmount", pr.getTotalAmount(),
                "applicantDeptId", pr.getApplicantDeptId());
        approvalEngine.submit("PR", prId, snapshot, this);
    }

    @Transactional
    public PurchaseOrder convert(Long prId) {
        PurchaseRequisition pr = require(prId);
        if (!"APPROVED".equals(pr.getStatus())) {
            throw new BusinessException(() -> 400, "Only APPROVED requisitions can be converted");
        }
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(generateOrderNo());
        order.setSupplierId(pr.getSupplierId());
        order.setTotalAmount(pr.getTotalAmount());
        order.setStatus("DRAFT");
        orderMapper.insert(order);

        List<PurchaseRequisitionItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequisitionItem>().eq(PurchaseRequisitionItem::getPrId, prId));
        for (PurchaseRequisitionItem it : items) {
            PurchaseOrderItem oi = new PurchaseOrderItem();
            oi.setOrderId(order.getId());
            oi.setItemId(it.getItemId());
            oi.setItemName(it.getItemName());
            oi.setQuantity(it.getQuantity());
            oi.setUnitPrice(BigDecimal.ZERO);
            oi.setAmount(BigDecimal.ZERO);
            orderItemMapper.insert(oi);
        }
        pr.setStatus("CONVERTED");
        requisitionMapper.updateById(pr);
        return order;
    }

    @Transactional
    public void cancel(Long prId, Long operatorId) {
        PurchaseRequisition pr = require(prId);
        if (!List.of("DRAFT", "SUBMITTED", "APPROVED").contains(pr.getStatus())) {
            throw new BusinessException(() -> 400, "Requisition in status " + pr.getStatus() + " cannot be cancelled");
        }
        approvalEngine.cancel("PR", prId, operatorId);
    }

    // ---------- 审批回调 ----------

    @Override
    public void onSubmitted(String bizType, Long bizId) {
        PurchaseRequisition pr = require(bizId);
        pr.setStatus("SUBMITTED");
        requisitionMapper.updateById(pr);
    }

    @Override
    public void onApproved(String bizType, Long bizId) {
        PurchaseRequisition pr = require(bizId);
        pr.setStatus("APPROVED");
        requisitionMapper.updateById(pr);
    }

    @Override
    public void onRejectedBack(String bizType, Long bizId) {
        PurchaseRequisition pr = require(bizId);
        pr.setStatus("DRAFT");
        requisitionMapper.updateById(pr);
    }

    @Override
    public void onRejectedTerminal(String bizType, Long bizId) {
        PurchaseRequisition pr = require(bizId);
        pr.setStatus("REJECTED");
        requisitionMapper.updateById(pr);
    }

    @Override
    public void onCancelled(String bizType, Long bizId) {
        PurchaseRequisition pr = require(bizId);
        pr.setStatus("CANCELLED");
        requisitionMapper.updateById(pr);
    }

    private PurchaseRequisition require(Long id) {
        PurchaseRequisition pr = requisitionMapper.selectById(id);
        if (pr == null) {
            throw new BusinessException(() -> 404, "Requisition not found: " + id);
        }
        return pr;
    }

    private String generatePrNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuidPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "PR-" + datePart + "-" + uuidPart;
    }

    private String generateOrderNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuidPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "PO-" + datePart + "-" + uuidPart;
    }

    public record ItemLine(Long itemId, String itemName, BigDecimal quantity, BigDecimal amount) {}
}