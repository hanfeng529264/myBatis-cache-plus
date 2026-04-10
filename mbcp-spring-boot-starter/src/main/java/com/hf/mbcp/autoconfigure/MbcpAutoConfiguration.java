package com.hf.mbcp.autoconfigure;

import com.hf.mbcp.annotation.enums.CacheType;
import com.hf.mbcp.api.CacheKeyGenerator;
import com.hf.mbcp.api.CacheProvider;
import com.hf.mbcp.cache.local.CaffeineProvider;
import com.hf.mbcp.cache.local.LocalCacheProperties;
import com.hf.mbcp.cache.multi.CircuitBreaker;
import com.hf.mbcp.cache.multi.InvalidationBroadcaster;
import com.hf.mbcp.cache.multi.MultiLevelCacheProvider;
import com.hf.mbcp.cache.multi.NoOpBroadcaster;
import com.hf.mbcp.cache.multi.RedisBroadcaster;
import com.hf.mbcp.cache.redis.RedisPubSubInvalidator;
import com.hf.mbcp.cache.redis.RedissonProvider;
import com.hf.mbcp.core.aop.CacheAspect;
import com.hf.mbcp.core.consistency.ConsistencyCoordinator;
import com.hf.mbcp.core.consistency.LevelDCoordinator;
import com.hf.mbcp.core.interceptor.MbcpInterceptor;
import com.hf.mbcp.core.key.DefaultCacheKeyGenerator;
import com.hf.mbcp.core.protection.CacheProtector;
import com.hf.mbcp.core.support.SpelExpressionEvaluator;
import com.hf.mbcp.monitor.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.*;

/**
 * MBCP 自动配置入口。
 * <p>条件装配规则：
 * <ul>
 *   <li>整体开关：{@code mbcp.enabled=true}（默认）</li>
 *   <li>Redis 相关 Bean：{@code @ConditionalOnProperty(mbcp.cache-type=redis|hybrid)} 或有 RedissonClient</li>
 *   <li>监控相关：{@code @ConditionalOnClass(MeterRegistry)} + Actuator 存在时</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "mbcp", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MbcpProperties.class)
@EnableScheduling
public class MbcpAutoConfiguration {

    // ────── 线程池 ──────

    @Bean(name = "mbcpAsyncExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "mbcpAsyncExecutor")
    public ExecutorService mbcpAsyncExecutor(MbcpProperties props) {
        int core = props.getEvict().getAsyncCorePoolSize();
        return new ThreadPoolExecutor(
                core, core * 2, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                r -> { Thread t = new Thread(r, "mbcp-async"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean(name = "mbcpDelayedEvictExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "mbcpDelayedEvictExecutor")
    public ScheduledExecutorService mbcpDelayedEvictExecutor(MbcpProperties props) {
        int size = props.getEvict().getDelayedPoolSize();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                size,
                r -> { Thread t = new Thread(r, "mbcp-delay-evict"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
        executor.setMaximumPoolSize(size);
        return executor;
    }

    // ────── 本地缓存 L1 ──────

    @Bean
    @ConditionalOnMissingBean(CaffeineProvider.class)
    public CaffeineProvider mbcpL1Provider(MbcpProperties props,
                                            ObjectProvider<MbcpLocalCacheConfig> javaConfigProvider) {
        // Java Config Bean 优先级 > yml
        LocalCacheProperties localProps = buildLocalProps(props, javaConfigProvider.getIfAvailable());
        return new CaffeineProvider(localProps);
    }

    private LocalCacheProperties buildLocalProps(MbcpProperties props, MbcpLocalCacheConfig javaConfig) {
        LocalCacheProperties.Builder builder = LocalCacheProperties.builder()
                .maxMemoryMb((int) props.getLocal().getMaxMemoryMb())
                .maxValueSizeKb((int) props.getLocal().getMaxValueSizeKb())
                .expireAfterWrite(props.getLocal().getExpireAfterWrite())
                .expireAfterAccess(props.getLocal().getExpireAfterAccess());
        if (javaConfig != null) {
            javaConfig.configure(builder);
        }
        return builder.build();
    }

    // ────── Redis / Redisson ──────

    @Bean
    @ConditionalOnMissingBean(RedissonClient.class)
    @ConditionalOnProperty(prefix = "mbcp", name = "cache-type",
            havingValue = "redis", matchIfMissing = false)
    public RedissonClient mbcpRedissonClient(MbcpProperties props) {
        return buildRedissonClient(props);
    }

    @Bean
    @ConditionalOnMissingBean(RedissonClient.class)
    @ConditionalOnProperty(prefix = "mbcp", name = "cache-type",
            havingValue = "hybrid", matchIfMissing = false)
    public RedissonClient mbcpRedissonClientHybrid(MbcpProperties props) {
        return buildRedissonClient(props);
    }

    private RedissonClient buildRedissonClient(MbcpProperties props) {
        MbcpProperties.Redis redisCfg = props.getRedis();
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisCfg.getHost() + ":" + redisCfg.getPort())
                .setDatabase(redisCfg.getDatabase())
                .setConnectTimeout(redisCfg.getConnectTimeout())
                .setTimeout(redisCfg.getTimeout());
        if (redisCfg.getPassword() != null && !redisCfg.getPassword().isBlank()) {
            config.useSingleServer().setPassword(redisCfg.getPassword());
        }
        return Redisson.create(config);
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(RedissonProvider.class)
    public RedissonProvider mbcpL2Provider(RedissonClient redissonClient) {
        return new RedissonProvider(redissonClient, "");
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(RedisPubSubInvalidator.class)
    public RedisPubSubInvalidator mbcpPubSubInvalidator(RedissonClient redissonClient,
                                                         ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper om = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        String nodeId = java.util.UUID.randomUUID().toString();
        return new RedisPubSubInvalidator(redissonClient, om, "mbcp:invalidate", nodeId);
    }

    @Bean
    @ConditionalOnBean(RedisPubSubInvalidator.class)
    @ConditionalOnMissingBean(InvalidationBroadcaster.class)
    public RedisBroadcaster mbcpRedisBroadcaster(RedisPubSubInvalidator invalidator) {
        String nodeId = java.util.UUID.randomUUID().toString();
        return new RedisBroadcaster(invalidator, nodeId);
    }

    @Bean
    @ConditionalOnMissingBean(InvalidationBroadcaster.class)
    public NoOpBroadcaster mbcpNoOpBroadcaster() {
        return new NoOpBroadcaster();
    }

    @Bean
    @ConditionalOnMissingBean(CircuitBreaker.class)
    public CircuitBreaker mbcpCircuitBreaker() {
        return new CircuitBreaker(5, 30_000L, 3);
    }

    // ────── 多级缓存 ──────

    @Bean
    @ConditionalOnProperty(prefix = "mbcp", name = "cache-type", havingValue = "hybrid")
    @ConditionalOnBean({CaffeineProvider.class, RedissonProvider.class})
    @ConditionalOnMissingBean(MultiLevelCacheProvider.class)
    public MultiLevelCacheProvider mbcpMultiLevelCacheProvider(
            CaffeineProvider l1, RedissonProvider l2,
            InvalidationBroadcaster broadcaster,
            CircuitBreaker circuitBreaker,
            ExecutorService mbcpAsyncExecutor,
            MbcpProperties props) {
        return new MultiLevelCacheProvider(l1, l2, broadcaster, circuitBreaker,
                mbcpAsyncExecutor, props.getRedis().getL1TtlRatio());
    }

    // ────── CacheProvider 主 Bean ──────

    @Bean
    @ConditionalOnMissingBean(CacheProvider.class)
    public CacheProvider mbcpCacheProvider(MbcpProperties props,
                                            ObjectProvider<MultiLevelCacheProvider> multiLevel,
                                            ObjectProvider<RedissonProvider> redissonProvider,
                                            CaffeineProvider caffeineProvider) {
        CacheType type = props.getCacheType();
        return switch (type) {
            case HYBRID -> {
                MultiLevelCacheProvider ml = multiLevel.getIfAvailable();
                if (ml == null) {
                    throw new IllegalStateException("[MBCP] cache-type=HYBRID but MultiLevelCacheProvider not available. Check Redis configuration.");
                }
                yield ml;
            }
            case REDIS -> {
                RedissonProvider rp = redissonProvider.getIfAvailable();
                if (rp == null) {
                    throw new IllegalStateException("[MBCP] cache-type=REDIS but RedissonProvider not available. Check Redis configuration.");
                }
                yield rp;
            }
            case LOCAL -> caffeineProvider;
            case NONE -> new com.hf.mbcp.cache.multi.NoOpCacheProvider();
        };
    }

    // ────── 防护机制 ──────

    @Bean
    @ConditionalOnMissingBean(CacheProtector.class)
    public CacheProtector mbcpCacheProtector(MbcpProperties props,
                                              ObjectProvider<RedissonClient> redissonClient) {
        MbcpProperties.Protection prot = props.getProtection();
        // 锁工厂：有 Redis 用 RedissonLock，否则用 JVM 本地锁
        java.util.function.Supplier<com.hf.mbcp.api.CacheLock> lockFactory;
        RedissonClient rc = redissonClient.getIfAvailable();
        if (rc != null) {
            lockFactory = () -> new com.hf.mbcp.cache.redis.RedissonLock(
                    rc.getLock("mbcp:lock:breakdown"));
        } else {
            lockFactory = LocalLockAdapter::new;
        }
        return new CacheProtector(
                true, true, true,
                prot.getAvalancheOffsetRatio(),
                prot.getNullValueTtlSeconds(),
                prot.getLockWaitTimeMs(),
                lockFactory
        );
    }

    // ────── 一致性协调器 ──────

    @Bean
    @ConditionalOnMissingBean(LevelDCoordinator.class)
    @ConditionalOnBean({RedissonClient.class, MultiLevelCacheProvider.class})
    public LevelDCoordinator mbcpLevelDCoordinator(RedissonClient redissonClient,
                                                    MultiLevelCacheProvider multiLevelCacheProvider,
                                                    ScheduledExecutorService mbcpDelayedEvictExecutor,
                                                    MbcpProperties props) {
        return new LevelDCoordinator(
                multiLevelCacheProvider,
                redissonClient,
                mbcpDelayedEvictExecutor,
                props.getRedis().getLockWaitTimeMs(),
                props.getRedis().getDoubleEvictDelayMs()
        );
    }

    @Bean
    @ConditionalOnMissingBean(ConsistencyCoordinator.class)
    public ConsistencyCoordinator mbcpConsistencyCoordinator(
            CacheProvider cacheProvider,
            CacheProtector cacheProtector,
            ExecutorService mbcpAsyncExecutor,
            ScheduledExecutorService mbcpDelayedEvictExecutor,
            ObjectProvider<LevelDCoordinator> levelDProvider,
            MbcpProperties props) {
        return new ConsistencyCoordinator(
                cacheProvider,
                cacheProtector,
                mbcpAsyncExecutor,
                mbcpDelayedEvictExecutor,
                props.getRedis().getDoubleEvictDelayMs(),
                levelDProvider.getIfAvailable(),
                props.getConsistencyLevel()
        );
    }

    // ────── Key 生成器 ──────

    @Bean
    @ConditionalOnMissingBean(CacheKeyGenerator.class)
    public CacheKeyGenerator mbcpCacheKeyGenerator() {
        return new DefaultCacheKeyGenerator();
    }

    // ────── AOP 切面 ──────

    @Bean
    @ConditionalOnMissingBean(CacheAspect.class)
    public CacheAspect mbcpCacheAspect(ConsistencyCoordinator coordinator,
                                        CacheKeyGenerator keyGenerator,
                                        MbcpProperties props) {
        return new CacheAspect(coordinator, keyGenerator,
                new SpelExpressionEvaluator(), props.getDefaultExpire());
    }

    // ────── MyBatis 拦截器 ──────

    @Bean
    @ConditionalOnMissingBean(MbcpInterceptor.class)
    public MbcpInterceptor mbcpInterceptor(ConsistencyCoordinator coordinator,
                                            CacheKeyGenerator keyGenerator,
                                            MbcpProperties props) {
        return new MbcpInterceptor(coordinator, keyGenerator, props.getDefaultExpire());
    }

    // ────── 监控 ──────

    @Bean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnMissingBean(MbcpMetrics.class)
    public MbcpMetrics mbcpMetrics(MeterRegistry meterRegistry) {
        return new MbcpMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnBean(MbcpMetrics.class)
    @ConditionalOnMissingBean(MicrometerEventListener.class)
    public MicrometerEventListener mbcpEventListener(MbcpMetrics metrics) {
        return new MicrometerEventListener(metrics);
    }

    @Bean
    @ConditionalOnBean(CaffeineProvider.class)
    @ConditionalOnMissingBean(MemoryPressureWatcher.class)
    public MemoryPressureWatcher mbcpMemoryPressureWatcher(CaffeineProvider caffeineProvider,
                                                            MbcpProperties props) {
        return new MemoryPressureWatcher(caffeineProvider, props.getLocal().getMaxMemoryMb());
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    @ConditionalOnMissingBean(MbcpCacheEndpoint.class)
    public MbcpCacheEndpoint mbcpCacheEndpoint(CaffeineProvider l1,
                                                ObjectProvider<RedissonProvider> l2) {
        return new MbcpCacheEndpoint(l1, l2.getIfAvailable());
    }

    // ────── 内部工具类 ──────

    /** JVM 本地可重入锁，无 Redis 时的 fallback */
    private static class LocalLockAdapter implements com.hf.mbcp.api.CacheLock {
        private final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();

        @Override
        public boolean tryLock(long time, java.util.concurrent.TimeUnit unit) {
            try { return lock.tryLock(time, unit); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }

        @Override
        public void unlock() {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }

        @Override
        public boolean isHeldByCurrentThread() { return lock.isHeldByCurrentThread(); }
    }
}
