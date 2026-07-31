# frontend-demo

Spring Cloud Demo 的前端管理界面，覆盖采购-入库-付款全链路。

## 运行地址

`http://localhost:5174/`

## 技术栈

- React + TypeScript + Vite
- Ant Design 5（中文）
- React Router v7
- Axios（直连后端 Gateway `http://localhost:8080`）

## 项目结构

```
frontend-demo/src/
├── api/
│   ├── client.ts          # axios 实例 + JWT 拦截器 + 401 自动跳转登录
│   ├── auth.ts            # 注册/登录 API
│   ├── supplier.ts        # 供应商 CRUD API
│   ├── purchase.ts        # 采购订单 API
│   ├── inventory.ts       # 物料/入库/预留 API
│   └── payment.ts         # 应付账款 API
├── context/
│   └── AuthContext.tsx     # 认证状态管理（login/logout/isAuthenticated）
├── layouts/
│   └── MainLayout.tsx     # 侧边栏导航 + 顶栏用户信息 + 内容区布局
├── pages/
│   ├── Login.tsx          # 登录页
│   ├── Register.tsx       # 注册页（含角色选择）
│   ├── Dashboard.tsx      # 首页仪表盘 + 业务流程图
│   ├── SupplierList.tsx   # 供应商列表 + 新增 + 状态修改
│   ├── ItemList.tsx       # 物料管理 + 入库收货 + 库存预留
│   ├── OrderList.tsx      # 采购订单 + 创建（多行项）+ 审批
│   └── PaymentList.tsx    # 应付账款 + 创建 + 审批
├── App.tsx                # 路由配置 + 路由守卫
├── main.tsx               # 入口
└── index.css              # 全局样式
```

## 技术要点

- **后端直连** `http://localhost:8080`（Gateway），token 自动携带、401 自动踢回登录页
- **路由守卫**：`GuestRoute` 阻止已登录用户访问注册/登录页；`ProtectedRoute` 阻止未登录用户访问业务页面
- **Ant Design 中文**：`ConfigProvider locale={zhCN}` 全局中文
- **生产构建**已验证通过（`npx vite build`，零错误）

## 使用方式

1. 先注册一个账号 → 登录
2. 创建供应商 → 创建物料 → 创建采购订单 → 审批 → 入库 → 创建应付 → 审批
3. 全链路就走通了

## 开发命令

```bash
npm install       # 安装依赖
npm run dev       # 启动开发服务器
npm run build     # 生产构建
```