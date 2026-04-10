package com.hf.mbcp.cache.multi;

import com.hf.mbcp.api.*;
import com.hf.mbcp.cache.local.CaffeineProvider;
import com.hf.mbcp.cache.redis.RedissonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * L1 + L2 多级缓存组合（hybrid 模式）。
 * <pre>
 * 读：L1 → [熔断保护] L2 → DB（由上层调用）
 * 写/删：L2 删除 + 广播 L1 失效
 * </pre>
 */
public class MultiLevelCacheProvider implements CacheProvider {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelCacheProvider.class);

    private final CaffeineProvider l1;
    private final RedissonProvider l2;
    private final InvalidationBroadcaster broadcaster;
    private final CircuitBreaker circuitBreaker;
    private final Executor asyncExecutor;

    /** L1 TTL 相对 L2 的比例（确保 L1 先于 L2 过期） */
    private final double l1TtlRatio;

    public MultiLevelCacheProvider(CaffeineProvider l1, RedissonProvider l2,
                                    InvalidationBroadcaster broadcaster,
                                    CircuitBreaker circuitBreaker,
                                    Executor asyncExecutor,
                                    double l1TtlRatio) {
        this.l1 = l1;
        this.l2 = l2;
        this.broadcaster = broadcaster;
        this.circuitBreaker = circuitBreaker;
        this.asyncExecutor = asyncExecutor;
        this.l1TtlRatio = l1TtlRatio;
    }

    @Override
    public String getName() { return "multi-level"; }

    @Override
    public Optional<Object> get(String key) {
        // 1. 查 L1
        Optional<Object> l1Result = l1.get(key);
        if (l1Result.isPresent()) {
            return l1Result;
        }
        // 2. 查 L2（熔断保护）
        return circuitBreaker.execute(
                () -> {
                    Optional<Object> l2Result = l2.get(key);
                    if (l2Result.isPresent()) {
                        // 异步回写 L1（不阻塞当前请求）
                        final Object val = l2Result.get();
                        asyncExecutor.execute(() -> l1.put(key, val, 0));
                    }
                    return l2Result;
                },
                Optional::empty   // 熔断时降级返回 miss，由上层决定是否直查 DB
        );
    }

    @Override
    public void put(String key, Object value, long ttlSeconds) {
        // 写 L2（含 TTL）
        circuitBreaker.execute(
                () -> { l2.put(key, value, ttlSeconds); return null; },
                () -> null
        );
        // 写 L1（TTL 按比例缩短，确保 L1 先于 L2 过期）
        long l1Ttl = ttlSeconds > 0 ? Math.max(1, (long)(ttlSeconds * l1TtlRatio)) : 0;
        l1.put(key, value, l1Ttl);
    }

    @Override
    public void evict(String key) {
        // 删 L2
        circuitBreaker.execute(
                () -> { l2.evict(key); return null; },
                () -> null
        );
        // 删本节点 L1 并广播其他节点
        l1.evict(key);
        broadcaster.broadcast(List.of(key), List.of());
    }

    @Override
    public void evictByPrefix(String prefix) {
        circuitBreaker.execute(
                () -> { l2.evictByPrefix(prefix); return null; },
                () -> null
        );
        l1.evictByPrefix(prefix);
        // 广播时传 prefix（各节点自行按前缀清 L1）
        broadcaster.broadcast(List.of(), List.of(prefix));
    }

    /**
     * 按逻辑表名批量失效（分库分表 / table-aware 策略）。
     * @param tableNames 逻辑表名列表
     */
    public void evictByTables(List<String> tableNames) {
        for (String table : tableNames) {
            circuitBreaker.execute(
                    () -> { l2.evictByPrefix(table + ":"); return null; },
                    () -> null
            );
            l1.evictByPrefix(table + ":");
        }
        broadcaster.broadcast(List.of(), tableNames);
    }

    @Override
    public void clear() {
        circuitBreaker.execute(
                () -> { l2.clear(); return null; },
                () -> null
        );
        l1.clear();
        broadcaster.broadcast(List.of(), List.of("__ALL__"));
    }

    @Override
    public CacheStats getStats() {
        return l1.getStats(); // 上层可分别获取 l1/l2 stats
    }

    @Override
    public boolean isAvailable() {
        return circuitBreaker.getState() != CircuitBreaker.State.OPEN;
    }

    public CaffeineProvider getL1() { return l1; }
    public RedissonProvider getL2() { return l2; }
    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public InvalidationBroadcaster getBroadcaster() { return broadcaster; }
}
