package com.hf.mbcp.api;

import com.hf.mbcp.api.model.CacheLevel;
import com.hf.mbcp.api.model.EvictCause;

/** 缓存事件监听器，用于监控和日志记录。可注册多个实现，均会被调用。 */
public interface CacheEventListener {

    /** 缓存命中 */
    default void onHit(String key, CacheLevel level) {}

    /** 缓存未命中 */
    default void onMiss(String key) {}

    /** 穿透：DB 查询结果也为 null */
    default void onPenetration(String key) {}

    /** 缓存条目被清除/淘汰 */
    default void onEvict(String key, EvictCause cause) {}

    /** Redis 熔断器状态变更 */
    default void onCircuitBreak(String state, String reason) {}

    /** 缓存被动态禁用/启用 */
    default void onSwitch(boolean enabled) {}
}
