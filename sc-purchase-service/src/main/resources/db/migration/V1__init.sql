-- purchase_db V1 基线：现有表结构（与 docker/mysql/init/02-tables.sql 的 purchase_db 部分一致）
CREATE TABLE IF NOT EXISTS purchase_order (
    id BIGINT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    supplier_id BIGINT NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS purchase_order_item (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit_price DECIMAL(18,4) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    KEY idx_order (order_id),
    KEY idx_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;