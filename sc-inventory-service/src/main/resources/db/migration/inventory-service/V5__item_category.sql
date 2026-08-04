-- V5：物料分类 + item 扩展列（docs/design/schema.md §4；此前 item 表缺这些列导致实体查询报错）
CREATE TABLE IF NOT EXISTS item_category (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    create_by BIGINT, update_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料分类';

ALTER TABLE item
    ADD COLUMN category_id BIGINT COMMENT '物料分类ID',
    ADD COLUMN safety_stock DECIMAL(18,4) COMMENT '安全库存',
    ADD COLUMN reorder_point DECIMAL(18,4) COMMENT '再订购点';