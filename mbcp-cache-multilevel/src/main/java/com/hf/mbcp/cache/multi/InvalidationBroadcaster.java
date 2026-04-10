package com.hf.mbcp.cache.multi;

import com.hf.mbcp.api.InvalidationMessage;

import java.util.List;

/**
 * L1 缓存失效广播接口。
 * 集群模式：RedisBroadcaster（Pub/Sub）
 * 单节点/无 Redis 模式：NoOpBroadcaster
 */
public interface InvalidationBroadcaster {

    /**
     * 向集群所有节点广播失效消息（含发布节点自身）。
     * @param cacheKeys  精确 key 列表
     * @param tableNames 逻辑表名列表（前缀批量失效）
     */
    void broadcast(List<String> cacheKeys, List<String> tableNames);

    /**
     * 订阅失效消息（应用启动时调用）。
     * @param handler 收到消息后清除对应 L1 key 的回调
     */
    void subscribe(java.util.function.Consumer<InvalidationMessage> handler);
}
