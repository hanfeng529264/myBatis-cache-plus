package com.hf.mbcp.autoconfigure;

import com.hf.mbcp.annotation.enums.CacheType;
import com.hf.mbcp.annotation.enums.ConsistencyLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * MBCP 全局配置属性，绑定前缀 {@code mbcp}。
 */
@ConfigurationProperties(prefix = "mbcp")
public class MbcpProperties {

    /** 是否启用 MBCP */
    private boolean enabled = true;

    /** 默认缓存类型 */
    private CacheType cacheType = CacheType.LOCAL;

    /** 默认 TTL（秒） */
    private long defaultExpire = 300;

    /** 默认一致性级别 */
    private ConsistencyLevel consistencyLevel = ConsistencyLevel.EVENTUAL;

    @NestedConfigurationProperty
    private Local local = new Local();

    @NestedConfigurationProperty
    private Redis redis = new Redis();

    @NestedConfigurationProperty
    private Protection protection = new Protection();

    @NestedConfigurationProperty
    private Evict evict = new Evict();

    @NestedConfigurationProperty
    private Sharding sharding = new Sharding();

    // ────── 内部类 ──────

    public static class Local {
        /** L1 最大内存（MB），0 = 不限 */
        private long maxMemoryMb = 256;
        /** 单个缓存值最大大小（KB），超过则不缓存到 L1 */
        private long maxValueSizeKb = 512;
        /** 写后过期时间（秒），0 = 跟随全局 defaultExpire */
        private long expireAfterWrite = 300;
        /** 访问后过期时间（秒），0 = 不启用 */
        private long expireAfterAccess = 0;
        /** 初始容量（条目数估算） */
        private int initialCapacity = 1000;

        public long getMaxMemoryMb() { return maxMemoryMb; }
        public void setMaxMemoryMb(long maxMemoryMb) { this.maxMemoryMb = maxMemoryMb; }
        public long getMaxValueSizeKb() { return maxValueSizeKb; }
        public void setMaxValueSizeKb(long maxValueSizeKb) { this.maxValueSizeKb = maxValueSizeKb; }
        public long getExpireAfterWrite() { return expireAfterWrite; }
        public void setExpireAfterWrite(long expireAfterWrite) { this.expireAfterWrite = expireAfterWrite; }
        public long getExpireAfterAccess() { return expireAfterAccess; }
        public void setExpireAfterAccess(long expireAfterAccess) { this.expireAfterAccess = expireAfterAccess; }
        public int getInitialCapacity() { return initialCapacity; }
        public void setInitialCapacity(int initialCapacity) { this.initialCapacity = initialCapacity; }
    }

    public static class Redis {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database = 0;
        /** 连接超时（ms） */
        private int connectTimeout = 3000;
        /** 命令超时（ms） */
        private int timeout = 3000;
        /** Redisson 配置文件路径（优先级高于上述字段） */
        private String configFile;
        /** L2 写锁等待超时（ms），Level D 专用 */
        private long lockWaitTimeMs = 3000;
        /** 延迟双删间隔（ms） */
        private long doubleEvictDelayMs = 200;
        /** L1 相对 L2 的 TTL 比例（0~1） */
        private double l1TtlRatio = 0.3;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getDatabase() { return database; }
        public void setDatabase(int database) { this.database = database; }
        public int getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }
        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
        public String getConfigFile() { return configFile; }
        public void setConfigFile(String configFile) { this.configFile = configFile; }
        public long getLockWaitTimeMs() { return lockWaitTimeMs; }
        public void setLockWaitTimeMs(long lockWaitTimeMs) { this.lockWaitTimeMs = lockWaitTimeMs; }
        public long getDoubleEvictDelayMs() { return doubleEvictDelayMs; }
        public void setDoubleEvictDelayMs(long doubleEvictDelayMs) { this.doubleEvictDelayMs = doubleEvictDelayMs; }
        public double getL1TtlRatio() { return l1TtlRatio; }
        public void setL1TtlRatio(double l1TtlRatio) { this.l1TtlRatio = l1TtlRatio; }
    }

    public static class Protection {
        /** 防穿透：空值缓存 TTL（秒） */
        private long nullValueTtlSeconds = 60;
        /** 防击穿：锁等待超时（ms） */
        private long lockWaitTimeMs = 3000;
        /** 防雪崩：TTL 随机浮动比例（0~1） */
        private double avalancheOffsetRatio = 0.2;

        public long getNullValueTtlSeconds() { return nullValueTtlSeconds; }
        public void setNullValueTtlSeconds(long nullValueTtlSeconds) { this.nullValueTtlSeconds = nullValueTtlSeconds; }
        public long getLockWaitTimeMs() { return lockWaitTimeMs; }
        public void setLockWaitTimeMs(long lockWaitTimeMs) { this.lockWaitTimeMs = lockWaitTimeMs; }
        public double getAvalancheOffsetRatio() { return avalancheOffsetRatio; }
        public void setAvalancheOffsetRatio(double avalancheOffsetRatio) { this.avalancheOffsetRatio = avalancheOffsetRatio; }
    }

    public static class Evict {
        /** 异步线程池核心线程数 */
        private int asyncCorePoolSize = 4;
        /** 延迟双删线程池大小 */
        private int delayedPoolSize = 2;
        /** 延迟双删队列容量 */
        private int delayedQueueCapacity = 500;

        public int getAsyncCorePoolSize() { return asyncCorePoolSize; }
        public void setAsyncCorePoolSize(int asyncCorePoolSize) { this.asyncCorePoolSize = asyncCorePoolSize; }
        public int getDelayedPoolSize() { return delayedPoolSize; }
        public void setDelayedPoolSize(int delayedPoolSize) { this.delayedPoolSize = delayedPoolSize; }
        public int getDelayedQueueCapacity() { return delayedQueueCapacity; }
        public void setDelayedQueueCapacity(int delayedQueueCapacity) { this.delayedQueueCapacity = delayedQueueCapacity; }
    }

    public static class Sharding {
        /** 是否启用分库分表感知缓存键生成 */
        private boolean enabled = false;
        /** 使用 ShardingSphere RouteContext 时是否自动追加分片后缀 */
        private boolean appendShardSuffix = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isAppendShardSuffix() { return appendShardSuffix; }
        public void setAppendShardSuffix(boolean appendShardSuffix) { this.appendShardSuffix = appendShardSuffix; }
    }

    // ────── Getters/Setters ──────

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public CacheType getCacheType() { return cacheType; }
    public void setCacheType(CacheType cacheType) { this.cacheType = cacheType; }
    public long getDefaultExpire() { return defaultExpire; }
    public void setDefaultExpire(long defaultExpire) { this.defaultExpire = defaultExpire; }
    public ConsistencyLevel getConsistencyLevel() { return ConsistencyLevel.EVENTUAL; }
    public void setConsistencyLevel(ConsistencyLevel consistencyLevel) { this.consistencyLevel = consistencyLevel; }
    public Local getLocal() { return local; }
    public void setLocal(Local local) { this.local = local; }
    public Redis getRedis() { return redis; }
    public void setRedis(Redis redis) { this.redis = redis; }
    public Protection getProtection() { return protection; }
    public void setProtection(Protection protection) { this.protection = protection; }
    public Evict getEvict() { return evict; }
    public void setEvict(Evict evict) { this.evict = evict; }
    public Sharding getSharding() { return sharding; }
    public void setSharding(Sharding sharding) { this.sharding = sharding; }
}
