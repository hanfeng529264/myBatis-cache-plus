package com.hf.mbcp.api;

import java.util.concurrent.atomic.LongAdder;

/** 缓存统计数据（线程安全）。 */
public class CacheStats {

    private final LongAdder hitCount = new LongAdder();
    private final LongAdder missCount = new LongAdder();
    private final LongAdder evictionCount = new LongAdder();
    private final LongAdder loadCount = new LongAdder();
    private final LongAdder loadTotalNanos = new LongAdder();

    public void recordHit() { hitCount.increment(); }
    public void recordMiss() { missCount.increment(); }
    public void recordEviction() { evictionCount.increment(); }
    public void recordLoad(long nanos) { loadCount.increment(); loadTotalNanos.add(nanos); }

    public long getHitCount() { return hitCount.sum(); }
    public long getMissCount() { return missCount.sum(); }
    public long getEvictionCount() { return evictionCount.sum(); }
    public long getLoadCount() { return loadCount.sum(); }

    public double getHitRate() {
        long total = hitCount.sum() + missCount.sum();
        return total == 0 ? 0.0 : (double) hitCount.sum() / total;
    }

    public double getAverageLoadNanos() {
        long cnt = loadCount.sum();
        return cnt == 0 ? 0.0 : (double) loadTotalNanos.sum() / cnt;
    }

    @Override
    public String toString() {
        return String.format("CacheStats{hit=%d, miss=%d, eviction=%d, hitRate=%.2f%%}",
                getHitCount(), getMissCount(), getEvictionCount(), getHitRate() * 100);
    }
}
