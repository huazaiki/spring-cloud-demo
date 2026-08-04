-- payment_db V1 基线：现有表结构（与 docker/mysql/init/02-tables.sql 的 payment_db 部分一致）
CREATE TABLE IF NOT EXISTS payable (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    due_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_order (order_id),
    KEY idx_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;