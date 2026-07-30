# OpenFeign 声明式服务间调用

学习者理解了 OpenFeign 的设计哲学——声明式优于命令式。掌握了三个核心概念：动态代理机制（java.lang.reflect.Proxy 生成运行时实现）、接口即契约（Feign 接口是客户端视角的需求声明，而非服务端 API 的镜像）、以及 Feign + LoadBalancer + Nacos 三角关系中各自的分工。理解了同步调用（Feign）和异步调用（Kafka）的选择标准：调用方是否必须等待结果。

**Implications:** 可以进入 Resilience4j 熔断、降级与重试——当 Feign 调用的下游不可用时的保护策略。
