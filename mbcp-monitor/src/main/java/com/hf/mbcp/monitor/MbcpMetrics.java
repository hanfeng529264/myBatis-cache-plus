package com.hf.mbcp.monitor;

import com.hf.mbcp.api.model.CacheLevel;
import io.micrometer.core.instrument.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * MBCP Micrometer 指标注册器。
 */
public class MbcpMetrics {

    private final MeterRegistry registry;
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final Timer loadTimer;

    public MbcpMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.loadTimer = Timer.builder("mbcp.cache.load_time")
                .description("Time to load from DB on cache miss")
                .register(registry);
    }

    public <T> void registerGauge(String name, T obj, java.util.function.ToDoubleFunction<T> fn,
                                   String description, String... tags) {
        Gauge.builder(name, obj, fn)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    public void recordHit(CacheLevel level) {
        counter("mbcp.cache.hits", "level", level.name()).increment();
    }

    public void recordMiss(CacheLevel level) {
        counter("mbcp.cache.misses", "level", level.name()).increment();
    }

    public void recordPenetration() {
        counter("mbcp.cache.penetrations").increment();
    }

    public void recordEviction(String cause) {
        counter("mbcp.cache.evictions", "cause", cause).increment();
    }

    public void recordCircuitBreak() {
        counter("mbcp.cache.circuit_breaks").increment();
    }

    public void recordLoadTime(long durationNanos) {
        loadTimer.record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    public <T> T timeLoad(Supplier<T> loader) {
        return loadTimer.record(loader);
    }

    private Counter counter(String name, String... tags) {
        String key = name + ":" + String.join(",", tags);
        return counterCache.computeIfAbsent(key, k ->
                Counter.builder(name)
                        .description(name)
                        .tags(tags)
                        .register(registry));
    }
}
