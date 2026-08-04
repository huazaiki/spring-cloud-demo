package com.huazaiki.purchase.controller;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.common.security.RequirePermission;
import com.huazaiki.purchase.approval.ApprovalEngine;
import com.huazaiki.purchase.entity.ApprovalRecord;
import com.huazaiki.purchase.entity.ApprovalTask;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/approval-tasks")
public class ApprovalTaskController {

    private final ApprovalEngine approvalEngine;

    public ApprovalTaskController(ApprovalEngine approvalEngine) {
        this.approvalEngine = approvalEngine;
    }

    @RequirePermission("approval:task:view")
    @GetMapping("/mine")
    public ApiResponse<List<ApprovalTask>> mine(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestHeader(value = "X-User-Dept", required = false) String deptId) {
        return ApiResponse.success(approvalEngine.listMyTasks(
                Long.valueOf(userId), split(roles), deptId != null && !deptId.isBlank() ? Long.valueOf(deptId) : null));
    }

    @RequirePermission("approval:task:view")
    @GetMapping
    public ApiResponse<List<ApprovalRecord>> records(
            @RequestParam String bizType, @RequestParam Long bizId) {
        return ApiResponse.success(approvalEngine.listRecords(bizType, bizId));
    }

    @RequirePermission("approval:task:approve")
    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approve(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestHeader(value = "X-User-Dept", required = false) String deptId,
            @RequestBody(required = false) Map<String, String> body) {
        approvalEngine.approve(id, Long.valueOf(userId), split(roles),
                deptId != null && !deptId.isBlank() ? Long.valueOf(deptId) : null,
                body != null ? body.get("opinion") : null);
        return ApiResponse.success();
    }

    @RequirePermission("approval:task:reject")
    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestHeader(value = "X-User-Dept", required = false) String deptId,
            @RequestBody Map<String, String> body) {
        approvalEngine.reject(id, Long.valueOf(userId), split(roles),
                deptId != null && !deptId.isBlank() ? Long.valueOf(deptId) : null,
                body != null ? body.get("opinion") : null);
        return ApiResponse.success();
    }

    @RequirePermission("approval:task:transfer")
    @PostMapping("/{id}/transfer")
    public ApiResponse<Void> transfer(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> body) {
        Long targetUserId = Long.valueOf(body.get("targetUserId").toString());
        approvalEngine.transfer(id, Long.valueOf(userId), targetUserId,
                body.get("opinion") != null ? body.get("opinion").toString() : null);
        return ApiResponse.success();
    }

    private List<String> split(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return Arrays.stream(header.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}