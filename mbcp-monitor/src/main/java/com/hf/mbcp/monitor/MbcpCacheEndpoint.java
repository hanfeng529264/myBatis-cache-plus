package com.hf.mbcp.monitor;

import com.hf.mbcp.api.CacheProvider;
import com.hf.mbcp.api.CacheStats;
import com.hf.mbcp.cache.local.CaffeineProvider;
import org.springframework.boot.actuate.endpoint.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Actuator 自定义端点：{@code /actuator/mbcp-cache}
 * <ul>
 *   <li>GET  — 返回所有缓存层的统计信息</li>
 *   <li>POST /evict/{key}  — 手动清除指定 key 或前缀</li>
 *   <li>DELETE — 清空所有缓存</li>
 * </ul>
 */
@Endpoint(id = "mbcp-cache")
public class MbcpCacheEndpoint {

    private final CacheProvider l1Provider;
    private final CacheProvider l2Provider;

    public MbcpCacheEndpoint(CacheProvider l1Provider, CacheProvider l2Provider) {
        this.l1Provider = l1Provider;
        this.l2Provider = l2Provider;
    }

    @ReadOperation
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("l1", statsOf(l1Provider));
        if (l2Provider != null) {
            result.put("l2", statsOf(l2Provider));
        }
        return result;
    }

    @WriteOperation
    public Map<String, Object> evict(@Selector String target) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if (target.endsWith("*")) {
                String prefix = target.substring(0, target.length() - 1);
                l1Provider.evictByPrefix(prefix);
                if (l2Provider != null) l2Provider.evictByPrefix(prefix);
                result.put("action", "evictByPrefix");
                result.put("prefix", prefix);
            } else {
                l1Provider.evict(target);
                if (l2Provider != null) l2Provider.evict(target);
                result.put("action", "evict");
                result.put("key", target);
            }
            result.put("status", "OK");
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @DeleteOperation
    public Map<String, Object> clear() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            l1Provider.clear();
            if (l2Provider != null) l2Provider.clear();
            result.put("status", "OK");
            result.put("action", "clear-all");
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
        }
        return result;
    }

    // ────── 私有工具 ──────

    private Map<String, Object> statsOf(CacheProvider provider) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", provider.getName());
        m.put("available", provider.isAvailable());
        CacheStats s = provider.getStats();
        if (s != null) {
            m.put("hitCount", s.getHitCount());
            m.put("missCount", s.getMissCount());
            m.put("evictionCount", s.getEvictionCount());
            m.put("hitRate", s.getHitRate());
        }
        // Caffeine 原生统计
        if (provider instanceof CaffeineProvider cp) {
            com.github.benmanes.caffeine.cache.stats.CacheStats cs = cp.caffeineStats();
            m.put("caffeine.estimatedSize", cp.estimatedSize());
            m.put("caffeine.loadSuccessCount", cs.loadSuccessCount());
            m.put("caffeine.loadFailureCount", cs.loadFailureCount());
            m.put("caffeine.totalLoadTime", cs.totalLoadTime());
        }
        return m;
    }
}
