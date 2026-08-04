package com.huazaiki.purchase.approval;

/**
 * 审批引擎业务回调：单据状态机由业务方实现，引擎只在关键节点回调。
 */
public interface BizApprovalCallback {

    void onSubmitted(String bizType, Long bizId);

    /** 全部节点审批通过 */
    void onApproved(String bizType, Long bizId);

    /** 驳回且策略为 BACK_TO_SUBMITTER：单据回草稿可改再提交 */
    void onRejectedBack(String bizType, Long bizId);

    /** 驳回且策略为 TERMINATE：单据终态驳回 */
    void onRejectedTerminal(String bizType, Long bizId);

    /** 取消 */
    void onCancelled(String bizType, Long bizId);
}