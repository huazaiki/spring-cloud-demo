-- V3：请购单 + 审批链（docs/design/schema.md §3、approval-engine.md）
CREATE TABLE IF NOT EXISTS purchase_requisition (
    id BIGINT PRIMARY KEY,
    pr_no VARCHAR(32) NOT NULL,
    applicant_id BIGINT NOT NULL,
    applicant_dept_id BIGINT NOT NULL,
    supplier_id BIGINT COMMENT '意向供应商（可空）',
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    expected_date DATE,
    purpose VARCHAR(255),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/APPROVED/REJECTED/CONVERTED/CANCELLED',
    create_by BIGINT, update_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_pr_no (pr_no),
    KEY idx_applicant (applicant_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请购单';

CREATE TABLE IF NOT EXISTS purchase_requisition_item (
    id BIGINT PRIMARY KEY,
    pr_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    spec VARCHAR(128),
    unit VARCHAR(32),
    quantity DECIMAL(18,4) NOT NULL,
    expected_date DATE,
    remark VARCHAR(255),
    KEY idx_pr (pr_id),
    KEY idx_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请购明细';

CREATE TABLE IF NOT EXISTS approval_flow (
    id BIGINT PRIMARY KEY,
    flow_code VARCHAR(64) NOT NULL,
    flow_name VARCHAR(128) NOT NULL,
    biz_type VARCHAR(16) NOT NULL COMMENT 'PR/PO/PAYMENT',
    rule_json JSON NOT NULL COMMENT '审批链规则：节点/条件/审批人角色',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    create_by BIGINT, update_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_flow_code (flow_code),
    KEY idx_biz_type (biz_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流定义';

CREATE TABLE IF NOT EXISTS approval_task (
    id BIGINT PRIMARY KEY,
    flow_id BIGINT NOT NULL,
    biz_type VARCHAR(16) NOT NULL,
    biz_id BIGINT NOT NULL,
    node_key VARCHAR(64) NOT NULL,
    node_name VARCHAR(128),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/TRANSFERRED/CANCELLED',
    approver_role VARCHAR(64) COMMENT '当前节点审批人角色',
    approver_id BIGINT COMMENT '实际处理人（转交后）',
    scope_dept_id BIGINT COMMENT 'APPLICANT_DEPT 范围时=申请人部门ID；ANY 为 NULL',
    snapshot_json JSON COMMENT '提交时的单据快照（条件求值用）',
    opinion VARCHAR(255),
    action_time DATETIME,
    create_by BIGINT, update_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    KEY idx_biz (biz_type, biz_id),
    KEY idx_approver (approver_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批任务';

CREATE TABLE IF NOT EXISTS approval_record (
    id BIGINT PRIMARY KEY,
    task_id BIGINT,
    biz_type VARCHAR(16) NOT NULL,
    biz_id BIGINT NOT NULL,
    node_key VARCHAR(64),
    action VARCHAR(16) NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/TRANSFER/CANCEL',
    approver_id BIGINT,
    opinion VARCHAR(255),
    action_time DATETIME NOT NULL,
    KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录';