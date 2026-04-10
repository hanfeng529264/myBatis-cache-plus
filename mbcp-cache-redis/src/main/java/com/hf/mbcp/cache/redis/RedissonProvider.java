package com.hf.mbcp.cache.redis;

import com.hf.mbcp.api.CacheProvider;
import com.hf.mbcp.api.CacheStats;
import com.hf.mbcp.api.NullValue;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson RMapCache 的分布式缓存（L2）实现。
 * <ul>
 *   <li>使用 RMapCache 支持 per-key TTL</li>
 *   <li>key 前缀统一为 {@code mbcp:cache:}</li>
 *   <li>evictByPrefix 通过 Redis SCAN 分批删除（每批 200 条）</li>
 * </ul>
 */
public class RedissonProvider implements CacheProvider {

    private static final Logger log = LoggerFactory.getLogger(RedissonProvider.class);
    private static final String MAP_NAME = "mbcp:cache";

    private final RedissonClient redisson;
    private final CacheStats stats = new CacheStats();
    private final String keyPrefix;

    public RedissonProvider(RedissonClient redisson, String keyPrefix) {
        this.redisson = redisson;
        this.keyPrefix = keyPrefix != null ? keyPrefix : "";
    }

    @Override
    public String getName() {
        return "redisson-l2";
    }

    @Override
    public Optional<Object> get(String key) {
        try {
            RMapCache<String, Object> map = redisson.getMapCache(MAP_NAME);
            Object value = map.get(prefixed(key));
            if (value == null) {
                stats.recordMiss();
                return Optional.empty();
            }
            stats.recordHit();
            return Optional.of(value);
        } catch (Exception e) {
            log.warn("[MBCP-L2] get failed, key={}", key, e);
            stats.recordMiss();
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, Object value, long ttlSeconds) {
        try {
            RMapCache<String, Object> map = redisson.getMapCache(MAP_NAME);
            if (ttlSeconds > 0) {
                map.put(prefixed(key), value, ttlSeconds, TimeUnit.SECONDS);
            } else {
                map.put(prefixed(key), value);
            }
        } catch (Exception e) {
            log.warn("[MBCP-L2] put failed, key={}", key, e);
        }
    }

    @Override
    public void evict(String key) {
        try {
            RMapCache<String, Object> map = redisson.getMapCache(MAP_NAME);
            map.remove(prefixed(key));
        } catch (Exception e) {
            log.warn("[MBCP-L2] evict failed, key={}", key, e);
        }
    }

    @Override
    public void evictByPrefix(String prefix) {
        try {
            String pattern = MAP_NAME + ":{" + keyPrefix + prefix + "*}";
            // 使用 RKeys SCAN 分批删除，每批 200 条，避免阻塞 Redis 主线程
            Iterable<String> keys = redisson.getKeys().getKeysByPattern(pattern, 200);
            for (String k : keys) {
                redisson.getKeys().delete(k);
            }
        } catch (Exception e) {
            log.warn("[MBCP-L2] evictByPrefix failed, prefix={}", prefix, e);
        }
    }

    @Override
    public void clear() {
        try {
            RMapCache<String, Object> map = redisson.getMapCache(MAP_NAME);
            map.clear();
        } catch (Exception e) {
            log.warn("[MBCP-L2] clear failed", e);
        }
    }

    @Override
    public CacheStats getStats() {
        return stats;
    }

    @Override
    public boolean isAvailable() {
        try {
            redisson.getKeys().count();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * write-through 语义：将新值写入并广播（Level D 使用）。
     * 与 put 相比，返回旧值供调用者感知变化。
     */
    public Object getAndPut(String key, Object value, long ttlSeconds) {
        try {
            RMapCache<String, Object> map = redisson.getMapCache(MAP_NAME);
            if (ttlSeconds > 0) {
                return map.put(prefixed(key), value, ttlSeconds, TimeUnit.SECONDS);
            }
            return map.put(prefixed(key), value);
        } catch (Exception e) {
            log.warn("[MBCP-L2] getAndPut failed, key={}", key, e);
            return null;
        }
    }

    private String prefixed(String key) {
        return keyPrefix.isEmpty() ? key : keyPrefix + key;
    }

    public RedissonClient getRedisson() {
        return redisson;
    }
}
