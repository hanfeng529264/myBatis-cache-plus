package com.hf.mbcp.api;

import java.util.List;

/**
 * 集群节点间的缓存失效广播消息。
 * 通过 Redis Pub/Sub 或 Stream 传播，所有节点收到后清除对应 L1 条目。
 */
public record InvalidationMessage(
        /** 消息来源节点 ID（hostname:port:random6），用于去重 */
        String sourceNodeId,
        /** 需要精确失效的 key 列表（可为空） */
        List<String> cacheKeys,
        /** 需要按前缀失效的逻辑表名列表（可为空） */
        List<String> tableNames,
        /** 消息发布时间戳（毫秒）*/
        long timestamp
) {
    public static InvalidationMessage ofKeys(String sourceNodeId, List<String> cacheKeys) {
        return new InvalidationMessage(sourceNodeId, cacheKeys, List.of(), System.currentTimeMillis());
    }

    public static InvalidationMessage ofTables(String sourceNodeId, List<String> tableNames) {
        return new InvalidationMessage(sourceNodeId, List.of(), tableNames, System.currentTimeMillis());
    }

    public static InvalidationMessage of(String sourceNodeId, List<String> cacheKeys, List<String> tableNames) {
        return new InvalidationMessage(sourceNodeId, cacheKeys, tableNames, System.currentTimeMillis());
    }
}
