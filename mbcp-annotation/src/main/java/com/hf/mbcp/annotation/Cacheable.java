package com.hf.mbcp.annotation;

import com.hf.mbcp.annotation.enums.ConsistencyLevel;

import java.lang.annotation.*;

/**
 * 标记查询方法启用缓存。
 * <p>示例：
 * <pre>
 * {@literal @}Cacheable(expire = 300, consistencyLevel = ConsistencyLevel.C)
 * User selectById(Long id);
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cacheable {

    /** 缓存名称（命名空间），用于批量失效；默认取 Mapper 全限定名 */
    String cacheName() default "";

    /**
     * SpEL 表达式，自定义缓存 key。
     * 支持变量：#paramName、#root.args[0]、@beanName
     * 为空时由 CacheKeyGenerator 自动生成
     */
    String key() default "";

    /** SpEL 条件，计算结果为 false 时跳过缓存直接查 DB（入参维度） */
    String condition() default "";

    /**
     * SpEL 条件，计算结果为 true 时不缓存方法返回值（结果维度）。
     * 例：unless="#result == null" 表示结果为 null 时不缓存
     */
    String unless() default "";

    /** 缓存过期时间（秒），0 = 使用全局默认值 */
    long expire() default 0;

    /** 一致性级别，覆盖全局配置（优先级：方法 > Mapper > 全局） */
    ConsistencyLevel consistencyLevel() default ConsistencyLevel.EVENTUAL;

    /**
     * 是否启用此缓存（SpEL 表达式，支持动态关闭）。
     * 例：enabled = "#{@featureFlags.cacheEnabled}"
     */
    String enabled() default "true";
}
