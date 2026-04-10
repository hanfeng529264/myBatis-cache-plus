package com.hf.mbcp.cache.multi;

import com.hf.mbcp.api.CacheProvider;
import com.hf.mbcp.api.CacheStats;

import java.util.Optional;
/**
 * 空操作 CacheProvider，用于 {@code cache-type=NONE}（完全禁用缓存）。
 */
public class NoOpCacheProvider implements CacheProvider {

    @Override
    public String getName() { return "no-op"; }

    @Override
    public Optional<Object> get(String key) { return Optional.empty(); }

    @Override
    public void put(String key, Object value, long ttlSeconds) { /* no-op */ }

    @Override
    public void evict(String key) { /* no-op */ }

    @Override
    public void evictByPrefix(String prefix) { /* no-op */ }

    @Override
    public void clear() { /* no-op */ }

    @Override
    public CacheStats getStats() { return null; }

    @Override
    public boolean isAvailable() { return false; }
}
