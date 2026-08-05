-- V5：6 角色采购流程种子数据，替换 V3 的 8 角色模型
-- 清理旧映射（幂等：存量库删旧数据，新库无影响）
DELETE FROM sys_role_permission WHERE role_id IN (1,2,3,4,5,6,7,8);
DELETE FROM sys_permission WHERE id BETWEEN 1001 AND 2010;

-- 删除不再需要的旧角色（PURCHASE_MANAGER、FINANCE_MANAGER、FINANCE_DIRECTOR）
DELETE FROM sys_role WHERE id IN (3,7,8);

-- 更新保留角色为新的 roleCode
UPDATE sys_role SET role_code = 'ROLE_SYS_ADMIN',     role_name = '系统管理员',   description = '用户、角色、权限、部门、基础数据维护' WHERE id = 1;
UPDATE sys_role SET role_code = 'ROLE_BUYER',         role_name = '采购员',       description = '管理供应商、物料、询价、创建采购订单、跟踪订单' WHERE id = 2;
UPDATE sys_role SET role_code = 'ROLE_DEPT_APPROVER', role_name = '部门审批人',   description = '审批采购申请，控制采购需求的合理性' WHERE id = 4;
UPDATE sys_role SET role_code = 'ROLE_WAREHOUSE',     role_name = '仓储管理员',   description = '收货确认、入库、库存管理' WHERE id = 5;
UPDATE sys_role SET role_code = 'ROLE_FINANCE',       role_name = '财务专员',     description = '发票审核、付款审批、付款管理' WHERE id = 6;

-- 插入新角色：需求申请人（复用 ID=3）
INSERT INTO sys_role (id, role_code, role_name, description) VALUES
 (3, 'ROLE_REQUESTER', '需求申请人', '提交采购申请、查看申请进度')
ON DUPLICATE KEY UPDATE role_code = VALUES(role_code), role_name = VALUES(role_name), description = VALUES(description);

-- 新权限点（domain:resource:action 命名规范）
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, sort_no) VALUES
 -- 系统管理 (ROLE_SYS_ADMIN)
 (3001,0,'system:user:create','创建用户','API',10),
 (3002,0,'system:user:update','编辑用户','API',11),
 (3003,0,'system:user:delete','删除用户','API',12),
 (3004,0,'system:user:view','查看用户','API',13),
 (3005,0,'system:role:assign','分配角色','API',20),
 (3006,0,'system:role:revoke','撤销角色','API',21),
 (3007,0,'system:role:view','查看角色','API',22),
 (3008,0,'system:permission:grant','授予权限','API',30),
 (3009,0,'system:permission:revoke','撤销权限','API',31),
 (3010,0,'system:permission:view','查看权限','API',32),
 (3011,0,'system:dept:create','创建部门','API',40),
 (3012,0,'system:dept:update','编辑部门','API',41),
 (3013,0,'system:dept:delete','删除部门','API',42),
 (3014,0,'system:dept:view','查看部门','API',43),
 (3015,0,'system:config:manage','系统配置管理','API',50),
 -- 请购 (ROLE_REQUESTER + ROLE_DEPT_APPROVER)
 (3101,0,'purchase:requisition:create','创建请购','API',100),
 (3102,0,'purchase:requisition:submit','提交请购','API',101),
 (3103,0,'purchase:requisition:view','查看请购','API',102),
 (3104,0,'purchase:requisition:cancel','撤回请购','API',103),
 (3105,0,'purchase:requisition:approve','审批通过','API',110),
 (3106,0,'purchase:requisition:reject','审批驳回','API',111),
 -- 采购订单 (ROLE_BUYER)
 (3201,0,'supplier:manage','供应商管理','API',200),
 (3202,0,'supplier:view','供应商查看','API',201),
 (3203,0,'purchase:item:manage','物料管理','API',210),
 (3204,0,'purchase:item:view','物料查看','API',211),
 (3205,0,'purchase:order:create','创建采购订单','API',220),
 (3206,0,'purchase:order:view','查看采购订单','API',221),
 (3207,0,'purchase:order:confirm','确认采购订单','API',222),
 (3208,0,'purchase:order:track','跟踪订单状态','API',223),
 -- 仓储 (ROLE_WAREHOUSE)
 (3301,0,'inventory:receive:confirm','收货确认','API',300),
 (3302,0,'inventory:receive:view','查看收货','API',301),
 (3303,0,'inventory:stock:view','查看库存','API',310),
 (3304,0,'inventory:stock:adjust','库存调整','API',311),
 (3305,0,'inventory:ledger:view','库存流水','API',320),
 -- 财务 (ROLE_FINANCE)
 (3401,0,'payment:invoice:audit','发票审核','API',400),
 (3402,0,'payment:invoice:view','查看发票','API',401),
 (3403,0,'payment:voucher:create','创建付款单','API',410),
 (3404,0,'payment:voucher:approve','付款审批','API',411),
 (3405,0,'payment:voucher:view','查看付款单','API',412),
 (3406,0,'payment:payable:view','应付账款查询','API',420),
 -- 菜单权限（前端动态路由）
 (3901,0,'menu:dashboard','首页','MENU',1),
 (3902,0,'menu:requisitions','请购管理','MENU',2),
 (3903,0,'menu:orders','采购订单','MENU',3),
 (3904,0,'menu:suppliers','供应商管理','MENU',4),
 (3905,0,'menu:inventory','仓储管理','MENU',5),
 (3906,0,'menu:finance','财务管理','MENU',6),
 (3907,0,'menu:system','系统管理','MENU',7)
ON DUPLICATE KEY UPDATE perm_name = VALUES(perm_name), perm_type = VALUES(perm_type);

-- 角色-权限映射
INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
 -- ROLE_SYS_ADMIN (1): 全部系统管理 + 所有菜单
 (50001,1,3001),(50002,1,3002),(50003,1,3003),(50004,1,3004),
 (50005,1,3005),(50006,1,3006),(50007,1,3007),
 (50008,1,3008),(50009,1,3009),(50010,1,3010),
 (50011,1,3011),(50012,1,3012),(50013,1,3013),(50014,1,3014),
 (50015,1,3015),
 (50016,1,3901),(50017,1,3902),(50018,1,3903),(50019,1,3904),(50020,1,3905),(50021,1,3906),(50022,1,3907),
 -- ROLE_BUYER (2): 供应商 + 物料 + 订单 + 相关菜单
 (50101,2,3201),(50102,2,3202),
 (50103,2,3203),(50104,2,3204),
 (50105,2,3205),(50106,2,3206),(50107,2,3207),(50108,2,3208),
 (50109,2,3901),(50110,2,3903),(50111,2,3904),
 -- ROLE_REQUESTER (3): 请购 + 相关菜单
 (50201,3,3101),(50202,3,3102),(50203,3,3103),(50204,3,3104),
 (50205,3,3901),(50206,3,3902),
 -- ROLE_DEPT_APPROVER (4): 审批 + 相关菜单
 (50301,4,3105),(50302,4,3106),(50303,4,3103),
 (50304,4,3901),(50305,4,3902),
 -- ROLE_WAREHOUSE (5): 仓储全部 + 相关菜单
 (50401,5,3301),(50402,5,3302),
 (50403,5,3303),(50404,5,3304),
 (50405,5,3305),
 (50406,5,3901),(50407,5,3905),
 -- ROLE_FINANCE (6): 财务全部 + 相关菜单
 (50501,6,3401),(50502,6,3402),
 (50503,6,3403),(50504,6,3404),(50505,6,3405),
 (50506,6,3406),
 (50507,6,3901),(50508,6,3906)
ON DUPLICATE KEY UPDATE permission_id = VALUES(permission_id);
