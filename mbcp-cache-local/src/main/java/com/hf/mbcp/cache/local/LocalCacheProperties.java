package com.hf.mbcp.cache.local;

import com.hf.mbcp.annotation.enums.ExpireStrategy;

/**
 * 本地缓存配置，支持 yml 和 Java Config 双通道。
 * <p><b>优先级：Java Config Bean > yml</b>
 * <p>Java Config 示例：
 * <pre>
 * {@literal @}Bean
 * public LocalCacheProperties localCacheProperties() {
 *     return LocalCacheProperties.builder()
 *         .maxMemoryMb(512)
 *         .expireAfterWrite(600)
 *         .build();
 * }
 * </pre>
 */
public class LocalCacheProperties {

    /** 内存上限（MB），默认 256 */
    private int maxMemoryMb = 256;

    /** 单值大小上限（KB），超过则跳过本地缓存，0=不限，默认 512 */
    private int maxValueSizeKb = 512;

    /** 过期策略，默认 WRITE */
    private ExpireStrategy expireStrategy = ExpireStrategy.WRITE;

    /** 写后过期（秒），默认 300 */
    private long expireAfterWrite = 300;

    /** 访问后过期（秒），默认 300，expireStrategy=ACCESS 时生效 */
    private long expireAfterAccess = 300;

    /** 后台刷新（秒），0=禁用，需 < expireAfterWrite */
    private long refreshAfterWrite = 0;

    /** 是否开启 Caffeine 内置统计，默认 true */
    private boolean recordStats = true;

    // ────── 内存压感知 ──────
    private boolean memoryPressureEnabled = true;
    private double shrinkThreshold = 0.80;
    private double shrinkRatio = 0.60;
    private int checkIntervalSeconds = 30;

    // ────── Builder ──────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final LocalCacheProperties p = new LocalCacheProperties();
        public Builder maxMemoryMb(int v) { p.maxMemoryMb = v; return this; }
        public Builder maxValueSizeKb(int v) { p.maxValueSizeKb = v; return this; }
        public Builder expireStrategy(ExpireStrategy v) { p.expireStrategy = v; return this; }
        public Builder expireAfterWrite(long v) { p.expireAfterWrite = v; return this; }
        public Builder expireAfterAccess(long v) { p.expireAfterAccess = v; return this; }
        public Builder refreshAfterWrite(long v) { p.refreshAfterWrite = v; return this; }
        public Builder recordStats(boolean v) { p.recordStats = v; return this; }
        public LocalCacheProperties build() { return p; }
    }

    // ────── Getters / Setters ──────
    public int getMaxMemoryMb() { return maxMemoryMb; }
    public void setMaxMemoryMb(int maxMemoryMb) { this.maxMemoryMb = maxMemoryMb; }
    public int getMaxValueSizeKb() { return maxValueSizeKb; }
    public void setMaxValueSizeKb(int maxValueSizeKb) { this.maxValueSizeKb = maxValueSizeKb; }
    public ExpireStrategy getExpireStrategy() { return expireStrategy; }
    public void setExpireStrategy(ExpireStrategy expireStrategy) { this.expireStrategy = expireStrategy; }
    public long getExpireAfterWrite() { return expireAfterWrite; }
    public void setExpireAfterWrite(long expireAfterWrite) { this.expireAfterWrite = expireAfterWrite; }
    public long getExpireAfterAccess() { return expireAfterAccess; }
    public void setExpireAfterAccess(long expireAfterAccess) { this.expireAfterAccess = expireAfterAccess; }
    public long getRefreshAfterWrite() { return refreshAfterWrite; }
    public void setRefreshAfterWrite(long refreshAfterWrite) { this.refreshAfterWrite = refreshAfterWrite; }
    public boolean isRecordStats() { return recordStats; }
    public void setRecordStats(boolean recordStats) { this.recordStats = recordStats; }
    public boolean isMemoryPressureEnabled() { return memoryPressureEnabled; }
    public void setMemoryPressureEnabled(boolean memoryPressureEnabled) { this.memoryPressureEnabled = memoryPressureEnabled; }
    public double getShrinkThreshold() { return shrinkThreshold; }
    public void setShrinkThreshold(double shrinkThreshold) { this.shrinkThreshold = shrinkThreshold; }
    public double getShrinkRatio() { return shrinkRatio; }
    public void setShrinkRatio(double shrinkRatio) { this.shrinkRatio = shrinkRatio; }
    public int getCheckIntervalSeconds() { return checkIntervalSeconds; }
    public void setCheckIntervalSeconds(int checkIntervalSeconds) { this.checkIntervalSeconds = checkIntervalSeconds; }
}
