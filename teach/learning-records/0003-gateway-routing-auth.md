 # Gateway 路由、鉴权与过滤器

 学习者理解了 Gateway 路由的三要素（Predicate + URI）、GlobalFilter 的工作机制（Ordered 接口控制执行顺序）、JWT 鉴权的完整链路（sc-auth-service 签发 → Gateway 校验 → 透传 X-User-Id / X-User-Role 到下游）。理解了 PUBLIC_PATHS 白名单的设计意图和 X-User-* 透传头的关注点分离优势。

 **Implications:** 可以进入 OpenFeign 声明式服务间调用，以及 LoadBalancer 在 Feign 客户端中的自动介入。
