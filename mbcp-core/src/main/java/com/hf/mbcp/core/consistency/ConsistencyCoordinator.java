package com.hf.mbcp.core.consistency;

import com.hf.mbcp.annotation.enums.ConsistencyLevel;
import com.hf.mbcp.api.CacheProvider;
import com.hf.mbcp.core.protection.CacheProtector;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * 一致性协调器工厂，根据级别路由到对应的 Coordinator。
 * 外部调用入口，AOP Aspect 和 MbcpInterceptor 统一通过此类操作缓存。
 */
public class ConsistencyCoordinator {

    private final LevelACoordinator levelA;
    private final LevelBCoordinator levelB;
    private final LevelCCoordinator levelC;
    private final LevelDCoordinator levelD;
    private final ConsistencyLevel defaultLevel;

    public ConsistencyCoordinator(CacheProvider cacheProvider,
                                   CacheProtector protector,
                                   Executor asyncExecutor,
                                   ScheduledExecutorService delayedEvictExecutor,
                                   long doubleEvictDelayMs,
                                   LevelDCoordinator levelDCoordinator,
                                   ConsistencyLevel defaultLevel) {
        this.levelA = new LevelACoordinator(cacheProvider, protector);
        this.levelB = new LevelBCoordinator(cacheProvider, protector, asyncExecutor);
        this.levelC = new LevelCCoordinator(cacheProvider, protector, delayedEvictExecutor, doubleEvictDelayMs);
        this.levelD = levelDCoordinator; // 可为 null（无 Redis 时）
        this.defaultLevel = defaultLevel;
    }

    // ────── 查询 ──────

    public Object executeQuery(String cacheKey, Supplier<Object> dbLoader,
                                long ttlSeconds, ConsistencyLevel level) {
        return switch (resolve(level)) {
            case IGNORE      -> levelA.executeQuery(cacheKey, dbLoader, ttlSeconds);
            case BEST_EFFORT -> levelB.executeQuery(cacheKey, dbLoader, ttlSeconds);
            case EVENTUAL    -> levelC.executeQuery(cacheKey, dbLoader, ttlSeconds);
            case STRONG      -> requireLevelD().executeQuery(cacheKey, dbLoader, ttlSeconds);
        };
    }

    // ────── 写失效 ──────

    /** Level A/B/C 写失效（多个 key + 表名维度）*/
    public void onWrite(List<String> cacheKeys, List<String> tableNames,
                        boolean doubleEvict, ConsistencyLevel level) {
        switch (resolve(level)) {
            case IGNORE      -> levelA.onWrite(cacheKeys, tableNames);
            case BEST_EFFORT -> levelB.onWrite(cacheKeys, tableNames);
            case EVENTUAL    -> levelC.onWrite(cacheKeys, tableNames, doubleEvict);
            case STRONG      -> throw new UnsupportedOperationException(
                    "Level D onWrite requires cacheKey + sqlExecutor, use onWriteStrong()");
        }
    }

    /** Level D 写失效（精确 key + write-through）*/
    public void onWriteStrong(String cacheKey, List<String> tableNames,
                              Supplier<Object> sqlExecutor, long ttlSeconds) {
        requireLevelD().onWrite(cacheKey, tableNames, sqlExecutor, ttlSeconds);
    }

    private ConsistencyLevel resolve(ConsistencyLevel level) {
        return (level == null || level == ConsistencyLevel.EVENTUAL) ? defaultLevel : level;
    }

    private LevelDCoordinator requireLevelD() {
        if (levelD == null) {
            throw new IllegalStateException(
                    "[MBCP] ConsistencyLevel.STRONG requires Redis (Redisson). " +
                    "Please configure mbcp.redis or set a lower consistency level.");
        }
        return levelD;
    }
}
