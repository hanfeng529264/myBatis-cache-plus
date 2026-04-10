package com.hf.mbcp.annotation;

import java.lang.annotation.*;

/**
 * 组合注解，在同一方法上同时声明多个缓存操作。
 * <p>示例：
 * <pre>
 * {@literal @}Caching(
 *     evict = {@literal @}CacheEvict(key = "#user.id"),
 *     put   = {@literal @}CachePut(key = "#user.id")
 * )
 * User saveOrUpdate(User user);
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Caching {
    Cacheable[] cacheable() default {};
    CacheEvict[] evict() default {};
    CachePut[] put() default {};
}
