package com.hf.mbcp.cache.local;

import com.github.benmanes.caffeine.cache.Weigher;
import com.hf.mbcp.api.CacheEntry;
import com.hf.mbcp.api.NullValue;

import java.util.Collection;

/**
 * Caffeine 缓存条目权重估算器。
 * 按内存字节数而非条目数控制上限，防止大对象 OOM。
 */
public class ObjectSizeWeigher implements Weigher<String, CacheEntry> {

    private static final int KEY_OVERHEAD = 64;
    private static final int ENTRY_OVERHEAD = 256; // CacheEntry 对象 + Caffeine 节点开销

    @Override
    public int weigh(String key, CacheEntry entry) {
        int keyBytes = key.length() * 2 + KEY_OVERHEAD; // Java String: 2 bytes/char
        int valueBytes = estimateValueSize(entry.getValue());
        return Math.min(keyBytes + valueBytes + ENTRY_OVERHEAD, Integer.MAX_VALUE);
    }

    private int estimateValueSize(Object value) {
        if (value == null || value instanceof NullValue) return 8;
        if (value instanceof String s) return s.length() * 2 + 40;
        if (value instanceof byte[] b) return b.length + 16;
        if (value instanceof Collection<?> c) return c.size() * 128 + 64;
        if (value instanceof Number) return 24;
        // 其他对象：保守估算 256 字节；若实现了 Measurable 接口则使用自报值
        if (value instanceof Measurable m) return m.sizeInBytes();
        return 256;
    }

    /** 允许业务对象自报内存大小，用于精确计量 */
    public interface Measurable {
        int sizeInBytes();
    }
}
