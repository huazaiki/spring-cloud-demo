package com.huazaiki.purchase.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 审批链规则（approval_flow.rule_json 反序列化模型，docs/design/approval-engine.md §2）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalRule {

    private String flowCode;
    private String bizType;
    private List<Node> nodes;
    private String rejectStrategy;   // BACK_TO_SUBMITTER / TERMINATE
    private Boolean transferAllowed;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Node {
        private String key;
        private String name;
        private Approver approver;
        private Enabled enabled;     // null = 恒启用
        private Boolean optional;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Approver getApprover() { return approver; }
        public void setApprover(Approver approver) { this.approver = approver; }
        public Enabled getEnabled() { return enabled; }
        public void setEnabled(Enabled enabled) { this.enabled = enabled; }
        public Boolean getOptional() { return optional; }
        public void setOptional(Boolean optional) { this.optional = optional; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Approver {
        private String type;   // ROLE / USER
        private String value;
        private String scope;  // APPLICANT_DEPT / ANY

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Enabled {
        private String field;
        private String op;   // gte/lte/gt/lt/eq
        private Number value;

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getOp() { return op; }
        public void setOp(String op) { this.op = op; }
        public Number getValue() { return value; }
        public void setValue(Number value) { this.value = value; }
    }

    public String getFlowCode() { return flowCode; }
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public List<Node> getNodes() { return nodes; }
    public void setNodes(List<Node> nodes) { this.nodes = nodes; }
    public String getRejectStrategy() { return rejectStrategy; }
    public void setRejectStrategy(String rejectStrategy) { this.rejectStrategy = rejectStrategy; }
    public Boolean getTransferAllowed() { return transferAllowed; }
    public void setTransferAllowed(Boolean transferAllowed) { this.transferAllowed = transferAllowed; }
}