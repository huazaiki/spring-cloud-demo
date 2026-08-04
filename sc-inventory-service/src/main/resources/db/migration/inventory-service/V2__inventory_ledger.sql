-- V2：库存流水表（一期，docs/design/schema.md）
CREATE TABLE IF NOT EXISTS inventory_ledger (
    id BIGINT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    change_type VARCHAR(16) NOT NULL COMMENT 'RESERVE/RELEASE/RECEIVE/ISSUE/ADJUST',
    ref_type VARCHAR(32) COMMENT '来源单据类型',
    ref_id BIGINT COMMENT '来源单据ID',
    qty_change DECIMAL(18,4) NOT NULL COMMENT '可用库存变动量（带符号）',
    before_qty DECIMAL(18,4) NOT NULL COMMENT '变动前可用库存',
    after_qty DECIMAL(18,4) NOT NULL COMMENT '变动后可用库存',
    biz_no VARCHAR(64) COMMENT '业务单号',
    remark VARCHAR(255),
    create_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_item_time (item_id, create_time),
    KEY idx_ref (ref_type, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水';