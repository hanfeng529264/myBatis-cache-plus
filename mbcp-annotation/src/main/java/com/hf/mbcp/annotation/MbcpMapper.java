package com.hf.mbcp.annotation;

import com.hf.mbcp.annotation.enums.ConsistencyLevel;

import java.lang.annotation.*;

/**
 * Mapper 接口级别缓存配置。
 * 方法级注解优先级高于此注解，此注解高于全局配置。
 * <p>示例：
 * <pre>
 * {@literal @}MbcpMapper(defaultExpire = 600, consistencyLevel = ConsistencyLevel.EVENTUAL)
 * public interface UserMapper { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MbcpMapper {

    /** Mapper 级别默认 TTL（秒），0 = 使用全局默认值 */
    long defaultExpire() default 0;

    /**
     * 是否对该 Mapper 所有查询方法自动启用透明缓存
     * （无需在每个方法上单独加 {@link Cacheable}）
     */
    boolean autoCache() default false;

    /** autoCache=true 时，排除不走缓存的方法名列表 */
    String[] excludeMethods() default {};

    /** Mapper 级别一致性级别，覆盖全局配置 */
    ConsistencyLevel consistencyLevel() default ConsistencyLevel.EVENTUAL;
}
