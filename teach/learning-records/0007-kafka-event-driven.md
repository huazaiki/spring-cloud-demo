 # Kafka 消息队列与事件驱动

 学习者理解了同步调用（Feign）与异步消息（Kafka）的关系模型差异：电话 vs 邮件。掌握了 Topic 作为新形态契约的设计含义——生产者不指定消费者，消费者只关心消息格式，双方完全解耦。理解了命令（指定执行者）和事件（宣告发生）的思维转变，以及事件驱动带来的"开放扩展"能力（加新消费者不改已有代码）。掌握了 at-least-once 语义和幂等消费的必要性——分布式系统中永远无法保证 absolutely exactly-once。

 **Implications:** 可以进入总结课——将 Gateway/Nacos/Feign/Resilience4j/Seata/Kafka 串起来，回答"为什么微服务值得它的复杂度"。
