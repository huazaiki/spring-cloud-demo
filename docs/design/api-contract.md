# 一期新增 API 契约设计

> 决策 ticket：「设计一期新增 API 契约」（[#9](https://github.com/huazaiki/spring-cloud-demo/issues/9)）的产出。
> 依据：Q2 范围、表结构（`docs/design/schema.md`）、审批链（`docs/design/approval-engine.md`）、权限（`docs/design/permissions.md`）。
> `openapi.yml` 将按本契约更新；实现期用 springdoc 生成兜底，以本契约为准。

## 1. 统一约定

- **响应包装**：一律 `ApiResponse { code, message, data }`；`code=200` 成功，其余为错误码。
- **认证**：`Authorization: Bearer <token>`；网关校验并透传身份头 `X-User-Id` / `X-User-Dept` / `X-User-Roles`。
- **鉴权**：服务内按权限点执行（见 `docs/design/permissions.md`），无权限返回 `403`。
- **数据权限**：按部门过滤由服务端执行，返回 `403`（越权）或自动过滤（查询类）。
- **分页**：`page`（1 起）、`size`（默认 20、上限 100）；响应 `{ records, total, page, size }`。
- **幂等**：写操作支持 `Idempotency-Key` 头；服务端按 key 去重，重复请求返回首次结果（409 转 200 语义由实现定）。
- **错误码**：400 参数错误 / 401 未认证 / 403 无权限 / 404 不存在 / 409 状态冲突 / 422 校验失败 / 500 系统错误；业务明细在 `message`。
- **数量金额**：数量 `DECIMAL` 字符串传输（避免精度丢失），金额 `DECIMAL(18,2)`。

## 2. 端点清单

### 2.1 身份与权限（sc-auth-service）

| 方法 | 路径 | 说明 | 权限点 |
|---|---|---|---|
| POST | /api/v1/auth/register | 注册（一期保留；角色由管理员分配，注册默认无角色） | 公开 |
| POST | /api/v1/auth/login | 登录发 token | 公开 |
| GET | /api/v1/auth/me | 当前用户：信息/部门/角色/权限点/菜单 | 登录 |
| GET/POST | /api/v1/users | 用户分页 / 新建 | user:list / user:create |
| PUT | /api/v1/users/{id} | 编辑（部门/状态/重置密码） | user:update |
| GET/POST | /api/v1/depts | 部门树 / 新建 | dept:manage |
| PUT/DELETE | /api/v1/depts/{id} | 部门编辑/删除 | dept:manage |
| GET/POST | /api/v1/roles | 角色列表 / 新建 | role:manage |
| PUT | /api/v1/roles/{id}/permissions | 角色-权限点分配 | role:manage |
| GET | /api/v1/permissions | 权限点/菜单树 | permission:view |

### 2.2 供应商（sc-supplier-service）

| 方法 | 路径 | 说明 | 权限点 |
|---|---|---|---|
| GET | /api/v1/suppliers | 分页（现有） | supplier:view |
| POST | /api/v1/suppliers | 新建（现有） | supplier:create |
| GET | /api/v1/suppliers/{id} | 详情（现有） | supplier:view |
| PUT | /api/v1/suppliers/{id} | 编辑（分类/银行/联系人） | supplier:update |
| PUT | /api/v1/suppliers/{id}/status | 状态（现有） | supplier:disable |
| POST | /api/v1/suppliers/{id}/qualification | 提交准入审核 | supplier:qualify |
| GET | /api/v1/suppliers/{id}/contacts | 联系人列表 | supplier:view |
| POST | /api/v1/supplier-items | 维护供货关系（约定价/交期） | supplier:create |

### 2.3 请购 PR（sc-purchase-service）

| 方法 | 路径 | 说明 | 权限点 |
|---|---|---|---|
| POST | /api/v1/requisitions | 创建草稿 | pr:create |
| GET | /api/v1/requisitions | 分页 | pr:view |
| GET | /api/v1/requisitions/{id} | 详情（含明细/审批轨迹） | pr:view |
| PUT | /api/v1/requisitions/{id} | 编辑草稿 | pr:update |
| POST | /api/v1/requisitions/{id}/submit | 提交审批 | pr:submit |
| POST | /api/v1/requisitions/{id}/convert | 转采购订单 | pr:convert |
| POST | /api/v1/requisitions/{id}/cancel | 取消 | pr:update |

### 2.4 采购订单 PO（sc-purchase-service）

| 方法 | 路径 | 说明 | 权限点 |
|---|---|---|---|
| POST | /api/v1/orders | 创建（可带 prId 从请购转单） | po:create |
| GET | /api/v1/orders | 分页 | po:view |
| GET | /api/v1/orders/{id} | 详情（明细/审批/收货/应付关联） | po:view |
| PUT | /api/v1/orders/{id} | 草稿编辑 / 变更提交 | po:update |
| POST | /api/v1/orders/{id}/submit | 提交审批 | po:submit |
| POST | /api/v1/orders/{id}/cancel | 取消 | po:update |
| PUT | /api/v1/orders/{id}/status | 状态推进（SHIPPED/RECEIVED 简化为手工登记） | po:update |

### 2.5 审批任务（通用，purchase/payment 各自暴露）

| 方法 | 路径 | 说明 | 权限点 |
|---|---|---|---|
| GET | /api/v1/approval-tasks/mine | 我的待办（分页） | approval:task:view |
| GET | /api/v1/approval-tasks | 按 bizType/bizId 查任务与轨迹 | approval:task:view |
| POST | /api/v1/approval-tasks/{id}/approve | 通过 | approval:task:approve |
| POST | /api/v1/approval-tasks/{id}/reject | 驳回（附意见） | approval:task:reject |
| POST | /api/v1/approval-tasks/{id}/transfer | 转交 | approval:task:transfer |

### 2.6 收货 / 质检 / 入库（sc-inventory-service）

| 方法 | 路径 | 说明 | 权限点 |
|---|---|---|---|
| POST | /api/v1/receives | 登记收货（明细含差异） | receive:create |
| GET | /api/v1/receives | 按订单/日期分页 | receive:view |
| GET | /api/v1/receives/{id} | 详情 | receive:view |
| POST | /api/v1/quality-inspections | 登记质检（免检/全检/抽检，合格/不合格数量） | qc:create |
| GET | /api/v1/quality-inspections | 查询 | qc:view |
| POST | /api/v1/receives/{id}/stock-in | 质检合格入库（核销预留 + 写流水） | stock:stock-in |

### 2.7 库存（sc-inventory-service）

| 方法 | 路径 | 说明 | 权限点 |
|---|---|---|---|
| GET | /api/v1/inventory/items | 物料分页（现有） | inventory:view |
| POST | /api/v1/inventory/items | 新建物料（现有） | inventory:view |
| GET | /api/v1/inventory | 库存余额（含安全库存/再订购点提示） | inventory:view |
| GET | /api/v1/inventory/ledger | 库存流水（按物料/时间） | inventory:ledger |
| POST | /api/v1/inventory/reserve | 预留（内部 Feign，现有） | 服务间 |

### 2.8 发票 / 三单匹配 / 付款（sc-payment-service）

| 方法 | 路径 | 说明 | 权限点 |
|---|---|---|---|
| POST | /api/v1/invoices | 登记发票 | invoice:create |
| GET | /api/v1/invoices | 分页 | invoice:view |
| GET | /api/v1/invoices/{id} | 详情 | invoice:view |
| POST | /api/v1/invoices/{id}/match | 三单匹配（订单/收货/发票） | invoice:match |
| GET | /api/v1/payables | 分页（现有） | payable:view |
| POST | /api/v1/payments | 创建付款单 | payment:create |
| GET | /api/v1/payments | 分页 | payment:view |
| POST | /api/v1/payments/{id}/submit | 提交付款审批 | payment:submit |
| POST | /api/v1/payments/{id}/pay | 执行付款 + 核销应付 | payment:pay |

### 2.9 报表（一期轻量，各服务提供统计接口）

| 方法 | 路径 | 说明 | 权限点 |
|---|---|---|---|
| GET | /api/v1/reports/procurement-dashboard | 采购看板（订单状态分布/金额/逾期） | report:procurement |
| GET | /api/v1/reports/payable-aging | 应付账龄 | report:payable-aging |
| GET | /api/v1/reports/inventory-summary | 库存汇总 | report:inventory |

## 3. 与现有接口的兼容

- 保留：/api/v1/auth/register|login、/api/v1/suppliers/*（现有）、/api/v1/orders（现有创建/审批→调整）、/api/v1/inventory/items|reserve、/api/v1/payments（现有）。
- 调整：`PUT /api/v1/orders/{id}/approve` 与 `PUT /api/v1/payments/{id}/approve` 由**审批任务端点**取代（审批动作统一走 approval-tasks）；旧端点一期内保留兼容，二期移除。
- 新增数据权限/分页/幂等/校验为全局约定，现有端点逐步对齐。

## 4. 说明

- 本契约不包含文件路径与代码实现；具体 DTO 字段以 schema.md 表字段 + 前端页面需求（#13）为准。
- openapi.yml 更新为独立任务（实现期随代码同步），本文件是契约源头。