-- V3：RBAC 种子数据（幂等；权限点/角色见 docs/design/permissions.md）
INSERT INTO sys_role (id, role_code, role_name, description) VALUES
 (1, 'ADMIN', '管理员', '全部权限'),
 (2, 'PURCHASER', '采购员', '请购/订单/查询'),
 (3, 'PURCHASE_MANAGER', '采购经理', '采购员 + 审批 + 采购报表'),
 (4, 'DEPT_MANAGER', '部门经理', '本部门请购审批'),
 (5, 'WAREHOUSE', '仓管员', '收货/质检/入库/库存'),
 (6, 'FINANCE', '财务', '发票/付款/应付'),
 (7, 'FINANCE_MANAGER', '财务经理', '财务 + 付款审批'),
 (8, 'FINANCE_DIRECTOR', '财务总监', '财务经理 + 大额订单审批')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), description = VALUES(description);

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, route_path, sort_no) VALUES
 -- 系统管理
 (1001,0,'user:list','用户查询','API',NULL,10),(1002,0,'user:create','用户创建','API',NULL,11),(1003,0,'user:update','用户编辑','API',NULL,12),
 (1004,0,'dept:manage','部门管理','API',NULL,13),(1005,0,'role:manage','角色管理','API',NULL,14),(1006,0,'permission:view','权限点查询','API',NULL,15),
 -- 供应商
 (1011,0,'supplier:view','供应商查询','API',NULL,20),(1012,0,'supplier:create','供应商创建','API',NULL,21),(1013,0,'supplier:update','供应商编辑','API',NULL,22),
 (1014,0,'supplier:qualify','供应商准入','API',NULL,23),(1015,0,'supplier:disable','供应商停用','API',NULL,24),
 -- 请购
 (1021,0,'pr:create','创建请购','API',NULL,30),(1022,0,'pr:update','编辑请购','API',NULL,31),(1023,0,'pr:view','查看请购','API',NULL,32),
 (1024,0,'pr:submit','提交请购','API',NULL,33),(1025,0,'pr:convert','转采购订单','API',NULL,34),
 -- 订单
 (1031,0,'po:create','创建订单','API',NULL,40),(1032,0,'po:update','编辑订单','API',NULL,41),(1033,0,'po:view','查看订单','API',NULL,42),(1034,0,'po:submit','提交订单','API',NULL,43),
 -- 审批任务
 (1041,0,'approval:task:view','查看待办','API',NULL,50),(1042,0,'approval:task:approve','审批通过','API',NULL,51),(1043,0,'approval:task:reject','审批驳回','API',NULL,52),(1044,0,'approval:task:transfer','审批转交','API',NULL,53),
 -- 收货/质检/入库
 (1051,0,'receive:create','登记收货','API',NULL,60),(1052,0,'receive:view','查看收货','API',NULL,61),(1053,0,'qc:create','登记质检','API',NULL,62),(1054,0,'qc:view','查看质检','API',NULL,63),(1055,0,'stock:stock-in','入库','API',NULL,64),
 -- 库存
 (1061,0,'inventory:view','库存查询','API',NULL,70),(1062,0,'inventory:ledger','库存流水','API',NULL,71),
 -- 财务
 (1071,0,'invoice:create','发票登记','API',NULL,80),(1072,0,'invoice:view','发票查询','API',NULL,81),(1073,0,'invoice:match','三单匹配','API',NULL,82),
 (1074,0,'payable:view','应付查询','API',NULL,83),(1075,0,'payment:create','付款单创建','API',NULL,84),(1076,0,'payment:submit','付款提交审批','API',NULL,85),(1077,0,'payment:pay','执行付款','API',NULL,86),
 -- 报表
 (1081,0,'report:procurement','采购报表','API',NULL,90),(1082,0,'report:payable-aging','应付账龄','API',NULL,91),(1083,0,'report:inventory','库存汇总','API',NULL,92),
 -- 菜单
 (2001,0,'menu:dashboard','首页','MENU','/',1),(2002,0,'menu:suppliers','供应商管理','MENU','/suppliers',2),(2003,0,'menu:items','物料与库存','MENU','/items',3),
 (2004,0,'menu:requisitions','请购管理','MENU','/requisitions',4),(2005,0,'menu:orders','采购订单','MENU','/orders',5),(2006,0,'menu:receives','收货质检入库','MENU','/receives',6),
 (2007,0,'menu:finance','发票付款','MENU','/finance',7),(2008,0,'menu:approval-tasks','待办中心','MENU','/approval-tasks',8),(2009,0,'menu:reports','报表','MENU','/reports',9),(2010,0,'menu:system','系统管理','MENU','/system',10)
ON DUPLICATE KEY UPDATE perm_name = VALUES(perm_name), perm_type = VALUES(perm_type), route_path = VALUES(route_path);

-- 角色-权限映射
INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
 -- ADMIN: 全部
 (100001,1,1001),(100002,1,1002),(100003,1,1003),(100004,1,1004),(100005,1,1005),(100006,1,1006),
 (100007,1,1011),(100008,1,1012),(100009,1,1013),(100010,1,1014),(100011,1,1015),
 (100012,1,1021),(100013,1,1022),(100014,1,1023),(100015,1,1024),(100016,1,1025),
 (100017,1,1031),(100018,1,1032),(100019,1,1033),(100020,1,1034),
 (100021,1,1041),(100022,1,1042),(100023,1,1043),(100024,1,1044),
 (100025,1,1051),(100026,1,1052),(100027,1,1053),(100028,1,1054),(100029,1,1055),
 (100030,1,1061),(100031,1,1062),
 (100032,1,1071),(100033,1,1072),(100034,1,1073),(100035,1,1074),(100036,1,1075),(100037,1,1076),(100038,1,1077),
 (100039,1,1081),(100040,1,1082),(100041,1,1083),
 (100042,1,2001),(100043,1,2002),(100044,1,2003),(100045,1,2004),(100046,1,2005),(100047,1,2006),(100048,1,2007),(100049,1,2008),(100050,1,2009),(100051,1,2010),
 -- PURCHASER
 (200001,2,1021),(200002,2,1022),(200003,2,1023),(200004,2,1024),(200005,2,1025),
 (200006,2,1031),(200007,2,1032),(200008,2,1033),(200009,2,1034),(200010,2,1011),(200011,2,1061),(200012,2,1041),
 (200013,2,2001),(200014,2,2004),(200015,2,2005),(200016,2,2003),(200017,2,2002),
 -- PURCHASE_MANAGER
 (300001,3,1042),(300002,3,1043),(300003,3,1044),(300004,3,1081),(300005,3,2009),
 -- DEPT_MANAGER
 (400001,4,1021),(400002,4,1022),(400003,4,1023),(400004,4,1024),(400005,4,1041),(400006,4,2001),(400007,4,2004),
 -- WAREHOUSE
 (500001,5,1051),(500002,5,1052),(500003,5,1053),(500004,5,1054),(500005,5,1055),(500006,5,1061),(500007,5,1062),
 (500008,5,2001),(500009,5,2006),(500010,5,2003),
 -- FINANCE
 (600001,6,1071),(600002,6,1072),(600003,6,1073),(600004,6,1074),(600005,6,1075),(600006,6,1076),(600007,6,1077),
 (600008,6,1082),(600009,6,2001),(600010,6,2007),(600011,6,2009),
 -- FINANCE_MANAGER
 (700001,7,1042),(700002,7,1043),(700003,7,1044),
 -- FINANCE_DIRECTOR
 (800001,8,1042),(800002,8,1043),(800003,8,1044)
ON DUPLICATE KEY UPDATE permission_id = VALUES(permission_id);