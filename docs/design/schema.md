# 一期表结构设计（Schema Design）

> 决策 ticket：「设计一期全量表结构」（[#3](https://github.com/huazaiki/spring-cloud-demo/issues/3)）的产出。
> 依据：Q2/Q4/Q5/Q6/Q7 决策、ADR-0001/0002、Spec（issue #1）、`docs/roadmap.md`。
> 迁移执行（Flyway 基线/脚本）见 ticket「设计 Flyway 基线迁移方案」（#10）；本文件描述**目标结构**。

## 0. 设计约定

- **主键**：`BIGINT` 雪花 ID（MyBatis-Plus `ASSIGN_ID`），无自增。
- **审计字段**：`create_by BIGINT` / `update_by BIGINT` / `create_time DATETIME` / `update_time DATETIME`，业务主表全含。
- **逻辑删除**：`deleted TINYINT NOT NULL DEFAULT 0`（0 正常 / 1 已删），业务主表全含。
- **乐观锁**：`version INT NOT NULL DEFAULT 0`，并发敏感表含（库存、订单、请购、审批任务、应付、发票、付款单、收货单、质检）。
- **索引**：业务单据号唯一索引；外键字段（`*_id`）建普通索引；**跨库只存 ID、不建外键**（同库也以索引为主，不强制 FK）。
- **金额/数量**：金额 `DECIMAL(18,2)`，数量 `DECIMAL(18,4)`。
- **状态**：用 `VARCHAR` 枚举字符串，状态机由代码维护，不建 CHECK。
- **跨服务引用**：`supplier_id`、`item_id`、`order_id` 等均为对方服务主键 ID，各服务只存 ID。

## 1. auth_db（身份与权限）

```sql
-- 部门
CREATE TABLE sys_dept (
  id BIGINT PRIMARY KEY,
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父部门ID，0=根',
  dept_code VARCHAR(64) NOT NULL COMMENT '部门编码',
  dept_name VARCHAR(128) NOT NULL COMMENT '部门名称',
  sort_no INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_dept_code (dept_code),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门';

-- 用户（现有 sys_user 扩展：+dept_id/status/审计/deleted/version；原 role 字段废弃由 sys_user_role 表达）
CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  dept_id BIGINT COMMENT '所属部门',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_username (username),
  KEY idx_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 角色
CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY,
  role_code VARCHAR(64) NOT NULL COMMENT '如 PURCHASER/WAREHOUSE/FINANCE/ADMIN/DEPT_MANAGER',
  role_name VARCHAR(128) NOT NULL,
  description VARCHAR(255),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- 用户-角色
CREATE TABLE sys_user_role (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

-- 权限点（菜单并入：perm_type=MENU/BUTTON/API；MENU 带 route_path）
CREATE TABLE sys_permission (
  id BIGINT PRIMARY KEY,
  parent_id BIGINT NOT NULL DEFAULT 0,
  perm_code VARCHAR(128) NOT NULL COMMENT '如 purchase:order:approve',
  perm_name VARCHAR(128) NOT NULL,
  perm_type VARCHAR(16) NOT NULL COMMENT 'MENU/BUTTON/API',
  route_path VARCHAR(255) COMMENT 'MENU 类型的前端路由',
  sort_no INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_perm_code (perm_code),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点（含菜单）';

-- 角色-权限点
CREATE TABLE sys_role_permission (
  id BIGINT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_role_perm (role_id, permission_id),
  KEY idx_perm (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联';
```

## 2. supplier_db（供应商）

```sql
-- 供应商（现有表扩展：+category/银行/资质/审计/deleted/version）
CREATE TABLE supplier (
  id BIGINT PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  credit_code VARCHAR(64) NOT NULL,
  contact_name VARCHAR(64),
  contact_phone VARCHAR(32),
  category VARCHAR(64) COMMENT '供应商分类',
  bank_name VARCHAR(128),
  bank_account_no VARCHAR(64),
  qualification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED（准入）',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE/DISQUALIFIED',
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_credit_code (credit_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商';

-- 供货关系（供应商-物料-约定价，跨库引用 inventory.item_id）
CREATE TABLE supplier_item (
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

-- 多联系人
CREATE TABLE supplier_contact (
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
```

## 3. purchase_db（请购 / 订单 / 审批 / Outbox）

```sql
-- 请购单
CREATE TABLE purchase_requisition (
  id BIGINT PRIMARY KEY,
  pr_no VARCHAR(32) NOT NULL,
  applicant_id BIGINT NOT NULL,
  applicant_dept_id BIGINT NOT NULL,
  supplier_id BIGINT COMMENT '意向供应商（可空）',
  total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  expected_date DATE COMMENT '期望到货日期',
  purpose VARCHAR(255) COMMENT '用途说明',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/APPROVED/REJECTED/CONVERTED/CANCELLED',
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_pr_no (pr_no),
  KEY idx_applicant (applicant_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请购单';

CREATE TABLE purchase_requisition_item (
  id BIGINT PRIMARY KEY,
  pr_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  item_name VARCHAR(128) NOT NULL,
  spec VARCHAR(128),
  unit VARCHAR(32),
  quantity DECIMAL(18,4) NOT NULL,
  expected_date DATE,
  remark VARCHAR(255),
  KEY idx_pr (pr_id),
  KEY idx_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请购明细';

-- 采购订单（现有表扩展：+pr_id/applicant/期望日期/状态机扩展/取消原因/审计/deleted/version）
CREATE TABLE purchase_order (
  id BIGINT PRIMARY KEY,
  order_no VARCHAR(32) NOT NULL,
  pr_id BIGINT COMMENT '来源请购单（可空）',
  supplier_id BIGINT NOT NULL,
  applicant_id BIGINT,
  applicant_dept_id BIGINT,
  total_amount DECIMAL(18,2) NOT NULL,
  expected_date DATE,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVED/SHIPPED/RECEIVED/SETTLED/CANCELLED',
  cancel_reason VARCHAR(255),
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_supplier (supplier_id),
  KEY idx_pr (pr_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单';

-- 订单明细（现有表扩展：+received_qty 累计实收）
CREATE TABLE purchase_order_item (
  id BIGINT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  item_name VARCHAR(128) NOT NULL,
  quantity DECIMAL(18,4) NOT NULL,
  unit_price DECIMAL(18,4) NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  received_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '累计实收',
  KEY idx_order (order_id),
  KEY idx_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单明细';

-- 审批流定义（规则 JSON 数据化；biz_type: PR/PO/PAYMENT）
CREATE TABLE approval_flow (
  id BIGINT PRIMARY KEY,
  flow_code VARCHAR(64) NOT NULL,
  flow_name VARCHAR(128) NOT NULL,
  biz_type VARCHAR(16) NOT NULL COMMENT 'PR/PO/PAYMENT',
  rule_json JSON NOT NULL COMMENT '审批链规则：节点/条件/审批人角色',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_flow_code (flow_code),
  KEY idx_biz_type (biz_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流定义';

-- 审批任务实例
CREATE TABLE approval_task (
  id BIGINT PRIMARY KEY,
  flow_id BIGINT NOT NULL,
  biz_type VARCHAR(16) NOT NULL,
  biz_id BIGINT NOT NULL,
  node_key VARCHAR(64) NOT NULL COMMENT '当前节点标识',
  node_name VARCHAR(128),
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/TRANSFERRED/CANCELLED',
  approver_role VARCHAR(64) COMMENT '当前节点审批人角色',
  approver_id BIGINT COMMENT '实际处理人',
  opinion VARCHAR(255),
  action_time DATETIME,
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  KEY idx_biz (biz_type, biz_id),
  KEY idx_approver (approver_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批任务';

-- 审批记录（历史轨迹，用户故事：查看审批历史）
CREATE TABLE approval_record (
  id BIGINT PRIMARY KEY,
  task_id BIGINT,
  biz_type VARCHAR(16) NOT NULL,
  biz_id BIGINT NOT NULL,
  node_key VARCHAR(64),
  action VARCHAR(16) NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/TRANSFER',
  approver_id BIGINT,
  opinion VARCHAR(255),
  action_time DATETIME NOT NULL,
  KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录';

-- Outbox（采购域事件，每个业务库一张同构表）
CREATE TABLE outbox (
  id BIGINT PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  biz_type VARCHAR(16) NOT NULL,
  biz_id BIGINT NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键：bizType:bizId:eventType',
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
```

## 4. inventory_db（物料 / 库存 / 收货 / 质检 / Outbox）

```sql
-- 物料分类
CREATE TABLE item_category (
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

-- 物料（现有表扩展：+category_id/安全库存/再订购点/状态/审计/deleted）
CREATE TABLE item (
  id BIGINT PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  spec VARCHAR(128),
  unit VARCHAR(32),
  sku VARCHAR(64),
  category_id BIGINT,
  safety_stock DECIMAL(18,4) COMMENT '安全库存',
  reorder_point DECIMAL(18,4) COMMENT '再订购点',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_category (category_id),
  KEY idx_sku (sku)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料';

-- 库存（现有表扩展：+version 乐观锁；语义修正：预留/入库核销闭环）
CREATE TABLE inventory (
  id BIGINT PRIMARY KEY,
  item_id BIGINT NOT NULL,
  available_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '可用库存',
  reserved_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已预留库存',
  version INT NOT NULL DEFAULT 0,
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存';
-- 语义：预留 reserve = available−、reserved+；入库核销 = reserved−、available+；全部变动写 inventory_ledger

-- 库存流水（新增，可追溯/对账核心）
CREATE TABLE inventory_ledger (
  id BIGINT PRIMARY KEY,
  item_id BIGINT NOT NULL,
  change_type VARCHAR(16) NOT NULL COMMENT 'RESERVE/RELEASE/RECEIVE/ISSUE/ADJUST',
  ref_type VARCHAR(32) COMMENT '来源单据类型：ORDER/RECEIVE/QC...',
  ref_id BIGINT COMMENT '来源单据ID',
  qty_change DECIMAL(18,4) NOT NULL COMMENT '变动量（带符号）',
  before_qty DECIMAL(18,4) NOT NULL,
  after_qty DECIMAL(18,4) NOT NULL,
  biz_no VARCHAR(64) COMMENT '业务单号',
  remark VARCHAR(255),
  create_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_item_time (item_id, create_time),
  KEY idx_ref (ref_type, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水';

-- 收货单（重构现有 receive_record → receive + receive_item）
CREATE TABLE receive (
  id BIGINT PRIMARY KEY,
  receive_no VARCHAR(32) NOT NULL,
  order_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  receive_date DATETIME NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED',
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_receive_no (receive_no),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货单';

CREATE TABLE receive_item (
  id BIGINT PRIMARY KEY,
  receive_id BIGINT NOT NULL,
  order_item_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  order_qty DECIMAL(18,4) NOT NULL COMMENT '订单数量',
  received_qty DECIMAL(18,4) NOT NULL COMMENT '实收数量',
  diff_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '差异（多收/少收）',
  remark VARCHAR(255),
  KEY idx_receive (receive_id),
  KEY idx_order_item (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货明细';

-- 质检
CREATE TABLE quality_inspection (
  id BIGINT PRIMARY KEY,
  inspect_no VARCHAR(32) NOT NULL,
  receive_item_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  inspect_type VARCHAR(16) NOT NULL COMMENT 'EXEMPT/FULL/SAMPLING',
  inspect_qty DECIMAL(18,4) NOT NULL,
  qualified_qty DECIMAL(18,4) NOT NULL,
  unqualified_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
  result VARCHAR(16) NOT NULL COMMENT 'PASS/FAIL/PARTIAL',
  inspector_id BIGINT,
  inspect_time DATETIME NOT NULL,
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_inspect_no (inspect_no),
  KEY idx_receive_item (receive_item_id),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检单';

-- Outbox（库存域事件）与 purchase_db.outbox 同构
```

## 5. payment_db（应付 / 发票 / 三单匹配 / 付款 / 核销 / 审批 / Outbox）

```sql
-- 发票
CREATE TABLE invoice (
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

CREATE TABLE invoice_item (
  id BIGINT PRIMARY KEY,
  invoice_id BIGINT NOT NULL,
  order_item_id BIGINT,
  item_id BIGINT NOT NULL,
  quantity DECIMAL(18,4) NOT NULL,
  unit_price DECIMAL(18,4) NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  KEY idx_invoice (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票明细';

-- 三单匹配（订单/收货/发票）
CREATE TABLE invoice_match (
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

-- 应付账款（现有表扩展：+invoice_id/paid_amount/状态机扩展/审计/deleted/version）
CREATE TABLE payable (
  id BIGINT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  invoice_id BIGINT COMMENT '关联发票（可空）',
  amount DECIMAL(18,2) NOT NULL,
  paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  due_date DATE,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/PAID/PARTIAL/CANCELLED',
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  KEY idx_order (order_id),
  KEY idx_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应付账款';

-- 付款单
CREATE TABLE payment (
  id BIGINT PRIMARY KEY,
  payment_no VARCHAR(32) NOT NULL,
  supplier_id BIGINT NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  pay_date DATE,
  method VARCHAR(32) COMMENT '转账/支票/...',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVED/PAID/CANCELLED',
  create_by BIGINT, update_by BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_payment_no (payment_no),
  KEY idx_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款单';

-- 核销（应付-付款关联）
CREATE TABLE payable_payment (
  id BIGINT PRIMARY KEY,
  payable_id BIGINT NOT NULL,
  payment_id BIGINT NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_payable_payment (payable_id, payment_id),
  KEY idx_payment (payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应付核销关联';

-- 审批流/任务/记录（付款审批，biz_type=PAYMENT）与 purchase_db 同构
-- Outbox（付款域事件）与 purchase_db.outbox 同构
```

## 6. 关键设计决策（要点）

1. **菜单并入权限点**：`sys_permission.perm_type` 区分 MENU/BUTTON/API，前端菜单由权限点派生，避免菜单与权限两套数据。
2. **审批引擎表按服务库部署**：`approval_flow / approval_task / approval_record` 在 purchase_db（PR/PO）与 payment_db（PAYMENT）各一份，`ApprovalEngine` 作为 sc-common 共享组件；服务间不跨服务调审批，保持数据自治。
3. **库存闭环语义**：`inventory.available_qty / reserved_qty` 为唯一账本（乐观锁 `version` 防并发）；预留（available→reserved）与入库核销（reserved→available，关联订单/收货）成对出现，每笔变动写 `inventory_ledger`，杜绝现网"reserved 只增不减"导致的虚增。
4. **Outbox 每服务一张**：各业务库（purchase/inventory/payment）均建 `outbox` 表，`idempotency_key` 唯一（`bizType:bizId:eventType`），消费方按此幂等。
5. **跨服务引用仅存 ID**：`supplier_id / item_id / order_id / receive_id` 等不建跨库外键；报表/聚合由查询侧按需 join（二期报表服务）。
6. **状态机用 VARCHAR 枚举**：由服务代码维护合法流转，符合"可配置状态机"方向（ADR-0001 的轻量路线）。

## 7. 迁移衔接（与「设计 Flyway 基线迁移方案」#10 对接）

- 现有 `docker/mysql/init/02-tables.sql` 各表为目标结构的 **V1 基线来源**；本文件为 **V2+ 目标**：
  - `sys_user`：+dept_id/status/审计/deleted/version；`role` 字段废弃 → 迁至 `sys_user_role`（种子数据：把现有 role 值转为角色并关联）。
  - `receive_record` → 由 `receive + receive_item` 取代。
  - 其余表：加审计/deleted/version/索引字段。
- 种子数据：角色（PURCHASER/WAREHOUSE/FINANCE/ADMIN/DEPT_MANAGER）、默认审批链（PR/PO/PAYMENT）、权限点骨架。