package com.hf.mbcp.annotation;

import java.lang.annotation.*;

/**
 * 标记写操作方法，执行后将返回值写入缓存（write-through 语义）。
 * <p>与 {@link Cacheable} 的区别：总是执行方法体，然后用返回值更新缓存。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CachePut {

    String cacheName() default "";

    /** SpEL key 表达式；为空时自动生成 */
    String key() default "";

    /** SpEL 入参条件，false 时跳过写缓存 */
    String condition() default "";

    /** SpEL 结果条件，true 时不写缓存（如 unless="#result==null"） */
    String unless() default "";

    /** TTL（秒），0 = 全局默认 */
    long expire() default 0;
}
