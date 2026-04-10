package com.hf.mbcp.api;

/**
 * 缓存条目包装类，携带过期时间，支持 per-entry TTL。
 * Caffeine 使用 expireAfter(Expiry) 从此对象读取 TTL。
 */
public final class CacheEntry {

    private final Object value;
    private final long expireAtMs; // 绝对过期时间戳（毫秒）

    public CacheEntry(Object value, long ttlSeconds) {
        this.value = value;
        this.expireAtMs = ttlSeconds > 0
                ? System.currentTimeMillis() + ttlSeconds * 1000L
                : Long.MAX_VALUE; // 永不过期
    }

    public Object getValue() {
        return value;
    }

    public long getExpireAtMs() {
        return expireAtMs;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireAtMs;
    }

    /** 距过期的剩余纳秒数（用于 Caffeine Expiry 接口） */
    public long remainingNanos() {
        long remainMs = expireAtMs - System.currentTimeMillis();
        return Math.max(0L, remainMs * 1_000_000L);
    }
}
