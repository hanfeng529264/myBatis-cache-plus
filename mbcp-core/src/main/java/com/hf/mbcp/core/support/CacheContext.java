package com.hf.mbcp.core.support;

import com.hf.mbcp.api.ShardInfo;

/**
 * 请求级别 ThreadLocal 上下文。
 * <ul>
 *   <li>isAnnotationHandled：AOP 注解已处理，MbcpInterceptor 应跳过</li>
 *   <li>currentShardInfo：当前请求的分片路由信息（分库分表场景）</li>
 * </ul>
 */
public final class CacheContext {

    private static final ThreadLocal<Boolean> ANNOTATION_HANDLED = new ThreadLocal<>();
    private static final ThreadLocal<ShardInfo> SHARD_INFO = new ThreadLocal<>();

    private CacheContext() {}

    public static void setAnnotationHandled(boolean handled) {
        if (handled) ANNOTATION_HANDLED.set(Boolean.TRUE);
        else ANNOTATION_HANDLED.remove();
    }

    public static boolean isAnnotationHandled() {
        return Boolean.TRUE.equals(ANNOTATION_HANDLED.get());
    }

    public static void setShardInfo(ShardInfo shardInfo) {
        if (shardInfo != null) SHARD_INFO.set(shardInfo);
        else SHARD_INFO.remove();
    }

    public static ShardInfo getShardInfo() {
        return SHARD_INFO.get();
    }

    /** 清理当前线程的所有上下文（在 AOP finally 块中调用） */
    public static void clear() {
        ANNOTATION_HANDLED.remove();
        SHARD_INFO.remove();
    }
}
