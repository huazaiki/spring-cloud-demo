-- V4：默认审批链种子（docs/design/approval-engine.md §5）
INSERT INTO approval_flow (id, flow_code, flow_name, biz_type, rule_json, status) VALUES
 (1, 'PR_DEFAULT', '请购默认审批链', 'PR',
  JSON_OBJECT(
    'flowCode', 'PR_DEFAULT',
    'bizType', 'PR',
    'nodes', JSON_ARRAY(
      JSON_OBJECT('key','dept_manager','name','部门经理审批','approver',JSON_OBJECT('type','ROLE','value','DEPT_MANAGER','scope','APPLICANT_DEPT'),'enabled',JSON_OBJECT('field','totalAmount','op','gte','value',0),'optional',false),
      JSON_OBJECT('key','purchase_manager','name','采购经理审批','approver',JSON_OBJECT('type','ROLE','value','PURCHASE_MANAGER','scope','ANY'),'enabled',JSON_OBJECT('field','totalAmount','op','gte','value',50000),'optional',true)
    ),
    'rejectStrategy', 'BACK_TO_SUBMITTER',
    'transferAllowed', true
  ),
  'ACTIVE')
ON DUPLICATE KEY UPDATE rule_json = VALUES(rule_json);