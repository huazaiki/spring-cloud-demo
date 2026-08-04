package com.huazaiki.purchase.approval;

import com.huazaiki.purchase.entity.ApprovalRecord;
import com.huazaiki.purchase.entity.ApprovalTask;

import java.util.List;
import java.util.Map;

/**
 * 审批引擎接口（docs/design/approval-engine.md §3）。
 */
public interface ApprovalEngine {

    /** 注册业务回调（如 RequisitionService 为 PR 注册自身） */
    void registerCallback(String bizType, BizApprovalCallback callback);

    /** 提交：为单据创建审批任务链，返回首个任务 */
    ApprovalContext submit(String bizType, Long bizId, Map<String, Object> snapshot, BizApprovalCallback callback);

    /** 通过当前节点：推进下一节点或完成（回调 onApproved） */
    void approve(Long taskId, Long operatorId, List<String> operatorRoles, Long operatorDeptId, String opinion);

    /** 驳回：按 rejectStrategy 回调（回草稿 / 终态） */
    void reject(Long taskId, Long operatorId, List<String> operatorRoles, Long operatorDeptId, String opinion);

    /** 转交：把当前任务转给指定用户 */
    void transfer(Long taskId, Long operatorId, Long targetUserId, String opinion);

    /** 取消：终止单据所有未完成任务 */
    void cancel(String bizType, Long bizId, Long operatorId);

    /** 我的待办（按审批人身份 / 角色 + 部门范围解析） */
    List<ApprovalTask> listMyTasks(Long userId, List<String> roles, Long deptId);

    /** 单据审批轨迹 */
    List<ApprovalRecord> listRecords(String bizType, Long bizId);

    record ApprovalContext(Long flowId, Long firstTaskId) {}
}