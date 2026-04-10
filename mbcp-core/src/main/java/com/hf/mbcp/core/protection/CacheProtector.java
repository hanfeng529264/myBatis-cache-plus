package com.hf.mbcp.core.protection;

import com.hf.mbcp.api.CacheLock;
import com.hf.mbcp.api.CacheProvider;
import com.hf.mbcp.api.NullValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存三大防护机制：
 * <ul>
 *   <li><b>防穿透</b>：DB 返回 null 时存入 {@link NullValue#INSTANCE} 短 TTL 占位符</li>
 *   <li><b>防雪崩</b>：对 TTL 添加随机偏移（±offsetRatio），错开集中过期时间</li>
 *   <li><b>防击穿</b>：L2 miss 时加 key 级别互斥锁 + 双重检查，只允许一个线程查 DB</li>
 * </ul>
 */
public class CacheProtector {

    private static final Logger log = LoggerFactory.getLogger(CacheProtector.class);

    private final boolean preventPenetration;
    private final boolean preventAvalanche;
    private final boolean preventBreakdown;
    private final double avalancheOffsetRatio;   // 随机偏移范围（如 0.10 = ±10%）
    private final long nullValueTtlSeconds;
    private final long lockWaitTimeMs;
    private final Supplier<CacheLock> lockFactory;  // 提供 key 级别的锁

    public CacheProtector(boolean preventPenetration, boolean preventAvalanche,
                           boolean preventBreakdown, double avalancheOffsetRatio,
                           long nullValueTtlSeconds, long lockWaitTimeMs,
                           Supplier<CacheLock> lockFactory) {
        this.preventPenetration = preventPenetration;
        this.preventAvalanche = preventAvalanche;
        this.preventBreakdown = preventBreakdown;
        this.avalancheOffsetRatio = avalancheOffsetRatio;
        this.nullValueTtlSeconds = nullValueTtlSeconds;
        this.lockWaitTimeMs = lockWaitTimeMs;
        this.lockFactory = lockFactory;
    }

    /**
     * 带防击穿保护的缓存读取（L2 miss 时加锁双重检查）。
     * @param key        缓存 key
     * @param provider   L2 缓存提供者
     * @param dbLoader   DB 查询回调
     * @param ttlSeconds 写缓存的 TTL
     * @return 查询结果（null 表示 DB 也没有）
     */
    public Object loadWithBreakdownProtection(String key, CacheProvider provider,
                                               Supplier<Object> dbLoader, long ttlSeconds) {
        // 先检查 L2
        Optional<Object> cached = provider.get(key);
        if (cached.isPresent()) {
            return unwrap(cached.get());
        }

        if (!preventBreakdown) {
            // 不防击穿，直接查 DB
            return loadAndCache(key, provider, dbLoader, ttlSeconds);
        }

        // 加 key 级别互斥锁
        CacheLock lock = lockFactory.get();
        boolean acquired = lock.tryLock(lockWaitTimeMs, TimeUnit.MILLISECONDS);
        if (!acquired) {
            // 等待超时：再次检查 L2（其他线程可能已写入），降级直查 DB
            Optional<Object> retry = provider.get(key);
            if (retry.isPresent()) return unwrap(retry.get());
            log.warn("[MBCP-Protect] lock timeout for key={}, fallback to direct DB query", key);
            return dbLoader.get();
        }

        try {
            // 双重检查：持锁后再次查 L2
            Optional<Object> doubleCheck = provider.get(key);
            if (doubleCheck.isPresent()) {
                return unwrap(doubleCheck.get());
            }
            return loadAndCache(key, provider, dbLoader, ttlSeconds);
        } finally {
            lock.unlock();
        }
    }

    private Object loadAndCache(String key, CacheProvider provider,
                                 Supplier<Object> dbLoader, long ttlSeconds) {
        Object result = dbLoader.get();
        long actualTtl = applyAvalancheOffset(ttlSeconds);
        if (result == null) {
            if (preventPenetration) {
                provider.put(key, NullValue.INSTANCE, nullValueTtlSeconds);
                log.debug("[MBCP-Protect] null value cached for key={}, ttl={}s", key, nullValueTtlSeconds);
            }
        } else {
            provider.put(key, result, actualTtl);
        }
        return result;
    }

    /** 解包 NullValue 占位符 */
    private Object unwrap(Object value) {
        return (value instanceof NullValue) ? null : value;
    }

    /** 对 TTL 添加随机偏移，防止雪崩 */
    public long applyAvalancheOffset(long baseTtl) {
        if (!preventAvalanche || baseTtl <= 0) return baseTtl;
        double offset = (Math.random() * 2 - 1) * avalancheOffsetRatio; // [-ratio, +ratio]
        long newTtl = (long) (baseTtl * (1 + offset));
        return Math.max(1, newTtl);
    }

    /** 判断值是否为空值占位符 */
    public static boolean isNullValue(Object value) {
        return value instanceof NullValue;
    }
}
