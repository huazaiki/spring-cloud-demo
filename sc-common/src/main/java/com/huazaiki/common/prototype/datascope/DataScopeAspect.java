package com.huazaiki.common.prototype.datascope;

/** PROTOTYPE — 数据权限 AOP 切面概念代码，展示注解的预期行为 */
public class DataScopeAspect {

    /*
     * 切面思路（伪代码，实际需要 Spring AOP + MyBatis-Plus 拦截器配合）：
     *
     * @Around("@annotation(dataScope)")
     * public Object around(ProceedingJoinPoint pjp, DataScope dataScope) {
     *     // 1. 从 DataScopeContext 获取当前用户信息
     *     Long userId  = DataScopeContext.currentUserId();
     *     Long deptId  = DataScopeContext.currentDeptId();
     *
     *     // 2. 根据注解类型构造过滤条件
     *     switch (dataScope.value()) {
     *         case SELF:
     *             // 追加 WHERE create_by = #{userId}
     *             break;
     *         case DEPT:
     *             // 追加 WHERE dept_id = #{deptId}
     *             break;
     *         case SUPPLIER:
     *             // 追加 WHERE supplier_id IN (SELECT supplier_id FROM buyer_supplier WHERE user_id = #{userId})
     *             break;
     *         case ALL:
     *             // 不做过滤
     *             break;
     *     }
     *
     *     return pjp.proceed();
     * }
     *
     * 使用示例：
     *   @DataScope(ScopeType.DEPT)
     *   List<PurchaseRequisition> selectByCondition(...);
     *
     * 跨服务透传方案（Gateway 层）：
     *   // 1. Gateway Filter 解析 JWT → 设置 Header
     *   //    X-User-Id: 1001
     *   //    X-Dept-Id: 10
     *   //    X-Roles: ROLE_REQUESTER,ROLE_BUYER
     *
     *   // 2. 各微服务用 Filter/Interceptor 读取 Header → 写入 DataScopeContext
     *   //    @Component
     *   //    public class DataScopeFilter extends OncePerRequestFilter {
     *   //        protected void doFilterInternal(...) {
     *   //            DataScopeContext.set(userId, deptId, roles);
     *   //            chain.doFilter(request, response);
     *   //            DataScopeContext.clear();
     *   //        }
     *   //    }
     */
}
