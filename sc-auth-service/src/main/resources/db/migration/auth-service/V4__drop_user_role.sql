-- V4：废弃 sys_user.role 列，统一到 sys_user_role 多对多
ALTER TABLE sys_user DROP COLUMN IF EXISTS role;
