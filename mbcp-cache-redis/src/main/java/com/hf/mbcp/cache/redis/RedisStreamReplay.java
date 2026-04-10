package com.hf.mbcp.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hf.mbcp.api.InvalidationMessage;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.RStream;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Level D 可靠广播：Redis Stream 持久化 + 断线重播。
 * <p>在 Pub/Sub 基础上补充 Stream，节点重启后从上次消费位置继续处理。
 */
public class RedisStreamReplay {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamReplay.class);
    private static final String STREAM_KEY = "mbcp:stream:invalidate";
    private static final String CONSUMER_GROUP = "mbcp-consumers";
    private static final String FIELD_MSG = "msg";

    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;
    private final String nodeId;

    public RedisStreamReplay(RedissonClient redisson, ObjectMapper objectMapper, String nodeId) {
        this.redisson = redisson;
        this.objectMapper = objectMapper;
        this.nodeId = nodeId;
        initConsumerGroup();
    }

    private void initConsumerGroup() {
        try {
            RStream<String, String> stream = redisson.getStream(STREAM_KEY);
            stream.createGroupAsync(StreamCreateGroupArgs.name(CONSUMER_GROUP).id(StreamMessageId.NEWEST));
        } catch (Exception e) {
            // consumer group 可能已存在，忽略
            log.debug("[MBCP-Stream] consumer group init: {}", e.getMessage());
        }
    }

    /** 写端：将失效消息追加到 Stream（同时也发布 Pub/Sub，二者并行） */
    public void append(InvalidationMessage message) {
        try {
            RStream<String, String> stream = redisson.getStream(STREAM_KEY);
            String json = objectMapper.writeValueAsString(message);
            stream.addAsync(StreamAddArgs.entry(FIELD_MSG, json));
        } catch (Exception e) {
            log.warn("[MBCP-Stream] append failed", e);
        }
    }

    /** 读端：消费自上次下线后积累的未处理消息（节点启动时调用） */
    public void replay(Consumer<InvalidationMessage> handler) {
        try {
            RStream<String, String> stream = redisson.getStream(STREAM_KEY);
            // 读取该 consumer group 中待处理的消息（pending 状态）
            var messages = stream.readGroup(CONSUMER_GROUP, nodeId,
                    StreamReadGroupArgs.neverDelivered().count(1000));
            if (messages != null && !messages.isEmpty()) {
                log.info("[MBCP-Stream] replaying {} pending messages for node={}", messages.size(), nodeId);
                for (var entry : messages.entrySet()) {
                    try {
                        String json = entry.getValue().get(FIELD_MSG);
                        if (json != null) {
                            InvalidationMessage msg = objectMapper.readValue(json, InvalidationMessage.class);
                            handler.accept(msg);
                        }
                        // ACK 确认
                        stream.ack(CONSUMER_GROUP, entry.getKey());
                    } catch (Exception e) {
                        log.warn("[MBCP-Stream] replay message failed", e);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[MBCP-Stream] replay failed", e);
        }
    }
}
