-- V2：Outbox 事件表（docs/design/schema.md §3）
CREATE TABLE IF NOT EXISTS outbox (
    id BIGINT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL COMMENT '事件/topic 名',
    biz_type VARCHAR(16) NOT NULL,
    biz_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PUBLISHED/FAILED',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME,
    published_at DATETIME,
    last_error VARCHAR(512),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_event_id (event_id),
    UNIQUE KEY uk_idempotency (idempotency_key),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbox 事件表';