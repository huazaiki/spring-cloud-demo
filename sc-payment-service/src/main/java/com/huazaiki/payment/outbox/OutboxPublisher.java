package com.huazaiki.payment.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazaiki.common.event.OutboxStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 发布器：轮询 PENDING 事件发送 Kafka，失败指数退避重试，超限置 FAILED。
 */
@Component
public class OutboxPublisher {

    private static final int MAX_RETRY = 5;

    private final OutboxMapper outboxMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxMapper outboxMapper,
                           KafkaTemplate<String, Object> kafkaTemplate,
                           ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void publishPending() {
        List<Outbox> pending = outboxMapper.selectList(new LambdaQueryWrapper<Outbox>()
                .eq(Outbox::getStatus, OutboxStatus.PENDING)
                .and(w -> w.isNull(Outbox::getNextRetryAt).or().le(Outbox::getNextRetryAt, LocalDateTime.now()))
                .last("LIMIT 100"));
        for (Outbox outbox : pending) {
            try {
                Map<String, Object> payload = objectMapper.readValue(
                        outbox.getPayload(), new TypeReference<Map<String, Object>>() {});
                kafkaTemplate.send(outbox.getEventType(), outbox.getIdempotencyKey(), payload)
                        .get(5, TimeUnit.SECONDS);
                outbox.setStatus(OutboxStatus.PUBLISHED);
                outbox.setPublishedAt(LocalDateTime.now());
                outbox.setLastError(null);
                outboxMapper.updateById(outbox);
            } catch (Exception e) {
                int retry = outbox.getRetryCount() + 1;
                outbox.setRetryCount(retry);
                outbox.setLastError(e.getMessage());
                if (retry >= MAX_RETRY) {
                    outbox.setStatus(OutboxStatus.FAILED);
                } else {
                    outbox.setStatus(OutboxStatus.PENDING);
                    outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(60, 1L << retry)));
                }
                outboxMapper.updateById(outbox);
            }
        }
    }
}