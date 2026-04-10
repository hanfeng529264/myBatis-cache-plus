package com.hf.mbcp.cache.multi;

import com.hf.mbcp.api.InvalidationMessage;
import com.hf.mbcp.cache.redis.RedisPubSubInvalidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/** 基于 Redis Pub/Sub 的失效广播实现。 */
public class RedisBroadcaster implements InvalidationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RedisBroadcaster.class);
    private final RedisPubSubInvalidator pubSub;
    private final String nodeId;

    public RedisBroadcaster(RedisPubSubInvalidator pubSub, String nodeId) {
        this.pubSub = pubSub;
        this.nodeId = nodeId;
    }

    @Override
    public void broadcast(List<String> cacheKeys, List<String> tableNames) {
        InvalidationMessage msg = InvalidationMessage.of(nodeId, cacheKeys, tableNames);
        pubSub.publish(msg);
    }

    @Override
    public void subscribe(Consumer<InvalidationMessage> handler) {
        pubSub.subscribe(handler);
    }
}
