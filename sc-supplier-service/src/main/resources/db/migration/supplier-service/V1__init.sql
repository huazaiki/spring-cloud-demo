-- supplier_db V1 基线：现有表结构（与 docker/mysql/init/02-tables.sql 的 supplier_db 部分一致）
CREATE TABLE IF NOT EXISTS supplier (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    credit_code VARCHAR(64) NOT NULL,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_credit_code (credit_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;