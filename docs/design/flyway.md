# Flyway 基线迁移方案

> 决策 ticket：「设计 Flyway 基线迁移方案」（[#10](https://github.com/huazaiki/spring-cloud-demo/issues/10)）的产出。
> 依据：Q7 决策、表结构目标态（`docs/design/schema.md`）、现有 `docker/mysql/init/*.sql`。

## 1. 总体策略

- **每个服务独立数据库，Flyway 随各自数据源迁移**（服务启动时 `spring.flyway.enabled=true` 自动执行，无需额外步骤；CI 里由 `mvn verify` 前先跑一次保证 schema 就绪）。
- **V1 = 现状基线**：把 `docker/mysql/init/02-tables.sql` 按库拆分为各服务的 `V1__init.sql`（保持现有结构，数据无损）。
- **V2+ = 一期目标**：以 `docs/design/schema.md` 为目标的增量迁移（新增表 + 现有表加列/索引），旧数据平滑保留。
- **变更纪律**：只增表/加列/加索引，**不修改既有列类型、不删列**（MySQL DDL 回滚有限）；需要"改"的先加新列并双写，二期再清理。

## 2. 目录与命名

```
sc-auth-service/src/main/resources/db/migration/
  V1__init.sql                      # auth_db 现状（sys_user）
  V2__rbac.sql                      # sys_dept/sys_role/sys_permission/sys_user_role/sys_role_permission + sys_user 加列
  V3__seed_permissions_roles.sql    # 权限点/角色/菜单种子（见 permissions.md）
sc-supplier-service/src/main/resources/db/migration/
  V1__init.sql                      # supplier 现状
  V2__supplier_phase1.sql           # supplier 加列 + supplier_item/supplier_contact
sc-purchase-service/src/main/resources/db/migration/
  V1__init.sql                      # purchase_order/purchase_order_item 现状
  V2__purchase_phase1.sql           # purchase_requisition(+item)/审批三表/outbox + order 加列
  V3__seed_approval_flows.sql       # PR_DEFAULT/PO_DEFAULT 默认审批链
sc-inventory-service/src/main/resources/db/migration/
  V1__init.sql                      # item/inventory/receive_record 现状
  V2__inventory_phase1.sql          # item_category/item 加列/inventory 加 version/inventory_ledger/receive+receive_item/quality_inspection/outbox
  V3__migrate_receive_record.sql    # receive_record → receive/receive_item 数据搬运
sc-payment-service/src/main/resources/db/migration/
  V1__init.sql                      # payable 现状
  V2__payment_phase1.sql            # invoice(+item)/invoice_match/payment/payable_payment + payable 加列 + 审批表 + outbox
  V3__seed_approval_flows.sql       # PAYMENT_DEFAULT 默认审批链
```

命名：`V{n}__{snake_case_description}.sql`，版本号全局递增、不重复。

## 3. docker init 脚本调整

- `docker/mysql/init/01-databases.sql`：**保留**（只建 5 个库）。
- `02-tables.sql`：**删除建表逻辑**（表全部由各服务 Flyway 建）；可保留为空/移除。
- `03-seata-undo-log.sql`：**保留**（Seata AT 各参与库需要 undo_log；由 MySQL init 或 Flyway 均可，建议并入各服务 V1 或独立执行）。
- 首启顺序：compose 起 MySQL（仅建库）→ 服务启动时 Flyway 自动建表 + 种子。

## 4. 种子数据

- 权限/角色/菜单种子：`V3__seed_permissions_roles.sql`（auth_db，内容见 `docs/design/permissions.md` §5）。
- 默认审批链种子：purchase_db / payment_db 的 `V3__seed_approval_flows.sql`（内容见 `docs/design/approval-engine.md` §5）。
- 种子用幂等 INSERT（`INSERT ... ON DUPLICATE KEY UPDATE` 或固定 ID），保证可重复执行语义。

## 5. 执行与验证

- **本地**：`docker compose up -d mysql` → 启动各服务自动迁移；或 `mvn -pl sc-purchase-service flyway:migrate`（插件）。
- **CI**：集成测试（Testcontainers）启动时各服务自动迁移到最新版；`mvn verify` 全链路覆盖。
- **校验**：`flyway validate` 检查脚本 checksum，防止已发布脚本被篡改；CI 中 `mvn flyway:validate` 作为质量门禁。
- **生产**：发布流程先跑迁移（只进不退）；回滚采用"新版本向前兼容"策略，不做 DDL 回滚。

## 6. 与既有部署的关系

- 已有本地数据卷：首次接入 Flyway 需 `V1` 与现有表结构一致（checksum 以 V1 为准）；若数据卷已存在旧表，直接叠加 V2+ 即可（V1 已在历史中视为已执行——对全新环境由 V1 建表）。
- 说明：全新环境（无数据卷）自动从 V1 顺序执行；存量环境（已有旧表）执行 V2+。两态兼容。