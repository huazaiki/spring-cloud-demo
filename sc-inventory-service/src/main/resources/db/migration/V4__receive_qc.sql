-- V4：收货单 + 质检（docs/design/schema.md §4）
CREATE TABLE IF NOT EXISTS receive (
    id BIGINT PRIMARY KEY,
    receive_no VARCHAR(32) NOT NULL,
    order_id BIGINT NOT NULL,
    supplier_id BIGINT,
    receive_date DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED',
    create_by BIGINT, update_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_receive_no (receive_no),
    KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货单';

CREATE TABLE IF NOT EXISTS receive_item (
    id BIGINT PRIMARY KEY,
    receive_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    order_qty DECIMAL(18,4) NOT NULL COMMENT '订单数量',
    received_qty DECIMAL(18,4) NOT NULL COMMENT '实收数量',
    diff_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '差异（多收/少收）',
    remark VARCHAR(255),
    KEY idx_receive (receive_id),
    KEY idx_order_item (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货明细';

CREATE TABLE IF NOT EXISTS quality_inspection (
    id BIGINT PRIMARY KEY,
    inspect_no VARCHAR(32) NOT NULL,
    receive_item_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    inspect_type VARCHAR(16) NOT NULL COMMENT 'EXEMPT/FULL/SAMPLING',
    inspect_qty DECIMAL(18,4) NOT NULL,
    qualified_qty DECIMAL(18,4) NOT NULL,
    unqualified_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    result VARCHAR(16) NOT NULL COMMENT 'PASS/FAIL/PARTIAL',
    inspector_id BIGINT,
    inspect_time DATETIME NOT NULL,
    create_by BIGINT, update_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_inspect_no (inspect_no),
    KEY idx_receive_item (receive_item_id),
    KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检单';