package com.hf.mbcp.core.consistency;

import com.hf.mbcp.api.CacheProvider;
import com.hf.mbcp.api.NullValue;
import com.hf.mbcp.cache.multi.MultiLevelCacheProvider;
import com.hf.mbcp.core.protection.CacheProtector;
import com.hf.mbcp.core.support.TransactionSynchronizationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Level C — 最终一致（默认）。
 * 写流程：
 * <ol>
 *   <li>事务感知：在事务 afterCommit() 后执行，非事务则立即执行</li>
 *   <li>同步删 L2</li>
 *   <li>广播清除所有节点 L1（Redis Pub/Sub，延迟 &lt; 50ms）</li>
 *   <li>提交延迟双删任务到有界线程池（默认 200ms 后再次删 L2 + 广播 L1）</li>
 * </ol>
 */
public class LevelCCoordinator {

    private static final Logger log = LoggerFactory.getLogger(LevelCCoordinator.class);

    private final CacheProvider cacheProvider;
    private final CacheProtector protector;
    private final ScheduledExecutorService delayedEvictExecutor;
    private final long doubleEvictDelayMs;

    public LevelCCoordinator(CacheProvider cacheProvider, CacheProtector protector,
                              ScheduledExecutorService delayedEvictExecutor,
                              long doubleEvictDelayMs) {
        this.cacheProvider = cacheProvider;
        this.protector = protector;
        this.delayedEvictExecutor = delayedEvictExecutor;
        this.doubleEvictDelayMs = doubleEvictDelayMs;
    }

    public Object executeQuery(String cacheKey, Supplier<Object> dbLoader, long ttlSeconds) {
        Optional<Object> cached = cacheProvider.get(cacheKey);
        if (cached.isPresent()) {
            Object val = cached.get();
            if (CacheProtector.isNullValue(val)) return null;
            return val;
        }
        return protector.loadWithBreakdownProtection(cacheKey, cacheProvider, dbLoader, ttlSeconds);
    }

    /** 写操作触发缓存失效（事务感知 + 延迟双删） */
    public void onWrite(List<String> cacheKeys, List<String> tableNames, boolean doubleEvict) {
        // 事务感知：提交后执行
        TransactionSynchronizationSupport.executeAfterCommitOrNow(() ->
                doEvict(cacheKeys, tableNames, doubleEvict));
    }

    private void doEvict(List<String> cacheKeys, List<String> tableNames, boolean doubleEvict) {
        // 第一次清除
        performEvict(cacheKeys, tableNames);
        // 延迟双删（处理主从同步延迟窗口）
        if (doubleEvict && doubleEvictDelayMs > 0) {
            try {
                delayedEvictExecutor.schedule(
                        () -> performEvict(cacheKeys, tableNames),
                        doubleEvictDelayMs, TimeUnit.MILLISECONDS
                );
            } catch (Exception e) {
                // 有界队列满时任务被丢弃，记录告警（第一次已执行，可接受）
                log.warn("[MBCP-C] delayed evict task dropped: {}", e.getMessage());
            }
        }
    }

    private void performEvict(List<String> cacheKeys, List<String> tableNames) {
        try {
            // 对 MultiLevelCacheProvider 特殊处理：evict 内部会广播
            if (cacheProvider instanceof MultiLevelCacheProvider mlp) {
                cacheKeys.forEach(mlp::evict);
                tableNames.forEach(t -> mlp.evictByPrefix(t + ":"));
            } else {
                cacheKeys.forEach(cacheProvider::evict);
                tableNames.forEach(t -> cacheProvider.evictByPrefix(t + ":"));
            }
        } catch (Exception e) {
            log.warn("[MBCP-C] performEvict failed: {}", e.getMessage());
        }
    }
}
