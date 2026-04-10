package com.hf.mbcp.cache.multi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Redis 熔断器：CLOSED → OPEN → HALF_OPEN 三态。
 * <ul>
 *   <li>CLOSED：正常，请求直接透传到 Redis</li>
 *   <li>OPEN：熔断中，请求走降级逻辑（本地缓存或直查 DB）</li>
 *   <li>HALF_OPEN：探测恢复，允许少量请求试探 Redis</li>
 * </ul>
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong openTimestamp = new AtomicLong(0);
    private final AtomicInteger halfOpenAttempts = new AtomicInteger(0);

    private final int failureThreshold;      // 连续失败次数触发熔断
    private final long resetTimeoutMs;       // OPEN 后等待恢复时间（ms）
    private final int halfOpenProbeCount;    // HALF_OPEN 时允许的探测请求数

    public CircuitBreaker(int failureThreshold, long resetTimeoutMs, int halfOpenProbeCount) {
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
        this.halfOpenProbeCount = halfOpenProbeCount;
    }

    /**
     * 执行操作，失败时走降级。
     * @param redisOp  Redis 操作
     * @param fallback 降级操作
     */
    public <T> T execute(Supplier<T> redisOp, Supplier<T> fallback) {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openTimestamp.get() > resetTimeoutMs) {
                state = State.HALF_OPEN;
                halfOpenAttempts.set(0);
                log.info("[MBCP-CircuitBreaker] → HALF_OPEN, probing Redis");
            } else {
                return fallback.get();
            }
        }
        if (state == State.HALF_OPEN && halfOpenAttempts.getAndIncrement() >= halfOpenProbeCount) {
            return fallback.get();
        }
        try {
            T result = redisOp.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(e);
            return fallback.get();
        }
    }

    private void onSuccess() {
        failureCount.set(0);
        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            log.info("[MBCP-CircuitBreaker] → CLOSED, Redis recovered");
        }
    }

    private void onFailure(Exception e) {
        int cnt = failureCount.incrementAndGet();
        log.warn("[MBCP-CircuitBreaker] Redis failure #{}: {}", cnt, e.getMessage());
        if (cnt >= failureThreshold || state == State.HALF_OPEN) {
            state = State.OPEN;
            openTimestamp.set(System.currentTimeMillis());
            log.error("[MBCP-CircuitBreaker] → OPEN after {} failures", cnt);
        }
    }

    public State getState() { return state; }

    /** 手动重置熔断器（Actuator 端点调用） */
    public void reset() {
        state = State.CLOSED;
        failureCount.set(0);
        log.info("[MBCP-CircuitBreaker] manually reset → CLOSED");
    }
}
