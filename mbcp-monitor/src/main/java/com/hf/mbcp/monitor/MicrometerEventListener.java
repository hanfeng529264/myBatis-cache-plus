package com.hf.mbcp.monitor;

import com.hf.mbcp.api.CacheEventListener;
import com.hf.mbcp.api.model.CacheLevel;
import com.hf.mbcp.api.model.EvictCause;

/**
 * 将缓存事件转发给 {@link MbcpMetrics} 记录 Micrometer 指标。
 */
public class MicrometerEventListener implements CacheEventListener {

    private final MbcpMetrics metrics;

    public MicrometerEventListener(MbcpMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void onHit(String key, CacheLevel level) {
        metrics.recordHit(level);
    }

    @Override
    public void onMiss(String key) {
        metrics.recordMiss(CacheLevel.L2);
    }

    @Override
    public void onPenetration(String key) {
        metrics.recordPenetration();
    }

    @Override
    public void onEvict(String key, EvictCause cause) {
        metrics.recordEviction(cause.name());
    }

    @Override
    public void onCircuitBreak(String state, String reason) {
        metrics.recordCircuitBreak();
    }
}
