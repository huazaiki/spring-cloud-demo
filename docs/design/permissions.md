# 权限点清单与数据权限实现方案

> 决策 ticket：「定义权限点清单与数据权限实现方案」（[#6](https://github.com/huazaiki/spring-cloud-demo/issues/6)）的产出。
> 依据：Q5 决策、表结构（`docs/design/schema.md`：sys_permission/sys_role/sys_user_role/sys_role_permission）、审批链角色码（`docs/design/approval-engine.md`）。
> 种子数据落库由「设计 Flyway 基线迁移方案」（#10）执行。

## 1. 权限点清单（resource:action）

按模块分组，`perm_type`：MENU / BUTTON / API。

### 系统管理（auth）
| perm_code | 类型 | 说明 |
|---|---|---|
| user:list / user:create / user:update | API | 用户管理 |
| dept:manage | API | 部门管理 |
| role:manage | API | 角色与权限分配 |
| permission:view | API | 权限点/菜单查询 |

### 供应商（supplier）
| perm_code | 类型 | 说明 |
|---|---|---|
| supplier:view / supplier:create / supplier:update | API | 供应商管理 |
| supplier:qualify | API | 准入审核 |
| supplier:disable | API | 停用/拉黑 |

### 请购（purchase）
| perm_code | 类型 | 说明 |
|---|---|---|
| pr:create / pr:update / pr:view | API | 请购单 |
| pr:submit / pr:convert | API | 提交审批 / 转订单 |

### 采购订单（purchase）
| perm_code | 类型 | 说明 |
|---|---|---|
| po:create / po:update / po:view | API | 采购订单 |
| po:submit | API | 提交审批 |

### 审批任务（通用）
| perm_code | 类型 | 说明 |
|---|---|---|
| approval:task:view | API | 查看待办/轨迹 |
| approval:task:approve / approval:task:reject / approval:task:transfer | API | 审批操作 |

### 收货 / 质检 / 入库（inventory）
| perm_code | 类型 | 说明 |
|---|---|---|
| receive:create / receive:view | API | 收货 |
| qc:create / qc:view | API | 质检 |
| stock:stock-in | API | 入库（核销预留） |

### 库存（inventory）
| perm_code | 类型 | 说明 |
|---|---|---|
| inventory:view | API | 库存余额 |
| inventory:ledger | API | 库存流水 |

### 财务（payment）
| perm_code | 类型 | 说明 |
|---|---|---|
| invoice:create / invoice:view / invoice:match | API | 发票与三单匹配 |
| payable:view | API | 应付查询 |
| payment:create / payment:submit / payment:pay | API | 付款 |

### 报表
| perm_code | 类型 | 说明 |
|---|---|---|
| report:procurement / report:payable-aging / report:inventory | API | 一期三张报表 |

### 菜单（MENU）
| perm_code | 路由 | 说明 |
|---|---|---|
| menu:dashboard | / | 首页 |
| menu:suppliers | /suppliers | 供应商管理 |
| menu:items | /items | 物料与库存 |
| menu:requisitions | /requisitions | 请购管理 |
| menu:orders | /orders | 采购订单 |
| menu:receives | /receives | 收货质检入库 |
| menu:finance | /finance | 发票付款 |
| menu:approval-tasks | /approval-tasks | 待办中心 |
| menu:reports | /reports | 报表 |
| menu:system | /system | 系统管理（ADMIN） |

## 2. 角色种子与权限映射

| role_code | 角色 | 权限点（含菜单） |
|---|---|---|
| ADMIN | 管理员 | 全部 |
| PURCHASER | 采购员 | menu:requisitions/orders/items/suppliers/dashboard、pr:*、po:*、supplier:view、inventory:view、approval:task:view |
| PURCHASE_MANAGER | 采购经理 | PURCHASER + menu:reports、approval:task:approve/reject/transfer（PR/PO 节点）、report:procurement |
| DEPT_MANAGER | 部门经理 | menu:requisitions/dashboard、pr:create/update/view/submit、approval:task:view（本部门单据） |
| WAREHOUSE | 仓管员 | menu:receives/items/dashboard、receive:*、qc:*、stock:stock-in、inventory:view/ledger |
| FINANCE | 财务 | menu:finance/reports/dashboard、invoice:*、payable:view、payment:*、report:payable-aging |
| FINANCE_MANAGER | 财务经理 | FINANCE + approval:task:approve/reject/transfer（PAYMENT 节点） |
| FINANCE_DIRECTOR | 财务总监 | FINANCE_MANAGER + approval:task:approve（PO 大额节点） |

> 审批链节点审批人 = 拥有 `approval:task:approve` + 满足节点 approver 解析（ROLE + scope）的用户。角色码与 `docs/design/approval-engine.md` 默认审批链对齐。

## 3. 数据权限（Data Scope）实现

- **维度**：部门。范围取值：`SELF`（本人）、`DEPT`（本部门）、`DEPT_AND_CHILD`（本部门及下级）、`ALL`（全部，仅 ADMIN）。
- **透传**：网关把 JWT 身份写入请求头 `X-User-Id` / `X-User-Dept` / `X-User-Roles`；服务内读取。
- **执行方式**：MyBatis-Plus 查询拦截器（`DataScopeInterceptor`）按权限点元数据对查询追加部门条件（如 `applicant_dept_id IN (子部门)`）；写操作在 Service 层显式校验（越权抛 403）。
- **适用对象**：
  - 请购：申请人部门（DEPT_AND_CHILD 管理员可 ALL）
  - 订单：申请人部门
  - 待办：审批人本人（SELF，硬约束）
  - 供应商/物料/库存/报表：普通角色按部门过滤（若启用），ADMIN ALL（一期从简：供应商/物料为全局主数据，不按部门过滤）
- **声明方式**：权限点元数据带 `dataScope` 属性（NONE/DEPT/DEPT_AND_CHILD/ALL），由拦截器消费；无需每个查询手写。

## 4. 菜单-权限点-路由映射（前端）

前端登录后调 `GET /api/v1/auth/me` 获取 `menus`（由 MENU 权限点派生）与 `permissions`：
- 侧边栏按 menus 渲染；
- 路由守卫按菜单/权限拦截，无权限显示 403 页；
- 按钮级用 `<Can permission="po:submit">` 包裹（前端实现见 `docs/design/frontend.md`）。

## 5. 种子数据（交给 #10 落地）

- 权限点/角色/角色-权限关联/菜单 → auth_db 种子脚本；
- 默认审批链 → purchase_db / payment_db 种子脚本。