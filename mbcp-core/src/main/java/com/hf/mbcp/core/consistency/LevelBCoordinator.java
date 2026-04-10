package com.hf.mbcp.core.consistency;

import com.hf.mbcp.api.CacheProvider;
import com.hf.mbcp.api.NullValue;
import com.hf.mbcp.cache.multi.MultiLevelCacheProvider;
import com.hf.mbcp.core.protection.CacheProtector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Level B — 尽力一致。
 * <ul>
 *   <li>写操作：立即清除自身 L1，<b>异步</b>删除 L2，不广播其他节点</li>
 *   <li>其他节点 L1：靠 TTL 自然过期（脏读窗口 = L1 TTL）</li>
 * </ul>
 */
public class LevelBCoordinator {

    private static final Logger log = LoggerFactory.getLogger(LevelBCoordinator.class);

    private final CacheProvider cacheProvider;
    private final CacheProtector protector;
    private final Executor asyncExecutor;

    public LevelBCoordinator(CacheProvider cacheProvider, CacheProtector protector,
                              Executor asyncExecutor) {
        this.cacheProvider = cacheProvider;
        this.protector = protector;
        this.asyncExecutor = asyncExecutor;
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

    public void onWrite(List<String> cacheKeys, List<String> tableNames) {
        // 立即清除 L1（自身节点）
        if (cacheProvider instanceof MultiLevelCacheProvider mlp) {
            cacheKeys.forEach(mlp.getL1()::evict);
            tableNames.forEach(t -> mlp.getL1().evictByPrefix(t + ":"));
        } else {
            cacheKeys.forEach(cacheProvider::evict);
        }
        // 异步删除 L2（失败不影响业务）
        asyncExecutor.execute(() -> {
            try {
                cacheKeys.forEach(cacheProvider::evict);
                tableNames.forEach(t -> cacheProvider.evictByPrefix(t + ":"));
            } catch (Exception e) {
                log.warn("[MBCP-B] async L2 evict failed: {}", e.getMessage());
            }
        });
    }
}
