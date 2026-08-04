# 前端页面 / 路由与权限映射设计

> 决策 ticket：「设计前端页面/路由与权限映射」（[#13](https://github.com/huazaiki/spring-cloud-demo/issues/13)）的产出。
> 依据：Q9 决策、API 契约（`docs/design/api-contract.md`）、权限（`docs/design/permissions.md`）、现有 `frontend-demo`。

## 1. 页面与路由

| 路由 | 页面 | 菜单（权限点） | 主要功能 |
|---|---|---|---|
| /login、/register | 登录/注册 | — | 登录发 token；注册（一期保留） |
| / | 首页/看板 | menu:dashboard | 个人待办数、快捷入口、采购看板摘要 |
| /suppliers | 供应商管理 | menu:suppliers | 列表/详情/新建/编辑/准入/供货关系 |
| /items | 物料与库存 | menu:items | 物料列表/新建、库存余额、库存流水 |
| /requisitions | 请购管理 | menu:requisitions | 列表/新建/详情/编辑/提交/转订单/取消 |
| /orders | 采购订单 | menu:orders | 列表/详情/新建（可基于 PR）/变更/取消/提交审批 |
| /receives | 收货质检入库 | menu:receives | 收货登记、质检登记、入库（核销） |
| /finance | 发票付款 | menu:finance | 发票登记/三单匹配、付款单/执行付款、应付查询 |
| /approval-tasks | 待办中心 | menu:approval-tasks | 我的待办、审批（通过/驳回/转交）、单据轨迹 |
| /reports | 报表 | menu:reports | 采购看板、应付账龄、库存汇总 |
| /system | 系统管理 | menu:system | 用户/部门/角色/权限点管理 |

## 2. 权限路由与动态菜单

- 登录成功 → `GET /api/v1/auth/me` → 存 `UserContext`：`{ user, dept, roles, permissions, menus }`（localStorage 持久化 + 刷新恢复）。
- **动态菜单**：侧边栏由 `menus`（MENU 权限点）渲染，替代现有硬编码 menuItems。
- **路由守卫**：`ProtectedRoute` 校验登录；`PermissionRoute` 按路由所需权限点过滤，无权限渲染 403 页。
- **按钮级权限**：`<Can permission="po:submit">` 组件，无权限隐藏操作按钮。
- 路由表按权限点动态注册（`useRoutes` + 过滤），未授权路由 fallback 403。

## 3. 状态管理（轻量）

- **UserContext**（扩展现有 AuthContext）：用户/部门/角色/权限点/菜单；登录、登出、刷新恢复。
- **全局请求态/错误**：axios 拦截器（client.ts 扩展）——401 清登录跳转、业务错误码 → antd `message`、loading 由页面局部管理。
- **复杂表单/列表缓存**：一期不引 redux；如分页/搜索状态跨页共享确有需要，引入 `zustand`（小体积）作为补充。
- 结论：**React Context + hooks 为主，zustand 按需**。

## 4. 统一请求层（client.ts 扩展）

- 响应类型：`ApiResponse<T>`、分页 `PageData<T> { records, total, page, size }`。
- 幂等：写操作（创建/提交/付款）自动带 `Idempotency-Key`（uuid，一次操作一个）。
- 错误处理：401 登出；403 提示无权限；409/422 展示后端 message；500 通用提示。
- 金额/数量以字符串传输（DECIMAL），前端展示格式化。

## 5. 表单与组件规范

- 表单：antd `Form` + `rules` 校验；金额 `InputNumber`（precision=2）、数量（precision=4）、日期 `DatePicker`。
- 列表：统一列表页结构（搜索区 + 分页表格），复用 antd `Table` + `Pagination`。
- 单据详情：`Drawer`/独立页展示明细与审批轨迹（`approval_record`）。
- 状态展示：状态枚举 → Tag 映射（如 DRAFT/SUBMITTED/APPROVED/...）。
- 审批操作组：通过/驳回（必填意见）/转交（选人），按权限点控制可见。

## 6. 与后端契约的对应

- 所有请求对接 `docs/design/api-contract.md` 端点；接口字段以契约为准。
- 后端未就绪前，可用契约 mock（MSW 或静态 json）并行开发（二期优化，一期可选）。