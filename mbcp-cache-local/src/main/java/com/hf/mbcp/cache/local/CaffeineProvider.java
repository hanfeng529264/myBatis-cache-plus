package com.hf.mbcp.cache.local;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.hf.mbcp.annotation.enums.ExpireStrategy;
import com.hf.mbcp.api.CacheEntry;
import com.hf.mbcp.api.CacheProvider;
import com.hf.mbcp.api.CacheStats;
import com.hf.mbcp.api.model.EvictCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Caffeine 的本地缓存（L1）实现。
 * <ul>
 *   <li>支持 per-entry TTL（通过 {@link Expiry} + {@link CacheEntry} 包装）</li>
 *   <li>按内存字节数限制（maximumWeight + {@link ObjectSizeWeigher}）</li>
 *   <li>开启 recordStats() 供 Micrometer 消费</li>
 * </ul>
 */
public class CaffeineProvider implements CacheProvider {

    private static final Logger log = LoggerFactory.getLogger(CaffeineProvider.class);

    private final Cache<String, CacheEntry> cache;
    private final CacheStats stats = new CacheStats();

    /** 用于 evictByPrefix 的辅助索引（key → key），Caffeine 原生不支持前缀扫描 */
    private final ConcurrentHashMap<String, Boolean> keyIndex = new ConcurrentHashMap<>(1024);

    private volatile long maxWeightBytes;

    public CaffeineProvider(LocalCacheProperties props) {
        this.maxWeightBytes = (long) props.getMaxMemoryMb() * 1024 * 1024;

        Caffeine<String, CacheEntry> builder = Caffeine.newBuilder()
                .maximumWeight(maxWeightBytes)
                .weigher(new ObjectSizeWeigher())
                // per-entry TTL：从 CacheEntry.remainingNanos() 读取
                .expireAfter(new Expiry<String, CacheEntry>() {
                    @Override
                    public long expireAfterCreate(String key, CacheEntry value, long currentTime) {
                        return value.remainingNanos();
                    }
                    @Override
                    public long expireAfterUpdate(String key, CacheEntry value, long currentTime, long currentDuration) {
                        return value.remainingNanos();
                    }
                    @Override
                    public long expireAfterRead(String key, CacheEntry value, long currentTime, long currentDuration) {
                        // ACCESS 策略：读后重置计时
                        if (props.getExpireStrategy() == ExpireStrategy.ACCESS) {
                            return TimeUnit.SECONDS.toNanos(props.getExpireAfterAccess());
                        }
                        return currentDuration;
                    }
                })
                .recordStats()
                .removalListener((String key, CacheEntry value, RemovalCause cause) -> {
                    if (key != null) {
                        keyIndex.remove(key);
                        stats.recordEviction();
                        log.trace("[MBCP-L1] evict key={}, cause={}", key, cause);
                    }
                });

        this.cache = builder.build();
    }

    @Override
    public String getName() {
        return "caffeine-l1";
    }

    @Override
    public Optional<Object> get(String key) {
        CacheEntry entry = cache.getIfPresent(key);
        if (entry == null) {
            stats.recordMiss();
            return Optional.empty();
        }
        stats.recordHit();
        return Optional.of(entry.getValue());
    }

    @Override
    public void put(String key, Object value, long ttlSeconds) {
        CacheEntry entry = new CacheEntry(value, ttlSeconds);
        cache.put(key, entry);
        keyIndex.put(key, Boolean.TRUE);
    }

    @Override
    public void evict(String key) {
        cache.invalidate(key);
        keyIndex.remove(key);
    }

    @Override
    public void evictByPrefix(String prefix) {
        // 遍历 keyIndex 找到所有匹配前缀的 key，批量失效
        Iterator<String> it = keyIndex.keySet().iterator();
        while (it.hasNext()) {
            String k = it.next();
            if (k.startsWith(prefix)) {
                cache.invalidate(k);
                it.remove();
            }
        }
    }

    @Override
    public void clear() {
        cache.invalidateAll();
        keyIndex.clear();
    }

    @Override
    public CacheStats getStats() {
        return stats;
    }

    /** 获取 Caffeine 内置统计（用于 Micrometer 指标，含命中率等精确数据） */
    public com.github.benmanes.caffeine.cache.stats.CacheStats caffeineStats() {
        return cache.stats();
    }

    /** 当前缓存条目数 */
    public long estimatedSize() {
        return cache.estimatedSize();
    }

    /** 动态调整内存上限（JVM 内存压感知收缩时调用） */
    public synchronized void resizeMaxWeight(long newMaxBytes) {
        this.maxWeightBytes = newMaxBytes;
        // Caffeine 不支持运行时修改 maximumWeight，
        // 通过手动触发清理来间接收缩（驱逐超出部分）
        cache.cleanUp();
        log.info("[MBCP-L1] resize maxWeight to {} bytes", newMaxBytes);
    }

    public long getMaxWeightBytes() {
        return maxWeightBytes;
    }
}
