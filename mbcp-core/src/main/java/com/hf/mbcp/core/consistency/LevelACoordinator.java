package com.hf.mbcp.core.consistency;

import com.hf.mbcp.api.CacheProvider;
import com.hf.mbcp.api.NullValue;
import com.hf.mbcp.core.protection.CacheProtector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Level A — 忽略一致性。
 * <ul>
 *   <li>读：正常走缓存，miss 则查 DB 写缓存</li>
 *   <li>写：不做任何缓存操作，完全依赖 TTL 自然过期</li>
 * </ul>
 * 适合：字典表、菜单树、公告等极少变更的数据。
 */
public class LevelACoordinator {

    private static final Logger log = LoggerFactory.getLogger(LevelACoordinator.class);

    private final CacheProvider cacheProvider;
    private final CacheProtector protector;

    public LevelACoordinator(CacheProvider cacheProvider, CacheProtector protector) {
        this.cacheProvider = cacheProvider;
        this.protector = protector;
    }

    /** 带缓存的查询 */
    public Object executeQuery(String cacheKey, Supplier<Object> dbLoader, long ttlSeconds) {
        Optional<Object> cached = cacheProvider.get(cacheKey);
        if (cached.isPresent()) {
            Object val = cached.get();
            if (CacheProtector.isNullValue(val)) return null;
            return val;
        }
        // L2 miss：带防击穿保护查 DB
        return protector.loadWithBreakdownProtection(cacheKey, cacheProvider, dbLoader, ttlSeconds);
    }

    /** 写操作：不做任何缓存清理（忽略一致性）*/
    public void onWrite(List<String> cacheKeys, List<String> tableNames) {
        log.trace("[MBCP-A] write ignored, keys={}", cacheKeys);
        // 什么都不做
    }
}
