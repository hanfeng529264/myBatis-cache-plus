package com.hf.mbcp.api;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 分库分表路由信息提取器 SPI。
 * 内置实现：ShardingSphereShardInfoExtractor（通过 RouteContext ThreadLocal 读取）
 * 自定义：实现此接口并注入 Spring 容器
 */
public interface ShardInfoExtractor {

    /**
     * 从当前请求上下文中提取分片信息。
     * @return null 表示不涉及分片（全局表/未分片表）
     */
    ShardInfo extract(Class<?> mapper, Method method, Map<String, Object> params);
}
