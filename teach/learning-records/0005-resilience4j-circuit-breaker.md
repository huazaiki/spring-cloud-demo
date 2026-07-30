 # Resilience4j 熔断、降级与重试

 学习者理解了级联故障的机制：下游慢/不可用时上游线程堆积导致整体不可用。掌握了断路器三态状态机（CLOSED → OPEN → HALF_OPEN）的运作逻辑，以及 HALF_OPEN 自动试探恢复的设计价值。理解了 Timeout / Retry / Circuit Breaker 三者的分层协作关系（非三选一），以及退避策略的核心理念（给下游恢复的时间窗口，而非盲目快速重试）。掌握了 Fallback 降级的正确姿势：不抛异常、记录上下文、不阻塞主流程。理解了"Failing fast is better than failing slow"的防御哲学。

 **Implications:** 可以进入 Seata 分布式事务 — 跨服务操作的部分失败后的回滚问题。
