package com.hf.mbcp.core.consistency;

import com.hf.mbcp.api.CacheProvider;
import com.hf.mbcp.api.NullValue;
import com.hf.mbcp.cache.multi.MultiLevelCacheProvider;
import com.hf.mbcp.cache.redis.RedissonProvider;
import com.hf.mbcp.cache.redis.RedissonReadWriteLock;
import com.hf.mbcp.core.protection.CacheProtector;
import com.hf.mbcp.core.support.TransactionSynchronizationSupport;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Level D — 强一致。
 * <p>必须配置 Redis（Redisson ReadWriteLock）。
 *
 * <b>读流程：</b>
 * <ol>
 *   <li>获取读锁（共享，与写锁互斥）</li>
 *   <li>跳过 L1，直查 L2（避免跨节点 L1 不一致）</li>
 *   <li>L2 miss → 查 DB → write-through 写 L2</li>
 *   <li>释放读锁</li>
 * </ol>
 *
 * <b>写流程：</b>
 * <ol>
 *   <li>获取写锁（排他，持锁期间读请求等待）</li>
 *   <li>事务感知：提交后执行步骤 3-6</li>
 *   <li>删 L2 + 广播清所有节点 L1</li>
 *   <li>执行 SQL</li>
 *   <li>Write-through：将新值同步写 L2</li>
 *   <li>广播推送新值到所有节点 L1</li>
 *   <li>释放写锁 + 延迟双删</li>
 * </ol>
 */
public class LevelDCoordinator {

    private static final Logger log = LoggerFactory.getLogger(LevelDCoordinator.class);
    private static final String LOCK_PREFIX = "mbcp:rwl:";

    private final MultiLevelCacheProvider mlProvider;
    private final RedissonClient redisson;
    private final ScheduledExecutorService delayedEvictExecutor;
    private final long lockWaitTimeMs;
    private final long doubleEvictDelayMs;

    public LevelDCoordinator(MultiLevelCacheProvider mlProvider, RedissonClient redisson,
                              ScheduledExecutorService delayedEvictExecutor,
                              long lockWaitTimeMs, long doubleEvictDelayMs) {
        this.mlProvider = mlProvider;
        this.redisson = redisson;
        this.delayedEvictExecutor = delayedEvictExecutor;
        this.lockWaitTimeMs = lockWaitTimeMs;
        this.doubleEvictDelayMs = doubleEvictDelayMs;
    }

    /** Level D 读（加读锁，跳过 L1，直查 L2）*/
    public Object executeQuery(String cacheKey, Supplier<Object> dbLoader, long ttlSeconds) {
        RedissonReadWriteLock rwLock = new RedissonReadWriteLock(
                redisson.getReadWriteLock(LOCK_PREFIX + cacheKey));
        var readLock = rwLock.readLock();
        boolean acquired = readLock.tryLock(lockWaitTimeMs, TimeUnit.MILLISECONDS);
        if (!acquired) {
            log.warn("[MBCP-D] read lock timeout for key={}, fallback direct query", cacheKey);
            return dbLoader.get();
        }
        try {
            // 跳过 L1，直查 L2（唯一权威来源）
            Optional<Object> l2 = mlProvider.getL2().get(cacheKey);
            if (l2.isPresent()) {
                Object val = l2.get();
                if (CacheProtector.isNullValue(val)) return null;
                // 可选：将结果写入本节点 L1（极短 TTL = 3s，热点缓冲）
                mlProvider.getL1().put(cacheKey, val, 3);
                return val;
            }
            // L2 miss → 查 DB → write-through 写 L2
            Object result = dbLoader.get();
            if (result != null) {
                mlProvider.getL2().put(cacheKey, result, ttlSeconds);
                mlProvider.getL1().put(cacheKey, result, 3);
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Level D 写（加写锁 + 事务感知 + write-through）。
     * @param cacheKey   单个精确 key（Level D 要求精确 key）
     * @param tableNames 涉及的表名（用于广播）
     * @param newValue   DB 操作完成后的新值（write-through 写入）
     * @param ttlSeconds 写入 L2 的 TTL
     */
    public void onWrite(String cacheKey, List<String> tableNames,
                        Supplier<Object> sqlExecutor, long ttlSeconds) {
        RedissonReadWriteLock rwLock = new RedissonReadWriteLock(
                redisson.getReadWriteLock(LOCK_PREFIX + cacheKey));
        var writeLock = rwLock.writeLock();
        boolean acquired = writeLock.tryLock(lockWaitTimeMs, TimeUnit.MILLISECONDS);
        if (!acquired) {
            throw new RuntimeException("[MBCP-D] write lock timeout for key=" + cacheKey +
                    ", Level D cannot degrade silently");
        }
        try {
            // 事务感知：提交后执行实际的缓存操作
            TransactionSynchronizationSupport.executeWithRollbackAware(
                    // onCommit：删 L2 → 执行 SQL → write-through → 广播
                    () -> {
                        // 1. 删 L2
                        mlProvider.getL2().evict(cacheKey);
                        // 2. 广播清所有节点 L1
                        mlProvider.getBroadcaster().broadcast(List.of(cacheKey), tableNames);
                        mlProvider.getL1().evict(cacheKey);
                        // 3. 执行 SQL（write-through：获取新值）
                        Object newValue = sqlExecutor.get();
                        // 4. write-through 写 L2
                        if (newValue != null) {
                            mlProvider.getL2().put(cacheKey, newValue, ttlSeconds);
                            // 5. 广播推送新值到其他节点 L1（清除让其重新加载）
                            mlProvider.getBroadcaster().broadcast(List.of(cacheKey), List.of());
                        }
                        // 6. 延迟双删
                        scheduleDoubleEvict(cacheKey, tableNames);
                    },
                    // onRollback：只删缓存，不 write-through
                    () -> {
                        mlProvider.getL2().evict(cacheKey);
                        mlProvider.getBroadcaster().broadcast(List.of(cacheKey), tableNames);
                        mlProvider.getL1().evict(cacheKey);
                    }
            );
        } finally {
            writeLock.unlock();
        }
    }

    private void scheduleDoubleEvict(String cacheKey, List<String> tableNames) {
        try {
            delayedEvictExecutor.schedule(() -> {
                mlProvider.getL2().evict(cacheKey);
                mlProvider.getBroadcaster().broadcast(List.of(cacheKey), tableNames);
                mlProvider.getL1().evict(cacheKey);
            }, doubleEvictDelayMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("[MBCP-D] delayed evict task dropped: {}", e.getMessage());
        }
    }
}
