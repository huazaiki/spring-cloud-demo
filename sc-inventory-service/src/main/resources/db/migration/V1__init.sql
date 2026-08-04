-- inventory_db V1 基线：现有表结构（与 docker/mysql/init/02-tables.sql 的 inventory_db 部分一致）
CREATE TABLE IF NOT EXISTS item (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    spec VARCHAR(128),
    unit VARCHAR(32),
    sku VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory (
    id BIGINT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    available_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    reserved_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS receive_record (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    received_at DATETIME NOT NULL,
    KEY idx_order_item (order_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;