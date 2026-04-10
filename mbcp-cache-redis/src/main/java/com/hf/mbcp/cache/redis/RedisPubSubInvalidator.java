package com.hf.mbcp.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hf.mbcp.api.InvalidationMessage;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 基于 Redis Pub/Sub 的 L1 缓存失效广播。
 * <ul>
 *   <li>发布：写操作后向 Topic 发送 {@link InvalidationMessage}</li>
 *   <li>订阅：所有节点监听 Topic，收到后清除本地 L1 对应 key</li>
 *   <li>去重：维护 LRU 窗口（最近 1000 条 messageId）防止重复处理</li>
 * </ul>
 */
public class RedisPubSubInvalidator {

    private static final Logger log = LoggerFactory.getLogger(RedisPubSubInvalidator.class);
    private static final int DEDUP_WINDOW = 1000;

    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String nodeId;

    /** LRU 去重窗口：key = sourceNodeId + ":" + timestamp */
    private final Map<String, Boolean> dedupWindow = Collections.synchronizedMap(
            new LinkedHashMap<>(DEDUP_WINDOW, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > DEDUP_WINDOW;
                }
            }
    );

    public RedisPubSubInvalidator(RedissonClient redisson, ObjectMapper objectMapper,
                                   String topic, String nodeId) {
        this.redisson = redisson;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.nodeId = nodeId;
    }

    /** 发布失效消息（写端调用） */
    public void publish(InvalidationMessage message) {
        try {
            RTopic rTopic = redisson.getTopic(topic);
            String json = objectMapper.writeValueAsString(message);
            rTopic.publish(json);
            log.debug("[MBCP-Broadcast] published, keys={}, tables={}", message.cacheKeys(), message.tableNames());
        } catch (Exception e) {
            log.warn("[MBCP-Broadcast] publish failed", e);
        }
    }

    /**
     * 订阅失效消息（应用启动时调用）。
     * @param l1Evict 收到消息后执行的 L1 清除逻辑
     */
    public void subscribe(Consumer<InvalidationMessage> l1Evict) {
        RTopic rTopic = redisson.getTopic(topic);
        rTopic.addListener(String.class, (channel, json) -> {
            try {
                InvalidationMessage msg = objectMapper.readValue(json, InvalidationMessage.class);
                // 去重：相同 sourceNodeId + timestamp 的消息只处理一次
                String dedupKey = msg.sourceNodeId() + ":" + msg.timestamp();
                if (dedupWindow.putIfAbsent(dedupKey, Boolean.TRUE) != null) {
                    log.trace("[MBCP-Broadcast] dedup skip, key={}", dedupKey);
                    return;
                }
                log.debug("[MBCP-Broadcast] received from {}, keys={}, tables={}",
                        msg.sourceNodeId(), msg.cacheKeys(), msg.tableNames());
                l1Evict.accept(msg);
            } catch (Exception e) {
                log.warn("[MBCP-Broadcast] process message failed, json={}", json, e);
            }
        });
        log.info("[MBCP-Broadcast] subscribed to topic={}", topic);
    }
}
