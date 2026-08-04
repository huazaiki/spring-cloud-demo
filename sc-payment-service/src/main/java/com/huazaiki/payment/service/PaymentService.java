package com.huazaiki.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.event.KafkaTopics;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.payment.entity.Invoice;
import com.huazaiki.payment.entity.InvoiceItem;
import com.huazaiki.payment.entity.InvoiceMatch;
import com.huazaiki.payment.entity.Payable;
import com.huazaiki.payment.entity.PayablePayment;
import com.huazaiki.payment.entity.Payment;
import com.huazaiki.payment.feign.OrderInfo;
import com.huazaiki.payment.feign.PurchaseFeignClient;
import com.huazaiki.payment.mapper.InvoiceItemMapper;
import com.huazaiki.payment.mapper.InvoiceMapper;
import com.huazaiki.payment.mapper.InvoiceMatchMapper;
import com.huazaiki.payment.mapper.PayableMapper;
import com.huazaiki.payment.mapper.PayablePaymentMapper;
import com.huazaiki.payment.mapper.PaymentMapper;
import com.huazaiki.payment.outbox.OutboxService;
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
 * 财务服务：应付账款 + 发票/三单匹配 + 付款单/核销（docs/design/schema.md §5）。
 */
@Service
public class PaymentService {

    private final PayableMapper payableMapper;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceItemMapper invoiceItemMapper;
    private final InvoiceMatchMapper invoiceMatchMapper;
    private final PaymentMapper paymentMapper;
    private final PayablePaymentMapper payablePaymentMapper;
    private final PurchaseFeignClient purchaseClient;
    private final OutboxService outboxService;

    public PaymentService(PayableMapper payableMapper,
                          InvoiceMapper invoiceMapper,
                          InvoiceItemMapper invoiceItemMapper,
                          InvoiceMatchMapper invoiceMatchMapper,
                          PaymentMapper paymentMapper,
                          PayablePaymentMapper payablePaymentMapper,
                          PurchaseFeignClient purchaseClient,
                          OutboxService outboxService) {
        this.payableMapper = payableMapper;
        this.invoiceMapper = invoiceMapper;
        this.invoiceItemMapper = invoiceItemMapper;
        this.invoiceMatchMapper = invoiceMatchMapper;
        this.paymentMapper = paymentMapper;
        this.payablePaymentMapper = payablePaymentMapper;
        this.purchaseClient = purchaseClient;
        this.outboxService = outboxService;
    }

    // ---------- 应付账款 ----------

    public List<Payable> listPayables() {
        return payableMapper.selectList(null);
    }

    @Transactional
    public Payable createPayable(Long orderId, Long supplierId, BigDecimal amount) {
        Payable payable = new Payable();
        payable.setOrderId(orderId);
        payable.setSupplierId(supplierId);
        payable.setAmount(amount);
        payable.setDueDate(LocalDate.now().plusDays(30));
        payable.setStatus("PENDING");
        payableMapper.insert(payable);
        return payable;
    }

    @Transactional
    public void approvePayment(Long id) {
        Payable payable = requirePayable(id);
        if (!"PENDING".equals(payable.getStatus())) {
            throw new BusinessException(() -> 400, "Only PENDING payables can be approved");
        }
        payable.setStatus("APPROVED");
        payableMapper.updateById(payable);
    }

    /**
     * 付款结算（单应付）：APPROVED → PAID + Outbox SettlementCompleted（幂等）。
     */
    @Transactional
    public void settlePayable(Long id) {
        Payable payable = requirePayable(id);
        if ("PAID".equals(payable.getStatus())) {
            return;
        }
        if (!"APPROVED".equals(payable.getStatus())) {
            throw new BusinessException(() -> 400, "Only APPROVED payables can be settled");
        }
        payable.setStatus("PAID");
        payableMapper.updateById(payable);
        outboxService.saveEvent(KafkaTopics.SETTLEMENT_COMPLETED, "PAYABLE", id,
                "settlement:" + id,
                Map.of("orderId", payable.getOrderId(), "payableId", id, "amount", payable.getAmount()));
    }

    // ---------- 发票 ----------

    public List<Invoice> listInvoices() {
        return invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>().orderByDesc(Invoice::getCreateTime));
    }

    public Invoice getInvoice(Long id) {
        return invoiceMapper.selectById(id);
    }

    @Transactional
    public Invoice createInvoice(Long supplierId, Long orderId, String invoiceNo, LocalDate invoiceDate,
                                 BigDecimal totalAmount, BigDecimal taxAmount, List<InvoiceLine> items) {
        Invoice invoice = new Invoice();
        invoice.setSupplierId(supplierId);
        invoice.setOrderId(orderId);
        invoice.setInvoiceNo(invoiceNo);
        invoice.setInvoiceDate(invoiceDate);
        invoice.setTotalAmount(totalAmount);
        invoice.setTaxAmount(taxAmount == null ? BigDecimal.ZERO : taxAmount);
        invoice.setStatus("REGISTERED");
        invoiceMapper.insert(invoice);
        if (items != null) {
            for (InvoiceLine it : items) {
                InvoiceItem ii = new InvoiceItem();
                ii.setInvoiceId(invoice.getId());
                ii.setOrderItemId(it.orderItemId());
                ii.setItemId(it.itemId());
                ii.setQuantity(it.quantity());
                ii.setUnitPrice(it.unitPrice());
                ii.setAmount(it.amount());
                invoiceItemMapper.insert(ii);
            }
        }
        return invoice;
    }

    /**
     * 三单匹配：发票金额 vs 订单金额（Feign 查 purchase），登记 invoice_match。
     */
    @Transactional
    public InvoiceMatch matchInvoice(Long invoiceId, Long receiveId) {
        Invoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new BusinessException(() -> 404, "Invoice not found: " + invoiceId);
        }
        ApiResponse<OrderInfo> resp = purchaseClient.getById(invoice.getOrderId());
        if (resp.getCode() != 200 || resp.getData() == null) {
            throw new IllegalStateException("Order not found for match, orderId=" + invoice.getOrderId());
        }
        BigDecimal amountDiff = invoice.getTotalAmount().subtract(resp.getData().getTotalAmount());

        InvoiceMatch match = new InvoiceMatch();
        match.setInvoiceId(invoiceId);
        match.setOrderId(invoice.getOrderId());
        match.setReceiveId(receiveId);
        match.setAmountDiff(amountDiff);
        match.setQuantityDiff(BigDecimal.ZERO);
        match.setMatchStatus(amountDiff.compareTo(BigDecimal.ZERO) == 0 ? "MATCHED" : "MISMATCH");
        invoiceMatchMapper.insert(match);

        if ("MATCHED".equals(match.getMatchStatus())) {
            invoice.setStatus("MATCHED");
            invoiceMapper.updateById(invoice);
        }
        return match;
    }

    // ---------- 付款单 ----------

    public List<Payment> listPayments() {
        return paymentMapper.selectList(new LambdaQueryWrapper<Payment>().orderByDesc(Payment::getCreateTime));
    }

    /**
     * 创建并执行付款单：payment DRAFT→PAID，核销指定应付（APPROVED→PAID + payable_payment + Outbox SettlementCompleted）。
     */
    @Transactional
    public Payment payVoucher(Long supplierId, BigDecimal amount, String method, List<Long> payableIds) {
        Payment payment = new Payment();
        payment.setPaymentNo(generatePaymentNo());
        payment.setSupplierId(supplierId);
        payment.setAmount(amount);
        payment.setPayDate(LocalDate.now());
        payment.setMethod(method);
        payment.setStatus("PAID");
        paymentMapper.insert(payment);

        if (payableIds != null) {
            for (Long payableId : payableIds) {
                Payable payable = requirePayable(payableId);
                if (!"APPROVED".equals(payable.getStatus()) && !"PAID".equals(payable.getStatus())) {
                    throw new BusinessException(() -> 400, "Payable " + payableId + " is not APPROVED");
                }
                if ("PAID".equals(payable.getStatus())) {
                    continue; // 幂等
                }
                payable.setStatus("PAID");
                payableMapper.updateById(payable);

                PayablePayment pp = new PayablePayment();
                pp.setPayableId(payableId);
                pp.setPaymentId(payment.getId());
                pp.setAmount(payable.getAmount());
                payablePaymentMapper.insert(pp);

                outboxService.saveEvent(KafkaTopics.SETTLEMENT_COMPLETED, "PAYABLE", payableId,
                        "settlement:" + payableId,
                        Map.of("orderId", payable.getOrderId(), "payableId", payableId, "amount", payable.getAmount()));
            }
        }
        return payment;
    }

    private Payable requirePayable(Long id) {
        Payable payable = payableMapper.selectById(id);
        if (payable == null) {
            throw new BusinessException(() -> 404, "Payable not found: " + id);
        }
        return payable;
    }

    private String generatePaymentNo() {
        return "PAY-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public record InvoiceLine(Long orderItemId, Long itemId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {}
}