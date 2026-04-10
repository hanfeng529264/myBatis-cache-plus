package com.hf.mbcp.api;

import java.util.Optional;

/**
 * 缓存后端统一抽象接口（SPI）。
 * 实现类：CaffeineProvider（L1）、RedissonProvider（L2）、MultiLevelCacheProvider（hybrid）、NoOpCacheProvider（none）
 */
public interface CacheProvider {

    /** 缓存名称，用于区分不同实现和监控标签 */
    String getName();

    /**
     * 查询缓存。
     * @return Optional.empty() 表示 miss；Optional.of(NullValue.INSTANCE) 表示缓存了空值
     */
    Optional<Object> get(String key);

    /**
     * 写入缓存。
     * @param ttlSeconds 过期时间（秒），≤0 表示使用默认 TTL
     */
    void put(String key, Object value, long ttlSeconds);

    /** 删除单个 key */
    void evict(String key);

    /**
     * 按前缀批量删除（如 "com.example.UserMapper:selectById:"）。
     * 实现需确保原子性或分批执行，避免阻塞
     */
    void evictByPrefix(String prefix);

    /** 清空所有缓存（谨慎调用） */
    void clear();

    /** 返回缓存统计数据（命中/失效等） */
    CacheStats getStats();

    /**
     * 当前是否可用（用于熔断器检测）。
     * 本地缓存始终返回 true；Redis 实现在连接异常时返回 false
     */
    default boolean isAvailable() {
        return true;
    }
}
