-- V4：应付审计/逻辑删除/乐观锁列（docs/design/schema.md §5；实体含 createBy/updateBy，此前表缺列）
ALTER TABLE payable
    ADD COLUMN create_by BIGINT,
    ADD COLUMN update_by BIGINT,
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN version INT NOT NULL DEFAULT 0;