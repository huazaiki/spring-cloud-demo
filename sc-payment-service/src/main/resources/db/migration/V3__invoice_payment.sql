-- V3：发票/三单匹配/付款单/核销（docs/design/schema.md §5）
CREATE TABLE IF NOT EXISTS invoice (
    id BIGINT PRIMARY KEY,
    invoice_no VARCHAR(64) NOT NULL,
    supplier_id BIGINT NOT NULL,
    order_id BIGINT COMMENT '关联订单（可空）',
    invoice_date DATE NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED/MATCHED/CANCELLED',
    create_by BIGINT, update_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_invoice_no (invoice_no),
    KEY idx_supplier (supplier_id),
    KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票';

CREATE TABLE IF NOT EXISTS invoice_item (
    id BIGINT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    order_item_id BIGINT,
    item_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit_price DECIMAL(18,4) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    KEY idx_invoice (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票明细';

CREATE TABLE IF NOT EXISTS invoice_match (
    id BIGINT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    receive_id BIGINT NOT NULL,
    quantity_diff DECIMAL(18,4) NOT NULL DEFAULT 0,
    amount_diff DECIMAL(18,2) NOT NULL DEFAULT 0,
    match_status VARCHAR(16) NOT NULL COMMENT 'MATCHED/MISMATCH',
    remark VARCHAR(255),
    create_by BIGINT, update_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_invoice_receive (invoice_id, receive_id),
    KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='三单匹配';

CREATE TABLE IF NOT EXISTS payment (
    id BIGINT PRIMARY KEY,
    payment_no VARCHAR(32) NOT NULL,
    supplier_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    pay_date DATE,
    method VARCHAR(32),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PAID/CANCELLED',
    create_by BIGINT, update_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款单';

CREATE TABLE IF NOT EXISTS payable_payment (
    id BIGINT PRIMARY KEY,
    payable_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payable_payment (payable_id, payment_id),
    KEY idx_payment (payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应付核销关联';