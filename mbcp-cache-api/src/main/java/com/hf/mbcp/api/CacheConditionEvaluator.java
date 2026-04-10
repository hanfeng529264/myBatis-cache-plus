package com.hf.mbcp.api;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 缓存条件判断器 SPI，决定是否应该缓存某次请求或结果。
 * 默认实现：{@link com.hf.mbcp.core.support.SpelExpressionEvaluator}
 */
public interface CacheConditionEvaluator {

    /**
     * 入参维度判断（对应 @Cacheable.condition）。
     * @param conditionExpr SpEL 表达式，为空则直接返回 true
     * @param method        方法
     * @param params        参数 Map
     * @return true = 应缓存
     */
    boolean evaluateCondition(String conditionExpr, Method method, Map<String, Object> params);

    /**
     * 结果维度判断（对应 @Cacheable.unless）。
     * @param unlessExpr SpEL 表达式，为空则直接返回 false（即：不排除，应缓存）
     * @param method     方法
     * @param params     参数 Map
     * @param result     方法返回值
     * @return true = 排除（不缓存）
     */
    boolean evaluateUnless(String unlessExpr, Method method, Map<String, Object> params, Object result);

    /**
     * 解析 SpEL key 表达式。
     * @param keyExpr SpEL 表达式，为空则返回 null（由 CacheKeyGenerator 自动生成）
     */
    String evaluateKey(String keyExpr, Method method, Map<String, Object> params, Object result);

    /**
     * 解析 enabled 表达式（@Cacheable.enabled）。
     * @param enabledExpr SpEL 表达式，为空则返回 true
     */
    boolean evaluateEnabled(String enabledExpr, Method method, Map<String, Object> params);
}
