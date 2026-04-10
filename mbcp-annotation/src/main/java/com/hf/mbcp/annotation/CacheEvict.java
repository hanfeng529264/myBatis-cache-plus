package com.hf.mbcp.annotation;

import com.hf.mbcp.annotation.enums.EvictScope;

import java.lang.annotation.*;

/**
 * 标记写操作方法，执行后（或前）清除相关缓存。
 * <p>示例：
 * <pre>
 * {@literal @}CacheEvict(beforeInvocation = true, afterInvocation = true)
 * int updateById(User user);
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheEvict {

    /** 缓存名称，为空时使用 Mapper 全限定名 */
    String cacheName() default "";

    /**
     * SpEL 精确 key 表达式，仅删除该 key。
     * 为空时根据 scope 决定删除范围
     */
    String key() default "";

    /** 失效范围（当 key 为空时生效） */
    EvictScope scope() default EvictScope.AUTO;

    /** 在方法执行前清除缓存（默认 false） */
    boolean beforeInvocation() default false;

    /** 在方法执行后清除缓存（默认 true） */
    boolean afterInvocation() default true;

    /** 是否启用延迟双删（默认 true），处理主从延迟窗口 */
    boolean doubleEvict() default true;

    /** 延迟双删延迟时间（毫秒），0 = 使用全局配置（默认 200ms） */
    long doubleEvictDelayMs() default 0;
}
