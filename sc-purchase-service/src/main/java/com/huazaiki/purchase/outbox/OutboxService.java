package com.huazaiki.purchase.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazaiki.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * 将业务事件写入本地 Outbox 表（与业务事务同库同事务）。
 */
@Service
public class OutboxService {

    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    public void saveEvent(String topic, String bizType, Long bizId, String idempotencyKey, Map<String, Object> payload) {
        try {
            Outbox outbox = new Outbox();
            outbox.setEventId(UUID.randomUUID().toString());
            outbox.setEventType(topic);
            outbox.setBizType(bizType);
            outbox.setBizId(bizId);
            outbox.setIdempotencyKey(idempotencyKey);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setStatus(com.huazaiki.common.event.OutboxStatus.PENDING);
            outbox.setRetryCount(0);
            outboxMapper.insert(outbox);
        } catch (JsonProcessingException e) {
            throw new BusinessException(() -> 500, "Failed to serialize outbox payload: " + e.getMessage());
        }
    }

    public Outbox findByEventId(String eventId) {
        return outboxMapper.selectOne(new LambdaQueryWrapper<Outbox>().eq(Outbox::getEventId, eventId));
    }
}