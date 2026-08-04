package com.huazaiki.purchase.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.purchase.entity.ApprovalFlow;
import com.huazaiki.purchase.entity.ApprovalRecord;
import com.huazaiki.purchase.entity.ApprovalTask;
import com.huazaiki.purchase.mapper.ApprovalFlowMapper;
import com.huazaiki.purchase.mapper.ApprovalRecordMapper;
import com.huazaiki.purchase.mapper.ApprovalTaskMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审批引擎实现：线性多级审批链，条件跳过节点，角色+部门范围鉴权，快照求值。
 * 业务回调按 bizType 注册（如 RequisitionService 注册 PR）。
 */
@Component
public class ApprovalEngineImpl implements ApprovalEngine {

    private static final String BACK_TO_SUBMITTER = "BACK_TO_SUBMITTER";

    private final ApprovalFlowMapper flowMapper;
    private final ApprovalTaskMapper taskMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, BizApprovalCallback> callbacks = new ConcurrentHashMap<>();

    public ApprovalEngineImpl(ApprovalFlowMapper flowMapper,
                              ApprovalTaskMapper taskMapper,
                              ApprovalRecordMapper recordMapper,
                              ObjectMapper objectMapper) {
        this.flowMapper = flowMapper;
        this.taskMapper = taskMapper;
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
    }

    public void registerCallback(String bizType, BizApprovalCallback callback) {
        callbacks.put(bizType, callback);
    }

    @Override
    @Transactional
    public ApprovalContext submit(String bizType, Long bizId, Map<String, Object> snapshot, BizApprovalCallback callback) {
        ApprovalFlow flow = flowMapper.selectOne(new LambdaQueryWrapper<ApprovalFlow>()
                .eq(ApprovalFlow::getBizType, bizType)
                .eq(ApprovalFlow::getStatus, "ACTIVE")
                .orderByAsc(ApprovalFlow::getId)
                .last("LIMIT 1"));
        if (flow == null) {
            throw new BusinessException(() -> 400, "No active approval flow for bizType: " + bizType);
        }
        ApprovalRule rule = parse(flow.getRuleJson());
        ApprovalRule.Node first = nextEnabled(rule, snapshot, -1);
        if (first == null) {
            if (callback != null) {
                callback.onApproved(bizType, bizId);
            }
            return new ApprovalContext(flow.getId(), null);
        }
        ApprovalTask task = createTask(flow, bizId, snapshot, first);
        record(bizType, bizId, task.getId(), first.getKey(), "SUBMIT", null, null);
        if (callback != null) {
            callback.onSubmitted(bizType, bizId);
        }
        return new ApprovalContext(flow.getId(), task.getId());
    }

    @Override
    @Transactional
    public void approve(Long taskId, Long operatorId, List<String> roles, Long deptId, String opinion) {
        ApprovalTask task = requirePending(taskId);
        checkCanOperate(task, operatorId, roles, deptId);
        ApprovalRule rule = parseFlowRule(task.getFlowId());
        int currentIdx = indexOf(rule, task.getNodeKey());
        Map<String, Object> snapshot = parseSnapshot(task.getSnapshotJson());

        task.setStatus("APPROVED");
        task.setApproverId(operatorId);
        task.setOpinion(opinion);
        task.setActionTime(LocalDateTime.now());
        taskMapper.updateById(task);
        record(task.getBizType(), task.getBizId(), task.getId(), task.getNodeKey(), "APPROVE", operatorId, opinion);

        ApprovalRule.Node next = nextEnabled(rule, snapshot, currentIdx);
        if (next == null) {
            BizApprovalCallback cb = callbacks.get(task.getBizType());
            if (cb != null) {
                cb.onApproved(task.getBizType(), task.getBizId());
            }
        } else {
            createTask(flowMapper.selectById(task.getFlowId()), task.getBizId(), snapshot, next);
        }
    }

    @Override
    @Transactional
    public void reject(Long taskId, Long operatorId, List<String> roles, Long deptId, String opinion) {
        ApprovalTask task = requirePending(taskId);
        checkCanOperate(task, operatorId, roles, deptId);
        ApprovalRule rule = parseFlowRule(task.getFlowId());

        task.setStatus("REJECTED");
        task.setApproverId(operatorId);
        task.setOpinion(opinion);
        task.setActionTime(LocalDateTime.now());
        taskMapper.updateById(task);
        record(task.getBizType(), task.getBizId(), task.getId(), task.getNodeKey(), "REJECT", operatorId, opinion);

        cancelPendingTasks(task.getBizType(), task.getBizId(), task.getId(), operatorId);

        BizApprovalCallback cb = callbacks.get(task.getBizType());
        if (cb != null) {
            if (BACK_TO_SUBMITTER.equals(rule.getRejectStrategy())) {
                cb.onRejectedBack(task.getBizType(), task.getBizId());
            } else {
                cb.onRejectedTerminal(task.getBizType(), task.getBizId());
            }
        }
    }

    @Override
    @Transactional
    public void transfer(Long taskId, Long operatorId, Long targetUserId, String opinion) {
        ApprovalTask task = requirePending(taskId);
        checkCanOperate(task, operatorId, List.of(task.getApproverRole() == null ? "" : task.getApproverRole()), task.getScopeDeptId());
        task.setApproverId(targetUserId);
        task.setOpinion(opinion);
        taskMapper.updateById(task);
        record(task.getBizType(), task.getBizId(), task.getId(), task.getNodeKey(), "TRANSFER", operatorId, opinion);
    }

    @Override
    @Transactional
    public void cancel(String bizType, Long bizId, Long operatorId) {
        cancelPendingTasks(bizType, bizId, null, operatorId);
        BizApprovalCallback cb = callbacks.get(bizType);
        if (cb != null) {
            cb.onCancelled(bizType, bizId);
        }
    }

    @Override
    public List<ApprovalTask> listMyTasks(Long userId, List<String> roles, Long deptId) {
        LambdaQueryWrapper<ApprovalTask> wrapper = new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getStatus, "PENDING")
                .orderByAsc(ApprovalTask::getCreateTime);
        wrapper.and(x -> {
            x.eq(ApprovalTask::getApproverId, userId);
            if (roles != null && !roles.isEmpty()) {
                x.or(o -> o.isNull(ApprovalTask::getApproverId)
                        .in(ApprovalTask::getApproverRole, roles)
                        .and(a -> a.isNull(ApprovalTask::getScopeDeptId).or().eq(ApprovalTask::getScopeDeptId, deptId)));
            }
        });
        return taskMapper.selectList(wrapper);
    }

    @Override
    public List<ApprovalRecord> listRecords(String bizType, Long bizId) {
        return recordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getBizType, bizType)
                .eq(ApprovalRecord::getBizId, bizId)
                .orderByAsc(ApprovalRecord::getActionTime));
    }

    // ---------- helpers ----------

    private ApprovalTask requirePending(Long taskId) {
        ApprovalTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(() -> 404, "Approval task not found: " + taskId);
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw new BusinessException(() -> 409, "Task not PENDING: " + taskId);
        }
        return task;
    }

    private void checkCanOperate(ApprovalTask task, Long operatorId, List<String> roles, Long deptId) {
        if (task.getApproverId() != null) {
            if (!task.getApproverId().equals(operatorId)) {
                throw new BusinessException(() -> 403, "无审批权限（任务已转交他人）");
            }
            return;
        }
        if (roles == null || !roles.contains(task.getApproverRole())) {
            throw new BusinessException(() -> 403, "无审批权限（角色不符）");
        }
        if (task.getScopeDeptId() != null && !task.getScopeDeptId().equals(deptId)) {
            throw new BusinessException(() -> 403, "无审批权限（部门不符）");
        }
    }

    private ApprovalTask createTask(ApprovalFlow flow, Long bizId, Map<String, Object> snapshot, ApprovalRule.Node node) {
        ApprovalTask task = new ApprovalTask();
        task.setFlowId(flow.getId());
        task.setBizType(flow.getBizType());
        task.setBizId(bizId);
        task.setNodeKey(node.getKey());
        task.setNodeName(node.getName());
        task.setStatus("PENDING");
        ApprovalRule.Approver ap = node.getApprover();
        task.setApproverRole(ap != null ? ap.getValue() : null);
        if (ap != null && "APPLICANT_DEPT".equals(ap.getScope())) {
            Object dept = snapshot.get("applicantDeptId");
            task.setScopeDeptId(dept != null ? Long.valueOf(dept.toString()) : null);
        }
        try {
            task.setSnapshotJson(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            throw new BusinessException(() -> 500, "Failed to serialize approval snapshot");
        }
        taskMapper.insert(task);
        return task;
    }

    private void cancelPendingTasks(String bizType, Long bizId, Long excludeTaskId, Long operatorId) {
        List<ApprovalTask> pending = taskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getBizType, bizType)
                .eq(ApprovalTask::getBizId, bizId)
                .eq(ApprovalTask::getStatus, "PENDING")
                .ne(excludeTaskId != null, ApprovalTask::getId, excludeTaskId));
        for (ApprovalTask t : pending) {
            t.setStatus("CANCELLED");
            t.setActionTime(LocalDateTime.now());
            taskMapper.updateById(t);
            record(bizType, bizId, t.getId(), t.getNodeKey(), "CANCEL", operatorId, null);
        }
    }

    private void record(String bizType, Long bizId, Long taskId, String nodeKey, String action, Long operatorId, String opinion) {
        ApprovalRecord record = new ApprovalRecord();
        record.setTaskId(taskId);
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setNodeKey(nodeKey);
        record.setAction(action);
        record.setApproverId(operatorId);
        record.setOpinion(opinion);
        record.setActionTime(LocalDateTime.now());
        recordMapper.insert(record);
    }

    private ApprovalRule parse(String ruleJson) {
        try {
            return objectMapper.readValue(ruleJson, ApprovalRule.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(() -> 500, "Invalid approval rule json");
        }
    }

    private ApprovalRule parseFlowRule(Long flowId) {
        ApprovalFlow flow = flowMapper.selectById(flowId);
        if (flow == null) {
            throw new BusinessException(() -> 404, "Approval flow not found: " + flowId);
        }
        return parse(flow.getRuleJson());
    }

    private Map<String, Object> parseSnapshot(String snapshotJson) {
        if (snapshotJson == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(snapshotJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private int indexOf(ApprovalRule rule, String nodeKey) {
        if (rule.getNodes() == null) {
            return -1;
        }
        for (int i = 0; i < rule.getNodes().size(); i++) {
            if (rule.getNodes().get(i).getKey().equals(nodeKey)) {
                return i;
            }
        }
        return -1;
    }

    private ApprovalRule.Node nextEnabled(ApprovalRule rule, Map<String, Object> snapshot, int afterIndex) {
        if (rule.getNodes() == null) {
            return null;
        }
        for (int i = afterIndex + 1; i < rule.getNodes().size(); i++) {
            ApprovalRule.Node node = rule.getNodes().get(i);
            if (isEnabled(node, snapshot)) {
                return node;
            }
        }
        return null;
    }

    private boolean isEnabled(ApprovalRule.Node node, Map<String, Object> snapshot) {
        ApprovalRule.Enabled e = node.getEnabled();
        if (e == null || e.getOp() == null || e.getField() == null) {
            return true;
        }
        Object val = snapshot.get(e.getField());
        if (val == null) {
            return false;
        }
        BigDecimal actual = new BigDecimal(val.toString());
        BigDecimal expected = new BigDecimal(e.getValue().toString());
        return switch (e.getOp()) {
            case "gte" -> actual.compareTo(expected) >= 0;
            case "lte" -> actual.compareTo(expected) <= 0;
            case "gt" -> actual.compareTo(expected) > 0;
            case "lt" -> actual.compareTo(expected) < 0;
            case "eq" -> actual.compareTo(expected) == 0;
            default -> true;
        };
    }
}