-- V2：供应商一期扩展（docs/design/schema.md §2）
ALTER TABLE supplier
    ADD COLUMN category VARCHAR(64) COMMENT '供应商分类',
    ADD COLUMN bank_name VARCHAR(128),
    ADD COLUMN bank_account_no VARCHAR(64),
    ADD COLUMN qualification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED（准入）',
    ADD COLUMN create_by BIGINT,
    ADD COLUMN update_by BIGINT,
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN version INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS supplier_item (
    id BIGINT PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL COMMENT '引用 inventory 物料ID',
    unit_price DECIMAL(18,4) COMMENT '约定采购价',
    lead_time_days INT COMMENT '交期（天）',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    create_by BIGINT, update_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_supplier_item (supplier_id, item_id),
    KEY idx_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商供货关系';

CREATE TABLE IF NOT EXISTS supplier_contact (
    id BIGINT PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    contact_name VARCHAR(64) NOT NULL,
    contact_phone VARCHAR(32),
    email VARCHAR(128),
    is_primary TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT, update_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商联系人';