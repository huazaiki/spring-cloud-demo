package com.huazaiki.payment.controller;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.security.RequirePermission;
import com.huazaiki.payment.entity.Payable;
import com.huazaiki.payment.mapper.PayableMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应付账龄报表（docs/design/api-contract.md §2.9）。
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequirePermission("report:payable-aging")
public class PayableAgingReportController {

    private final PayableMapper payableMapper;

    public PayableAgingReportController(PayableMapper payableMapper) {
        this.payableMapper = payableMapper;
    }

    @GetMapping("/payable-aging")
    public ApiResponse<Map<String, Object>> aging() {
        List<Payable> payables = payableMapper.selectList(null);
        LocalDate today = LocalDate.now();
        Map<String, BigDecimal> amountByBucket = new LinkedHashMap<>();
        Map<String, Long> countByBucket = new LinkedHashMap<>();
        String[] bucketNames = {"未到期", "逾期0-30天", "逾期31-60天", "逾期61-90天", "逾期90天以上"};

        for (String b : bucketNames) {
            amountByBucket.put(b, BigDecimal.ZERO);
            countByBucket.put(b, 0L);
        }
        long overdueCount = 0;
        BigDecimal totalPayable = BigDecimal.ZERO;
        for (Payable p : payables) {
            if (p.getAmount() != null) {
                totalPayable = totalPayable.add(p.getAmount());
            }
            if ("PAID".equals(p.getStatus())) {
                continue;
            }
            if (p.getDueDate() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(p.getDueDate(), today);
            String bucket;
            if (days <= 0) {
                bucket = "未到期";
            } else if (days <= 30) {
                bucket = "逾期0-30天";
                overdueCount++;
            } else if (days <= 60) {
                bucket = "逾期31-60天";
                overdueCount++;
            } else if (days <= 90) {
                bucket = "逾期61-90天";
                overdueCount++;
            } else {
                bucket = "逾期90天以上";
                overdueCount++;
            }
            amountByBucket.put(bucket, amountByBucket.get(bucket).add(p.getAmount()));
            countByBucket.put(bucket, countByBucket.get(bucket) + 1);
        }

        List<Map<String, Object>> buckets = new ArrayList<>();
        amountByBucket.forEach((name, amount) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bucket", name);
            row.put("amount", amount);
            row.put("count", countByBucket.get(name));
            buckets.add(row);
        });

        return ApiResponse.success(Map.of(
                "totalPayable", totalPayable,
                "overdueCount", overdueCount,
                "buckets", buckets));
    }
}